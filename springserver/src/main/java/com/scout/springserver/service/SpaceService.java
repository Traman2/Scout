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

    /** A saved space plus how its automatic geocode went (for analytics). */
    public record SpaceResult(SpaceResponseDTO space, GeocodeAttempt geocodeAttempt) {}

    private final SpaceRepository spaceRepository;
    private final NominatimClient nominatimClient;

    public SpaceService(SpaceRepository spaceRepository, NominatimClient nominatimClient) {
        this.spaceRepository = spaceRepository;
        this.nominatimClient = nominatimClient;
    }

    /** Geocoding failure never blocks a create/update — the space is saved with null coordinates. */
    private GeocodeAttempt tryGeocode(String address) {
        try {
            Optional<Coordinates> result = nominatimClient.geocodeAddress(address);
            if (result.isEmpty()) {
                log.warn("Geocoding found no match for a space address");
                return new GeocodeAttempt.Failed(new AddressNotFoundException());
            }
            return new GeocodeAttempt.Succeeded(result.get());
        } catch (RuntimeException e) {
            log.warn("Geocoding request failed, continuing without coordinates");
            return new GeocodeAttempt.Failed(e);
        }
    }

    public SpaceResult createSpace(Integer ownerId, CreateSpaceRequestDTO createSpaceRequestDTO) {
        GeocodeAttempt geocodeAttempt = tryGeocode(createSpaceRequestDTO.getAddress());

        Space space = SpaceMapper.toModel(createSpaceRequestDTO, ownerId);
        if (geocodeAttempt instanceof GeocodeAttempt.Succeeded succeeded) {
            space.setLat(succeeded.coordinates().lat());
            space.setLng(succeeded.coordinates().lng());
        }

        Space newSpace = spaceRepository.save(space);

        log.info("Space {} created for user {}", newSpace.getId(), ownerId);
        return new SpaceResult(SpaceMapper.toDTO(newSpace), geocodeAttempt);
    }

    public List<SpaceResponseDTO> getSpaces(Integer ownerId) {
        return spaceRepository.findAllByOwnerIdOrderByIdAsc(ownerId).stream()
            .map(SpaceMapper::toDTO).toList();
    }

    public long countSpaces(Integer ownerId) {
        return spaceRepository.countByOwnerId(ownerId);
    }

    public SpaceResponseDTO getSpace(Integer id, Integer ownerId) {
        return SpaceMapper.toDTO(getOwnedSpace(id, ownerId));
    }

    /** The entity itself, for other services. Throws SpaceNotFoundException if it isn't the caller's. */
    public Space getOwnedSpace(Integer id, Integer ownerId) {
        return spaceRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(SpaceNotFoundException::new);
    }

    public SpaceResult updateSpace(Integer id, Integer ownerId, UpdateSpaceRequestDTO updateSpaceRequestDTO) {
        Space space = getOwnedSpace(id, ownerId);

        GeocodeAttempt geocodeAttempt = updateSpaceRequestDTO.getAddress() != null
            ? tryGeocode(updateSpaceRequestDTO.getAddress())
            : null;

        if (updateSpaceRequestDTO.getName() != null) space.setName(updateSpaceRequestDTO.getName());
        if (updateSpaceRequestDTO.getAddress() != null) space.setAddress(updateSpaceRequestDTO.getAddress());

        if (geocodeAttempt instanceof GeocodeAttempt.Succeeded succeeded) {
            space.setLat(succeeded.coordinates().lat());
            space.setLng(succeeded.coordinates().lng());
        }

        // Explicit lat/lng (e.g. from the manual-coordinates form) always wins
        // over an address-triggered geocode result.
        if (updateSpaceRequestDTO.getLat() != null) space.setLat(updateSpaceRequestDTO.getLat());
        if (updateSpaceRequestDTO.getLng() != null) space.setLng(updateSpaceRequestDTO.getLng());

        space.setUpdatedAt(Timestamps.nowUtc());
        Space updatedSpace = spaceRepository.save(space);

        log.info("Space {} updated for user {}", id, ownerId);
        return new SpaceResult(SpaceMapper.toDTO(updatedSpace), geocodeAttempt);
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
