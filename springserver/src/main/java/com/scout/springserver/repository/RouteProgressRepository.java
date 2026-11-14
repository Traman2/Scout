package com.scout.springserver.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.scout.springserver.model.RouteProgress;

@Repository
public interface RouteProgressRepository extends JpaRepository<RouteProgress, Integer> {
    Optional<RouteProgress> findBySpaceId(Integer spaceId);
}
