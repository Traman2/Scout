package com.scout.springserver.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.scout.springserver.client.Coordinates;
import com.scout.springserver.client.NominatimClient;
import com.scout.springserver.dto.CreateSpaceRequestDTO;
import com.scout.springserver.dto.SpaceResponseDTO;
import com.scout.springserver.dto.UpdateSpaceRequestDTO;
import com.scout.springserver.exception.AddressNotFoundException;
import com.scout.springserver.exception.SpaceNotFoundException;
import com.scout.springserver.mapper.SpaceMapper;
import com.scout.springserver.model.Space;
import com.scout.springserver.repository.SpaceRepository;
import com.scout.springserver.util.Timestamps;

/** backend/src/services/space.service.ts. Every lookup is scoped to the owner. */
@Service
public class SpaceService {
    private static final Logger log = LoggerFactory.getLogger(SpaceService.class);

    private final SpaceRepository spaceRepository;
    private final NominatimClient nominatimClient;

    public SpaceService(SpaceRepository spaceRepository, NominatimClient nominatimClient) {
        this.spaceRepository = spaceRepository;
        this.nominatimClient = nominatimClient;
    }

    /**
     * Geocoding failure never blocks a create/update — the space is saved with
     * null coordinates and can be retried via POST /api/spaces/:id/geocode.
     */
    private Optional<Coordinates> tryGeocode(String address) {
        try {
            Optional<Coordinates> result = nominatimClient.geocodeAddress(address);
            if (result.isEmpty()) log.warn("Geocoding found no match for a space address");
            return result;
        } catch (RuntimeException e) {
            log.warn("Geocoding request failed, continuing without coordinates");
            return Optional.empty();
        }
    }

    public SpaceResponseDTO createSpace(Integer ownerId, CreateSpaceRequestDTO createSpaceRequestDTO) {
        Space space = SpaceMapper.toModel(createSpaceRequestDTO, ownerId);
        tryGeocode(createSpaceRequestDTO.getAddress()).ifPresent(coordinates -> {
            space.setLat(coordinates.lat());
            space.setLng(coordinates.lng());
        });

        Space newSpace = spaceRepository.save(space);

        log.info("Space {} created for user {}", newSpace.getId(), ownerId);
        return SpaceMapper.toDTO(newSpace);
    }

    public List<SpaceResponseDTO> getSpaces(Integer ownerId) {
        return spaceRepository.findAllByOwnerIdOrderByIdAsc(ownerId).stream()
            .map(SpaceMapper::toDTO).toList();
    }

    public SpaceResponseDTO getSpace(Integer id, Integer ownerId) {
        return SpaceMapper.toDTO(getOwnedSpace(id, ownerId));
    }

    /** The entity itself, for other services. Throws SpaceNotFoundException if it isn't the caller's. */
    public Space getOwnedSpace(Integer id, Integer ownerId) {
        return spaceRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(SpaceNotFoundException::new);
    }

    public SpaceResponseDTO updateSpace(Integer id, Integer ownerId, UpdateSpaceRequestDTO updateSpaceRequestDTO) {
        Space space = getOwnedSpace(id, ownerId);

        if (updateSpaceRequestDTO.getName() != null) space.setName(updateSpaceRequestDTO.getName());
        if (updateSpaceRequestDTO.getAddress() != null) {
            space.setAddress(updateSpaceRequestDTO.getAddress());
            tryGeocode(updateSpaceRequestDTO.getAddress()).ifPresent(coordinates -> {
                space.setLat(coordinates.lat());
                space.setLng(coordinates.lng());
            });
        }

        // Explicit lat/lng (e.g. from the manual-coordinates form) always wins
        // over an address-triggered geocode result.
        if (updateSpaceRequestDTO.getLat() != null) space.setLat(updateSpaceRequestDTO.getLat());
        if (updateSpaceRequestDTO.getLng() != null) space.setLng(updateSpaceRequestDTO.getLng());

        space.setUpdatedAt(Timestamps.nowUtc());
        Space updatedSpace = spaceRepository.save(space);

        log.info("Space {} updated for user {}", id, ownerId);
        return SpaceMapper.toDTO(updatedSpace);
    }

    public SpaceResponseDTO geocodeSpace(Integer id, Integer ownerId) {
        Space space = getOwnedSpace(id, ownerId);

        Coordinates geocoded = nominatimClient.geocodeAddress(space.getAddress())
            .orElseThrow(AddressNotFoundException::new);

        space.setLat(geocoded.lat());
        space.setLng(geocoded.lng());
        space.setUpdatedAt(Timestamps.nowUtc());
        Space updatedSpace = spaceRepository.save(space);

        log.info("Space {} geocoded for user {}", id, ownerId);
        return SpaceMapper.toDTO(updatedSpace);
    }

    /** Child rows (space_pois, routes, route_progress, university_commutes) go via ON DELETE CASCADE. */
    public void deleteSpace(Integer id, Integer ownerId) {
        Space space = getOwnedSpace(id, ownerId);
        spaceRepository.delete(space);
        log.info("Space {} deleted for user {}", id, ownerId);
    }
}
