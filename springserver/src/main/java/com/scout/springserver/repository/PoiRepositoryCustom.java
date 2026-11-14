package com.scout.springserver.repository;

import java.util.List;

import com.scout.springserver.model.Poi;

/**
 * Bulk operations JPA can't express efficiently. `pois` uses serial (IDENTITY)
 * ids, which disables Hibernate's insert batching — saveAll() would cost one
 * round trip to Neon per POI (hundreds per discover). These do it in one
 * statement, same as drizzle's `.insert().values([...]).onConflictDoUpdate()`.
 */
public interface PoiRepositoryCustom {

    /** Insert-or-update by osm_id; returns the stored rows (with ids). */
    List<Poi> upsertAllByOsmId(List<Poi> pois);

    /** Inserts the space_pois links for one space. */
    void linkAllToSpace(Integer spaceId, List<Integer> poiIds);
}
