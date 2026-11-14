package com.scout.springserver.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.scout.springserver.model.SpacePoi;

@Repository
public interface SpacePoiRepository extends JpaRepository<SpacePoi, Integer> {

    @Modifying
    @Query("delete from SpacePoi sp where sp.spaceId = :spaceId")
    int deleteAllBySpaceId(@Param("spaceId") Integer spaceId);
}
