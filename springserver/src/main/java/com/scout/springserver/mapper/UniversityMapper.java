package com.scout.springserver.mapper;

import com.scout.springserver.client.OtpClient.TripPlan;
import com.scout.springserver.dto.UniversityCommuteResponseDTO;
import com.scout.springserver.dto.UniversityResponseDTO;
import com.scout.springserver.model.Space;
import com.scout.springserver.model.University;
import com.scout.springserver.model.UniversityCommute;
import com.scout.springserver.util.Timestamps;

public class UniversityMapper {

    public static UniversityResponseDTO toDTO(University university) {
        if (university == null) return null;
        UniversityResponseDTO dto = new UniversityResponseDTO();
        dto.setId(university.getId());
        dto.setOwnerId(university.getOwnerId());
        dto.setName(university.getName());
        dto.setAddress(university.getAddress());
        dto.setLat(university.getLat());
        dto.setLng(university.getLng());
        dto.setCreatedAt(Timestamps.toIso(university.getCreatedAt()));
        dto.setUpdatedAt(Timestamps.toIso(university.getUpdatedAt()));
        return dto;
    }

    /** A commute joined with its space (listUniversityCommutesForUser's select). */
    public static UniversityCommuteResponseDTO toCommuteDTO(UniversityCommute commute, Space space) {
        UniversityCommuteResponseDTO dto = new UniversityCommuteResponseDTO();
        dto.setId(commute.getId());
        dto.setSpaceId(commute.getSpaceId());
        dto.setSpaceName(space.getName());
        dto.setSpaceAddress(space.getAddress());
        dto.setSpaceLat(space.getLat());
        dto.setSpaceLng(space.getLng());
        TripMapper.copyTrip(commute, dto);
        return dto;
    }

    public static UniversityCommute toCommuteModel(Integer universityId, Integer spaceId, TripPlan trip) {
        UniversityCommute commute = new UniversityCommute();
        commute.setUniversityId(universityId);
        commute.setSpaceId(spaceId);
        TripMapper.copyTrip(trip, commute);
        return commute;
    }
}
