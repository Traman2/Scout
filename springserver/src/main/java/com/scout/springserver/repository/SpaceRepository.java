package com.scout.springserver.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.scout.springserver.model.Space;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Integer> {
    List<Space> findAllByOwnerIdOrderByIdAsc(Integer ownerId);
    Optional<Space> findByIdAndOwnerId(Integer id, Integer ownerId);
    List<Space> findAllByOwnerIdAndLatIsNotNullAndLngIsNotNull(Integer ownerId);
    List<Space> findAllByIdInAndOwnerId(List<Integer> ids, Integer ownerId);
}
