package com.scout.springserver.mapper;

import com.scout.springserver.client.OtpClient.TripPlan;
import com.scout.springserver.dto.TripDurationsDTO;
import com.scout.springserver.model.TripDurations;
import com.scout.springserver.util.Timestamps;

/** Copies the shared duration/geometry fields used by routes and university commutes. */
public class TripMapper {

    public static void copyTrip(TripPlan trip, TripDurations target) {
        target.setDurationSeconds(trip.durationSeconds());
        target.setWalkDurationSeconds(trip.walkDurationSeconds());
        target.setMetroDurationSeconds(trip.metroDurationSeconds());
        target.setBusDurationSeconds(trip.busDurationSeconds());
        target.setTramDurationSeconds(trip.tramDurationSeconds());
        target.setOtherDurationSeconds(trip.otherDurationSeconds());
        target.setWaitDurationSeconds(trip.waitDurationSeconds());
        target.setRouteGeometry(trip.routeGeometry());
    }

    public static void copyTrip(TripDurations source, TripDurationsDTO target) {
        target.setDurationSeconds(source.getDurationSeconds());
        target.setWalkDurationSeconds(source.getWalkDurationSeconds());
        target.setMetroDurationSeconds(source.getMetroDurationSeconds());
        target.setBusDurationSeconds(source.getBusDurationSeconds());
        target.setTramDurationSeconds(source.getTramDurationSeconds());
        target.setOtherDurationSeconds(source.getOtherDurationSeconds());
        target.setWaitDurationSeconds(source.getWaitDurationSeconds());
        target.setRouteGeometry(source.getRouteGeometry());
        target.setComputedAt(Timestamps.toIso(source.getComputedAt()));
    }
}
