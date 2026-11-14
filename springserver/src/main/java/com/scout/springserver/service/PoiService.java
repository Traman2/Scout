package com.scout.springserver.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.scout.springserver.client.OverpassClient;
import com.scout.springserver.client.OverpassClient.OverpassPoi;
import com.scout.springserver.dto.PoiResponseDTO;
import com.scout.springserver.exception.MissingCoordinatesException;
import com.scout.springserver.mapper.PoiMapper;
import com.scout.springserver.model.Poi;
import com.scout.springserver.model.Space;
import com.scout.springserver.repository.PoiRepository;
import com.scout.springserver.repository.SpacePoiRepository;

/**
 * backend/src/services/poi.service.ts.
 *
 * pois are shared across all spaces (deduped by OSM node id). Discovering for
 * one space upserts into that shared table and deletes-and-replaces only this
 * space's links in space_pois — inside one transaction, so an interrupted
 * request can no longer leave a space with zero linked POIs.
 */
@Service
public class PoiService {
    private static final Logger log = LoggerFactory.getLogger(PoiService.class);

    public static final int DEFAULT_POI_RADIUS_METERS = 1500;

    private final PoiRepository poiRepository;
    private final SpacePoiRepository spacePoiRepository;
    private final SpaceService spaceService;
    private final OverpassClient overpassClient;
    private final TransactionTemplate transactionTemplate;

    public PoiService(
            PoiRepository poiRepository,
            SpacePoiRepository spacePoiRepository,
            SpaceService spaceService,
            OverpassClient overpassClient,
            TransactionTemplate transactionTemplate) {
        this.poiRepository = poiRepository;
        this.spacePoiRepository = spacePoiRepository;
        this.spaceService = spaceService;
        this.overpassClient = overpassClient;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Throws SpaceNotFoundException / MissingCoordinatesException before any
     * discovery work starts; anything thrown after that is a discovery failure.
     */
    public Space getSpaceReadyForDiscovery(Integer spaceId, Integer ownerId) {
        Space space = spaceService.getOwnedSpace(spaceId, ownerId);
        if (!space.hasCoordinates()) {
            throw new MissingCoordinatesException("Geocode this space before discovering nearby POIs");
        }
        return space;
    }

    public List<PoiResponseDTO> discoverPoisForSpace(Space space, int radiusMeters) {
        List<OverpassPoi> found = overpassClient.findNearbyPois(space.getLat(), space.getLng(), radiusMeters);

        // A node matching two category filters would otherwise appear twice,
        // and ON CONFLICT can't update the same row twice in one statement.
        Map<Long, Poi> unique = new LinkedHashMap<>();
        for (OverpassPoi poi : found) unique.putIfAbsent(poi.osmId(), PoiMapper.toModel(poi));

        List<Poi> stored = transactionTemplate.execute(status -> {
            spacePoiRepository.deleteAllBySpaceId(space.getId());
            if (unique.isEmpty()) return List.<Poi>of();

            List<Poi> upserted = poiRepository.upsertAllByOsmId(new ArrayList<>(unique.values()));
            poiRepository.linkAllToSpace(space.getId(), upserted.stream().map(Poi::getId).toList());
            return upserted;
        });

        if (stored == null || stored.isEmpty()) {
            log.info("No POIs found for space {}", space.getId());
            return List.of();
        }

        log.info("Discovered {} POIs for space {}", stored.size(), space.getId());
        return stored.stream().map(PoiMapper::toDTO).toList();
    }

    public List<PoiResponseDTO> getPoisForSpace(Integer spaceId, Integer ownerId) {
        Space space = spaceService.getOwnedSpace(spaceId, ownerId);
        return poiRepository.findAllBySpaceId(space.getId()).stream()
            .map(PoiMapper::toDTO).toList();
    }

    /** Entities, for route computation. Caller must have checked ownership. */
    public List<Poi> getPoiEntitiesForSpace(Integer spaceId) {
        return poiRepository.findAllBySpaceId(spaceId);
    }
}
