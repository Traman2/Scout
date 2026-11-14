package com.scout.springserver.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.scout.springserver.dto.RouteProgressResponseDTO;
import com.scout.springserver.mapper.RouteProgressMapper;
import com.scout.springserver.model.RouteProgress;
import com.scout.springserver.repository.RouteProgressRepository;
import com.scout.springserver.util.Timestamps;

/**
 * backend/src/services/route-progress.service.ts. The route_progress row is
 * the durable source of truth; every write is also published as a
 * RouteProgressEvent for live Socket.IO fan-out.
 */
@Service
public class RouteProgressService {
    private static final Logger log = LoggerFactory.getLogger(RouteProgressService.class);

    private final RouteProgressRepository routeProgressRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RouteProgressService(RouteProgressRepository routeProgressRepository, ApplicationEventPublisher eventPublisher) {
        this.routeProgressRepository = routeProgressRepository;
        this.eventPublisher = eventPublisher;
    }

    public RouteProgressResponseDTO getRouteProgress(Integer spaceId) {
        return routeProgressRepository.findBySpaceId(spaceId)
            .map(RouteProgressMapper::toDTO)
            .orElseGet(RouteProgressMapper::idle);
    }

    /** Upsert: reset this space's row to a fresh in_progress job. */
    public RouteProgressResponseDTO startRouteProgress(Integer spaceId, int total) {
        LocalDateTime now = Timestamps.nowUtc();
        RouteProgress progress = routeProgressRepository.findBySpaceId(spaceId).orElseGet(RouteProgress::new);
        progress.setSpaceId(spaceId);
        progress.setStatus("in_progress");
        progress.setCompleted(0);
        progress.setTotal(total);
        progress.setError(null);
        progress.setStartedAt(now);
        progress.setFinishedAt(null);
        progress.setUpdatedAt(now);

        return saveAndEmit(progress);
    }

    /** Never throws — a failed progress write shouldn't fail the routing job. */
    public void updateRouteProgress(Integer spaceId, int completed, int total) {
        try {
            routeProgressRepository.findBySpaceId(spaceId).ifPresent(progress -> {
                progress.setCompleted(completed);
                progress.setTotal(total);
                progress.setUpdatedAt(Timestamps.nowUtc());
                saveAndEmit(progress);
            });
        } catch (RuntimeException e) {
            log.error("Error updating route progress", e);
        }
    }

    /** Null if there's no row or the write failed. */
    public RouteProgressResponseDTO completeRouteProgress(Integer spaceId) {
        try {
            return routeProgressRepository.findBySpaceId(spaceId).map(progress -> {
                LocalDateTime now = Timestamps.nowUtc();
                progress.setStatus("done");
                progress.setFinishedAt(now);
                progress.setUpdatedAt(now);
                return saveAndEmit(progress);
            }).orElse(null);
        } catch (RuntimeException e) {
            log.error("Error completing route progress", e);
            return null;
        }
    }

    /** Null if there's no row or the write failed. */
    public RouteProgressResponseDTO failRouteProgress(Integer spaceId, String error) {
        try {
            return routeProgressRepository.findBySpaceId(spaceId).map(progress -> {
                LocalDateTime now = Timestamps.nowUtc();
                progress.setStatus("error");
                progress.setError(error);
                progress.setFinishedAt(now);
                progress.setUpdatedAt(now);
                return saveAndEmit(progress);
            }).orElse(null);
        } catch (RuntimeException e) {
            log.error("Error failing route progress", e);
            return null;
        }
    }

    private RouteProgressResponseDTO saveAndEmit(RouteProgress progress) {
        RouteProgress saved = routeProgressRepository.save(progress);
        RouteProgressResponseDTO dto = RouteProgressMapper.toDTO(saved);
        eventPublisher.publishEvent(new RouteProgressEvent(saved.getSpaceId(), dto));
        return dto;
    }
}
