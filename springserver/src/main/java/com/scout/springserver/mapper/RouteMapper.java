package com.scout.springserver.mapper;

import com.scout.springserver.client.OtpClient.TripPlan;
import com.scout.springserver.dto.SpaceRouteResponseDTO;
import com.scout.springserver.model.Poi;
import com.scout.springserver.model.Route;

public class RouteMapper {

    /** A route joined with its POI (listRoutesForSpace's select). */
    public static SpaceRouteResponseDTO toDTO(Route route, Poi poi) {
        SpaceRouteResponseDTO dto = new SpaceRouteResponseDTO();
        dto.setId(route.getId());
        dto.setPoiId(route.getPoiId());
        dto.setPoiName(poi.getName());
        dto.setPoiCategory(poi.getCategory());
        TripMapper.copyTrip(route, dto);
        return dto;
    }

    public static Route toModel(Integer spaceId, Integer poiId, TripPlan trip) {
        Route route = new Route();
        route.setSpaceId(spaceId);
        route.setPoiId(poiId);
        TripMapper.copyTrip(trip, route);
        return route;
    }
}
