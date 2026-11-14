package com.scout.springserver.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.scout.springserver.client.Coordinates;
import com.scout.springserver.client.NominatimClient;
import com.scout.springserver.client.OtpClient;
import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.UniversityCommuteResponseDTO;
import com.scout.springserver.dto.UniversityRequestDTO;
import com.scout.springserver.dto.UniversityResponseDTO;
import com.scout.springserver.exception.NoGeocodedSpacesException;
import com.scout.springserver.exception.UniversityAddressNotFoundException;
import com.scout.springserver.exception.UniversityNotConfiguredException;
import com.scout.springserver.mapper.UniversityMapper;
import com.scout.springserver.model.Space;
import com.scout.springserver.model.University;
import com.scout.springserver.model.UniversityCommute;
import com.scout.springserver.repository.SpaceRepository;
import com.scout.springserver.repository.UniversityCommuteRepository;
import com.scout.springserver.repository.UniversityRepository;
import com.scout.springserver.util.Timestamps;

/** backend/src/services/university.service.ts */
@Service
public class UniversityService {
    private static final Logger log = LoggerFactory.getLogger(UniversityService.class);
    private static final int OTP_CONCURRENCY = 3;

    private final UniversityRepository universityRepository;
    private final UniversityCommuteRepository universityCommuteRepository;
    private final SpaceRepository spaceRepository;
    private final NominatimClient nominatimClient;
    private final OtpClient otpClient;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executor;

    public UniversityService(
            UniversityRepository universityRepository,
            UniversityCommuteRepository universityCommuteRepository,
            SpaceRepository spaceRepository,
            NominatimClient nominatimClient,
            OtpClient otpClient,
            TransactionTemplate transactionTemplate,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.universityRepository = universityRepository;
        this.universityCommuteRepository = universityCommuteRepository;
        this.spaceRepository = spaceRepository;
        this.nominatimClient = nominatimClient;
        this.otpClient = otpClient;
        this.transactionTemplate = transactionTemplate;
        this.executor = executor;
    }

    /** Null when the user hasn't saved a university yet. */
    public UniversityResponseDTO getUniversity(Integer ownerId) {
        return universityRepository.findByOwnerId(ownerId).map(UniversityMapper::toDTO).orElse(null);
    }

    /** Upsert on owner_id. Clears existing commutes, which point at the old destination. */
    public UniversityResponseDTO saveUniversity(Integer ownerId, UniversityRequestDTO universityRequestDTO) {
        Coordinates geocoded = nominatimClient.geocodeAddress(universityRequestDTO.getAddress())
            .orElseThrow(UniversityAddressNotFoundException::new);

        University saved = transactionTemplate.execute(status -> {
            Optional<University> existing = universityRepository.findByOwnerId(ownerId);
            University university = existing.orElseGet(University::new);
            university.setOwnerId(ownerId);
            university.setName(universityRequestDTO.getName());
            university.setAddress(universityRequestDTO.getAddress());
            university.setLat(geocoded.lat());
            university.setLng(geocoded.lng());
            if (existing.isPresent()) university.setUpdatedAt(Timestamps.nowUtc());

            University stored = universityRepository.save(university);
            universityCommuteRepository.deleteAllByUniversityId(stored.getId());
            return stored;
        });

        log.info("University saved for user {}", ownerId);
        return UniversityMapper.toDTO(saved);
    }

    /** Commutes joined with their (still owned) spaces. */
    public List<UniversityCommuteResponseDTO> getCommutes(Integer ownerId) {
        Optional<University> university = universityRepository.findByOwnerId(ownerId);
        if (university.isEmpty()) return List.of();

        List<UniversityCommute> commutes = universityCommuteRepository.findAllByUniversityId(university.get().getId());
        if (commutes.isEmpty()) return List.of();

        Map<Integer, Space> spacesById = spaceRepository
            .findAllByIdInAndOwnerId(commutes.stream().map(UniversityCommute::getSpaceId).toList(), ownerId)
            .stream().collect(Collectors.toMap(Space::getId, Function.identity()));

        return commutes.stream()
            .filter(commute -> spacesById.containsKey(commute.getSpaceId()))
            .map(commute -> UniversityMapper.toCommuteDTO(commute, spacesById.get(commute.getSpaceId())))
            .toList();
    }

    public List<UniversityCommuteResponseDTO> computeCommutes(Integer ownerId) {
        University university = universityRepository.findByOwnerId(ownerId)
            .orElseThrow(UniversityNotConfiguredException::new);

        List<Space> geocodedSpaces = spaceRepository.findAllByOwnerIdAndLatIsNotNullAndLngIsNotNull(ownerId);
        if (geocodedSpaces.isEmpty()) throw new NoGeocodedSpacesException();

        Coordinates destination = new Coordinates(university.getLat(), university.getLng());
        List<UniversityCommute> computed = new ArrayList<>();

        for (int i = 0; i < geocodedSpaces.size(); i += OTP_CONCURRENCY) {
            List<Space> batch = geocodedSpaces.subList(i, Math.min(geocodedSpaces.size(), i + OTP_CONCURRENCY));
            List<CompletableFuture<UniversityCommute>> futures = new ArrayList<>();
            for (Space space : batch) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    if (!space.hasCoordinates()) return null;
                    return otpClient.planTripWithGeometry(new Coordinates(space.getLat(), space.getLng()), destination)
                        .map(trip -> UniversityMapper.toCommuteModel(university.getId(), space.getId(), trip))
                        .orElse(null);
                }, executor));
            }
            RoutingService.joinAll(futures).stream().filter(Objects::nonNull).forEach(computed::add);
        }

        transactionTemplate.executeWithoutResult(status -> {
            universityCommuteRepository.deleteAllByUniversityId(university.getId());
            if (!computed.isEmpty()) universityCommuteRepository.saveAll(computed);
        });

        log.info("Computed {} university commutes for user {}", computed.size(), ownerId);
        return getCommutes(ownerId);
    }
}
