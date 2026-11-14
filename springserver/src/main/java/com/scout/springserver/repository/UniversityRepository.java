package com.scout.springserver.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.scout.springserver.model.University;

@Repository
public interface UniversityRepository extends JpaRepository<University, Integer> {
    Optional<University> findByOwnerId(Integer ownerId);
}
