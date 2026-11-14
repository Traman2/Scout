package com.scout.springserver.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.scout.springserver.model.Poi;
import com.scout.springserver.util.SqlValues;
import com.scout.springserver.util.Timestamps;

/**
 * Spring Data picks this up as the implementation of PoiRepositoryCustom.
 * JdbcClient joins the surrounding JPA transaction (JpaTransactionManager
 * exposes the same JDBC connection), so these writes commit or roll back
 * together with the repository calls around them.
 */
class PoiRepositoryCustomImpl implements PoiRepositoryCustom {

    private static final int ROWS_PER_STATEMENT = 1000;

    private static final RowMapper<Poi> POI_ROW = (rs, i) -> {
        Poi poi = new Poi();
        poi.setId(rs.getInt("id"));
        poi.setOsmId(rs.getLong("osm_id"));
        poi.setCategory(rs.getString("category"));
        poi.setName(rs.getString("name"));
        poi.setLat(rs.getDouble("lat"));
        poi.setLng(rs.getDouble("lng"));
        poi.setCreatedAt(Timestamps.read(rs, "created_at"));
        return poi;
    };

    private final JdbcClient jdbc;

    PoiRepositoryCustomImpl(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Poi> upsertAllByOsmId(List<Poi> pois) {
        List<Poi> stored = new ArrayList<>();
        for (List<Poi> chunk : SqlValues.chunks(pois, ROWS_PER_STATEMENT)) {
            List<Object> params = new ArrayList<>();
            for (Poi poi : chunk) {
                params.add(poi.getOsmId());
                params.add(poi.getCategory());
                params.add(poi.getName());
                params.add(poi.getLat());
                params.add(poi.getLng());
            }
            stored.addAll(jdbc.sql("INSERT INTO pois (osm_id, category, name, lat, lng) VALUES "
                            + SqlValues.tuples("(?, ?, ?, ?, ?)", chunk.size())
                            + " ON CONFLICT (osm_id) DO UPDATE SET"
                            + " category = excluded.category, name = excluded.name,"
                            + " lat = excluded.lat, lng = excluded.lng"
                            + " RETURNING *")
                    .params(params)
                    .query(POI_ROW)
                    .list());
        }
        return stored;
    }

    @Override
    public void linkAllToSpace(Integer spaceId, List<Integer> poiIds) {
        for (List<Integer> chunk : SqlValues.chunks(poiIds, ROWS_PER_STATEMENT)) {
            List<Object> params = new ArrayList<>();
            for (Integer poiId : chunk) {
                params.add(spaceId);
                params.add(poiId);
            }
            jdbc.sql("INSERT INTO space_pois (space_id, poi_id) VALUES " + SqlValues.tuples("(?, ?)", chunk.size()))
                    .params(params)
                    .update();
        }
    }
}
