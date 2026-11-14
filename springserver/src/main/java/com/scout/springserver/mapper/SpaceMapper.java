package com.scout.springserver.mapper;

import com.scout.springserver.dto.CreateSpaceRequestDTO;
import com.scout.springserver.dto.SpaceResponseDTO;
import com.scout.springserver.model.Space;
import com.scout.springserver.util.Timestamps;

public class SpaceMapper {

    public static SpaceResponseDTO toDTO(Space space) {
        SpaceResponseDTO dto = new SpaceResponseDTO();
        dto.setId(space.getId());
        dto.setOwnerId(space.getOwnerId());
        dto.setName(space.getName());
        dto.setAddress(space.getAddress());
        dto.setLat(space.getLat());
        dto.setLng(space.getLng());
        dto.setCreatedAt(Timestamps.toIso(space.getCreatedAt()));
        dto.setUpdatedAt(Timestamps.toIso(space.getUpdatedAt()));
        return dto;
    }

    public static Space toModel(CreateSpaceRequestDTO request, Integer ownerId) {
        Space space = new Space();
        space.setOwnerId(ownerId);
        space.setName(request.getName());
        space.setAddress(request.getAddress());
        return space;
    }
}
