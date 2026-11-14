package com.scout.springserver.mapper;

import com.scout.springserver.dto.RouteProgressResponseDTO;
import com.scout.springserver.model.RouteProgress;
import com.scout.springserver.util.Timestamps;

public class RouteProgressMapper {

    public static RouteProgressResponseDTO toDTO(RouteProgress progress) {
        RouteProgressResponseDTO dto = new RouteProgressResponseDTO();
        dto.setStatus(progress.getStatus());
        dto.setCompleted(progress.getCompleted());
        dto.setTotal(progress.getTotal());
        dto.setError(progress.getError());
        dto.setStartedAt(Timestamps.toIso(progress.getStartedAt()));
        dto.setFinishedAt(Timestamps.toIso(progress.getFinishedAt()));
        return dto;
    }

    /** What a space that has never computed routes reports. */
    public static RouteProgressResponseDTO idle() {
        RouteProgressResponseDTO dto = new RouteProgressResponseDTO();
        dto.setStatus("idle");
        dto.setCompleted(0);
        dto.setTotal(0);
        return dto;
    }
}
