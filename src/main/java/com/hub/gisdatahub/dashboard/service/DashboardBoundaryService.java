package com.hub.gisdatahub.dashboard.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardBoundaryService {

    private static final String EMPTY_FEATURE_COLLECTION = """
            {"type":"FeatureCollection","features":[]}
            """;
    private static final Bbox DEFAULT_SEOUL_BBOX = new Bbox(126.75, 37.42, 127.20, 37.72);
    private static final double MAX_SIGUNGU_BBOX_AREA = 25.0;
    private static final double MAX_EUPMYEONDONG_BBOX_AREA = 2.0;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DashboardBoundaryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getAreaBoundaries(String level, String sidoCode, String bbox) {
        String resolvedLevel = resolveLevel(level);
        String resolvedSidoCode = resolveSidoCode(sidoCode);
        Bbox resolvedBbox = resolveBbox(bbox);
        validateBboxArea(resolvedLevel, resolvedBbox);

        if ("SIGUNGU".equals(resolvedLevel)) {
            return getSigunguBoundaries(resolvedSidoCode, resolvedBbox);
        }

        return getEupmyeondongBoundaries(resolvedSidoCode, resolvedBbox);
    }

    private String getSigunguBoundaries(String sidoCode, Bbox bbox) {
        String sidoFilter = sidoCode == null ? "" : "AND c.sido_code = :sidoCode";
        String sql = """
                WITH sigungu AS (
                    SELECT
                        c.sigungu_code || '00000' AS area_code,
                        c.sigungu_code,
                        COALESCE(MAX(sgg.name), MAX(c.name)) AS name,
                        COALESCE(MAX(sgg.full_name), MAX(c.full_name)) AS full_name,
                        ST_Multi(
                            ST_CollectionExtract(
                                ST_UnaryUnion(ST_Collect(ST_MakeValid(b.geom))),
                                3
                            )
                        ) AS geom
                    FROM public.sd_area_boundary b
                    JOIN public.sd_area_code c
                        ON c.area_code = b.area_code
                    LEFT JOIN public.sd_area_code sgg
                        ON sgg.area_code = c.sigungu_code || '00000'
                    WHERE c.level = 'EUPMYEONDONG'
                      AND ST_Intersects(
                          b.geom,
                          ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                      )
                      %s
                    GROUP BY c.sigungu_code
                ),
                features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(ST_SimplifyPreserveTopology(geom, 0.001), 5)::jsonb,
                        'properties', jsonb_build_object(
                            'areaCode', area_code,
                            'sigunguCode', sigungu_code,
                            'name', name,
                            'fullName', full_name,
                            'level', 'SIGUNGU'
                        )
                    ) AS feature
                    FROM sigungu
                    WHERE geom IS NOT NULL
                    ORDER BY sigungu_code
                )
                SELECT jsonb_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(jsonb_agg(feature), '[]'::jsonb)
                )::text
                FROM features
                """.formatted(sidoFilter);

        String geoJson = queryGeoJson(sql, sidoCode, bbox);
        return geoJson == null ? EMPTY_FEATURE_COLLECTION : geoJson;
    }

    private String getEupmyeondongBoundaries(String sidoCode, Bbox bbox) {
        String sidoFilter = sidoCode == null ? "" : "AND c.sido_code = :sidoCode";
        String sql = """
                WITH features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(ST_SimplifyPreserveTopology(b.geom, 0.0005), 5)::jsonb,
                        'properties', jsonb_build_object(
                            'areaCode', c.area_code,
                            'sigunguCode', c.sigungu_code,
                            'eupmyeondongCode', c.eupmyeondong_code,
                            'name', c.name,
                            'fullName', c.full_name,
                            'level', c.level
                        )
                    ) AS feature
                    FROM public.sd_area_boundary b
                    JOIN public.sd_area_code c
                        ON c.area_code = b.area_code
                    WHERE c.level = 'EUPMYEONDONG'
                      AND ST_Intersects(
                          b.geom,
                          ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                      )
                      %s
                    ORDER BY c.area_code
                )
                SELECT jsonb_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(jsonb_agg(feature), '[]'::jsonb)
                )::text
                FROM features
                """.formatted(sidoFilter);

        String geoJson = queryGeoJson(sql, sidoCode, bbox);
        return geoJson == null ? EMPTY_FEATURE_COLLECTION : geoJson;
    }

    private String queryGeoJson(String sql, String sidoCode, Bbox bbox) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("minLon", bbox.minLon())
                .addValue("minLat", bbox.minLat())
                .addValue("maxLon", bbox.maxLon())
                .addValue("maxLat", bbox.maxLat());

        if (sidoCode != null) {
            params.addValue("sidoCode", sidoCode);
        }

        return jdbcTemplate.queryForObject(sql, params, String.class);
    }

    private String resolveLevel(String level) {
        if (level == null || level.isBlank()) {
            return "SIGUNGU";
        }

        String upperLevel = level.trim().toUpperCase();
        if ("EUPMYEONDONG".equals(upperLevel)) {
            return upperLevel;
        }

        return "SIGUNGU";
    }

    private String resolveSidoCode(String sidoCode) {
        if (sidoCode == null || sidoCode.isBlank() || "ALL".equalsIgnoreCase(sidoCode.trim())) {
            return null;
        }
        return sidoCode.trim();
    }

    private Bbox resolveBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return DEFAULT_SEOUL_BBOX;
        }

        String[] values = bbox.split(",");
        if (values.length != 4) {
            throw invalidBbox();
        }

        try {
            double minLon = Double.parseDouble(values[0].trim());
            double minLat = Double.parseDouble(values[1].trim());
            double maxLon = Double.parseDouble(values[2].trim());
            double maxLat = Double.parseDouble(values[3].trim());
            Bbox parsedBbox = new Bbox(minLon, minLat, maxLon, maxLat);

            if (!parsedBbox.isValid()) {
                throw invalidBbox();
            }

            return parsedBbox;
        } catch (NumberFormatException e) {
            throw invalidBbox();
        }
    }

    private void validateBboxArea(String level, Bbox bbox) {
        double maxArea = "EUPMYEONDONG".equals(level) ? MAX_EUPMYEONDONG_BBOX_AREA : MAX_SIGUNGU_BBOX_AREA;
        if (bbox.area() > maxArea) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "bbox 범위가 너무 넓습니다. 지도를 확대한 후 다시 조회하세요.");
        }
    }

    private ResponseStatusException invalidBbox() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "bbox는 minLon,minLat,maxLon,maxLat 형식의 유효한 좌표여야 합니다.");
    }

    private record Bbox(double minLon, double minLat, double maxLon, double maxLat) {

        private boolean isValid() {
            return Double.isFinite(minLon)
                    && Double.isFinite(minLat)
                    && Double.isFinite(maxLon)
                    && Double.isFinite(maxLat)
                    && minLon >= -180
                    && maxLon <= 180
                    && minLat >= -90
                    && maxLat <= 90
                    && minLon < maxLon
                    && minLat < maxLat;
        }

        private double area() {
            return (maxLon - minLon) * (maxLat - minLat);
        }
    }
}
