package com.scout.springserver.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.scout.springserver.model.Route;

@Repository
public interface RouteRepository extends JpaRepository<Route, Integer> {
    List<Route> findAllBySpaceId(Integer spaceId);

    @Modifying
    @Query("delete from Route r where r.spaceId = :spaceId")
    int deleteAllBySpaceId(@Param("spaceId") Integer spaceId);
}
