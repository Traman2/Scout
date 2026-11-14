package com.scout.springserver.service;

import com.scout.springserver.client.OverpassClient;
import com.scout.springserver.client.OverpassClient.OverpassPoi;
import com.scout.springserver.model.Poi;
import com.scout.springserver.util.SqlValues;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * backend/src/services/poi.service.ts.
 *
 * pois are shared across all spaces (deduped by OSM node id via the unique
 * osm_id) — discovering for one space upserts into that shared table. Only
 * this space's links in space_pois are deleted-and-replaced on re-discover.
 *
 * Unlike the neon-http version, the delete + upsert + link-insert runs in one
 * transaction, so an interrupted request can no longer leave a space with
 * zero linked POIs.
 */
@Service
public class PoiService {

    private static final Logger log = LoggerFactory.getLogger(PoiService.class);

    public static final int DEFAULT_POI_RADIUS_METERS = 1500;
    private static final int ROWS_PER_INSERT = 1000;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final OverpassClient overpass;

    public PoiService(JdbcClient jdbc, TransactionTemplate tx, OverpassClient overpass) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.overpass = overpass;
    }

    public List<Poi> discoverPoisForSpace(int spaceId, double lat, double lng, int radiusMeters) {
        List<OverpassPoi> found = overpass.findNearbyPois(lat, lng, radiusMeters);

        // A node matching two category filters would otherwise appear twice,
        // and ON CONFLICT can't update the same row twice in one statement.
        Map<Long, OverpassPoi> unique = new LinkedHashMap<>();
        for (OverpassPoi poi : found) unique.putIfAbsent(poi.osmId(), poi);

        List<Poi> upserted = tx.execute(status -> {
            jdbc.sql("DELETE FROM space_pois WHERE space_id = ?").param(spaceId).update();

            if (unique.isEmpty()) return List.<Poi>of();

            List<Poi> rows = new ArrayList<>();
            for (List<OverpassPoi> chunk : SqlValues.chunks(new ArrayList<>(unique.values()), ROWS_PER_INSERT)) {
                List<Object> params = new ArrayList<>();
                for (OverpassPoi poi : chunk) {
                    params.add(poi.osmId());
                    params.add(poi.category());
                    params.add(poi.name());
                    params.add(poi.lat());
                    params.add(poi.lng());
                }
                rows.addAll(jdbc.sql("INSERT INTO pois (osm_id, category, name, lat, lng) VALUES "
                                + SqlValues.tuples("(?, ?, ?, ?, ?)", chunk.size())
                                + " ON CONFLICT (osm_id) DO UPDATE SET"
                                + " category = excluded.category, name = excluded.name,"
                                + " lat = excluded.lat, lng = excluded.lng"
                                + " RETURNING *")
                        .params(params)
                        .query(Poi.ROW_MAPPER)
                        .list());
            }

            for (List<Poi> chunk : SqlValues.chunks(rows, ROWS_PER_INSERT)) {
                List<Object> params = new ArrayList<>();
                for (Poi poi : chunk) {
                    params.add(spaceId);
                    params.add(poi.id());
                }
                jdbc.sql("INSERT INTO space_pois (space_id, poi_id) VALUES "
                                + SqlValues.tuples("(?, ?)", chunk.size()))
                        .params(params)
                        .update();
            }

            return rows;
        });

        if (upserted == null || upserted.isEmpty()) {
            log.info("No POIs found for space {}", spaceId);
            return List.of();
        }

        log.info("Discovered {} POIs for space {}", upserted.size(), spaceId);
        return upserted;
    }

    public List<Poi> listPoisForSpace(int spaceId) {
        return jdbc.sql("""
                        SELECT p.id, p.osm_id, p.category, p.name, p.lat, p.lng, p.created_at
                        FROM space_pois sp
                        INNER JOIN pois p ON sp.poi_id = p.id
                        WHERE sp.space_id = ?
                        """)
                .param(spaceId)
                .query(Poi.ROW_MAPPER)
                .list();
    }
}
