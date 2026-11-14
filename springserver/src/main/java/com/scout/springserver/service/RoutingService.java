package com.scout.springserver.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.scout.springserver.client.Coordinates;
import com.scout.springserver.client.OtpClient;
import com.scout.springserver.client.OtpClient.TripPlan;
import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.SpaceRouteResponseDTO;
import com.scout.springserver.mapper.RouteMapper;
import com.scout.springserver.model.Poi;
import com.scout.springserver.model.Route;
import com.scout.springserver.model.Space;
import com.scout.springserver.repository.PoiRepository;
import com.scout.springserver.repository.RouteRepository;
import com.scout.springserver.util.Errors;

/** backend/src/services/routing.service.ts */
@Service
public class RoutingService {
    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    // Firing a trip for every POI at once would overload the resource-capped
    // local OTP instance. Route one small batch at a time instead.
    static final int OTP_CONCURRENCY = 3;

    @FunctionalInterface
    public interface BatchProgressListener {
        void onBatchProgress(int completed, int total);
    }

    private final RouteRepository routeRepository;
    private final PoiRepository poiRepository;
    private final SpaceService spaceService;
    private final OtpClient otpClient;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executor;

    public RoutingService(
            RouteRepository routeRepository,
            PoiRepository poiRepository,
            SpaceService spaceService,
            OtpClient otpClient,
            TransactionTemplate transactionTemplate,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.routeRepository = routeRepository;
        this.poiRepository = poiRepository;
        this.spaceService = spaceService;
        this.otpClient = otpClient;
        this.transactionTemplate = transactionTemplate;
        this.executor = executor;
    }

    private Route planTripForPoi(Space space, Poi poi, String progress) {
        log.info("[{}] Planning trip: POI {} ({}) -> space {}", progress, poi.getId(), poi.getName(), space.getId());

        Optional<TripPlan> trip = otpClient.planTripWithGeometry(
            new Coordinates(poi.getLat(), poi.getLng()),
            new Coordinates(space.getLat(), space.getLng()));

        if (trip.isEmpty()) {
            log.warn("No itinerary found from POI {} to space {}", poi.getId(), space.getId());
            return null;
        }

        log.info("[{}] Done: POI {} -> space {}, {}s", progress, poi.getId(), space.getId(), trip.get().durationSeconds());
        return RouteMapper.toModel(space.getId(), poi.getId(), trip.get());
    }

    /**
     * Per-space delete-and-replace of routes (same convention as POI
     * discovery), inside a transaction so a failure can't leave zero routes.
     */
    public List<Route> computeRoutesForSpace(Space space, List<Poi> targets, BatchProgressListener onBatchProgress) {
        log.info("Computing routes for space {}: {} POIs, {} at a time", space.getId(), targets.size(), OTP_CONCURRENCY);

        List<Route> planned = new ArrayList<>();
        int plannedCount = 0;

        for (int i = 0; i < targets.size(); i += OTP_CONCURRENCY) {
            List<Poi> batch = targets.subList(i, Math.min(targets.size(), i + OTP_CONCURRENCY));
            List<CompletableFuture<Route>> futures = new ArrayList<>();
            for (int j = 0; j < batch.size(); j++) {
                Poi poi = batch.get(j);
                String progress = (i + j + 1) + "/" + targets.size();
                futures.add(CompletableFuture.supplyAsync(() -> planTripForPoi(space, poi, progress), executor));
            }

            planned.addAll(joinAll(futures));
            plannedCount += batch.size();
            if (onBatchProgress != null) onBatchProgress.onBatchProgress(plannedCount, targets.size());
        }

        List<Route> computed = planned.stream().filter(Objects::nonNull).toList();

        List<Route> saved = transactionTemplate.execute(status -> {
            routeRepository.deleteAllBySpaceId(space.getId());
            return computed.isEmpty() ? List.<Route>of() : routeRepository.saveAll(computed);
        });

        if (saved == null || saved.isEmpty()) {
            log.info("No routes computed for space {}", space.getId());
            return List.of();
        }

        log.info("Computed {} routes for space {}", saved.size(), space.getId());
        return saved;
    }

    /** Routes joined with their POI's name and category (inner join — routes without a POI are skipped). */
    public List<SpaceRouteResponseDTO> getRoutesForSpace(Integer spaceId, Integer ownerId) {
        Space space = spaceService.getOwnedSpace(spaceId, ownerId);
        List<Route> routes = routeRepository.findAllBySpaceId(space.getId());
        if (routes.isEmpty()) return List.of();

        Map<Integer, Poi> poisById = poiRepository.findAllById(routes.stream().map(Route::getPoiId).toList())
            .stream().collect(Collectors.toMap(Poi::getId, Function.identity()));

        return routes.stream()
            .filter(route -> poisById.containsKey(route.getPoiId()))
            .map(route -> RouteMapper.toDTO(route, poisById.get(route.getPoiId())))
            .toList();
    }

    /** Promise.all: wait for the batch, rethrowing the first failure unwrapped. */
    static <T> List<T> joinAll(List<CompletableFuture<T>> futures) {
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException e) {
            Throwable cause = Errors.unwrap(e);
            if (cause instanceof RuntimeException re) throw re;
            throw new IllegalStateException(cause);
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }
}
