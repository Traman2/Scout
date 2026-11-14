package com.scout.springserver.mapper;

import com.scout.springserver.client.OverpassClient.OverpassPoi;
import com.scout.springserver.dto.PoiResponseDTO;
import com.scout.springserver.model.Poi;
import com.scout.springserver.util.Timestamps;

public class PoiMapper {

    public static PoiResponseDTO toDTO(Poi poi) {
        PoiResponseDTO dto = new PoiResponseDTO();
        dto.setId(poi.getId());
        dto.setOsmId(poi.getOsmId());
        dto.setCategory(poi.getCategory());
        dto.setName(poi.getName());
        dto.setLat(poi.getLat());
        dto.setLng(poi.getLng());
        dto.setCreatedAt(Timestamps.toIso(poi.getCreatedAt()));
        return dto;
    }

    /** A POI found by Overpass, not yet stored. */
    public static Poi toModel(OverpassPoi found) {
        Poi poi = new Poi();
        poi.setOsmId(found.osmId());
        poi.setCategory(found.category());
        poi.setName(found.name());
        poi.setLat(found.lat());
        poi.setLng(found.lng());
        return poi;
    }
}
