package com.scout.springserver.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.scout.springserver.model.Poi;

@Repository
public interface PoiRepository extends JpaRepository<Poi, Integer>, PoiRepositoryCustom {

    /** The POIs linked to a space through space_pois. */
    @Query("select p from Poi p, SpacePoi sp where sp.poiId = p.id and sp.spaceId = :spaceId")
    List<Poi> findAllBySpaceId(@Param("spaceId") Integer spaceId);
}
