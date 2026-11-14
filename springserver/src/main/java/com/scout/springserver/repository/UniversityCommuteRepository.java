package com.scout.springserver.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.scout.springserver.model.UniversityCommute;

@Repository
public interface UniversityCommuteRepository extends JpaRepository<UniversityCommute, Integer> {
    List<UniversityCommute> findAllByUniversityId(Integer universityId);

    @Modifying
    @Query("delete from UniversityCommute uc where uc.universityId = :universityId")
    int deleteAllByUniversityId(@Param("universityId") Integer universityId);
}
