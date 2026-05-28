package com.hub.gisdatahub.dashboard.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.dashboard.dto.AreaNavigationResponse;
import com.hub.gisdatahub.dashboard.dto.AreaPopulationChartResponse;
import com.hub.gisdatahub.dashboard.dto.AreaPopulationDto;
import com.hub.gisdatahub.dashboard.dto.FloatingPopulationChartResponse;
import com.hub.gisdatahub.dashboard.dto.FloatingPopulationRankItem;
import com.hub.gisdatahub.dashboard.dto.PopulationChartDataset;
import com.hub.gisdatahub.dashboard.mapper.DashboardPopulationMapper;

@Service
public class DashboardBoundaryService {

    private static final String EMPTY_FEATURE_COLLECTION = """
            {"type":"FeatureCollection","features":[]}
            """;
    private static final Bbox DEFAULT_SEOUL_BBOX = new Bbox(126.75, 37.42, 127.20, 37.72);
    private static final double MAX_SIDO_BBOX_AREA = 80.0;
    private static final double MAX_SIGUNGU_BBOX_AREA = 25.0;
    private static final double MAX_EUPMYEONDONG_BBOX_AREA = 2.0;
    private static final double MAX_JIPGYEGU_BBOX_AREA = 0.5;
    private static final String NAVIGATION_PROPERTIES_SQL = """
                            'parentAreaCode', (
                                SELECT p.area_code
                                FROM public.sd_area_code p
                                WHERE p.is_active = TRUE
                                  AND (
                                      (c.level = 'SIGUNGU'
                                       AND p.level = 'SIDO'
                                       AND p.sido_code = c.sido_code)
                                      OR (
                                          c.level = 'EUPMYEONDONG'
                                          AND p.level = 'SIGUNGU'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                      )
                                      OR (
                                          c.level = 'JIPGYEGU'
                                          AND p.level = 'EUPMYEONDONG'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                          AND p.eupmyeondong_code = c.eupmyeondong_code
                                      )
                                  )
                                ORDER BY p.area_code
                                LIMIT 1
                            ),
                            'parentName', (
                                SELECT p.name
                                FROM public.sd_area_code p
                                WHERE p.is_active = TRUE
                                  AND (
                                      (c.level = 'SIGUNGU'
                                       AND p.level = 'SIDO'
                                       AND p.sido_code = c.sido_code)
                                      OR (
                                          c.level = 'EUPMYEONDONG'
                                          AND p.level = 'SIGUNGU'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                      )
                                      OR (
                                          c.level = 'JIPGYEGU'
                                          AND p.level = 'EUPMYEONDONG'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                          AND p.eupmyeondong_code = c.eupmyeondong_code
                                      )
                                  )
                                ORDER BY p.area_code
                                LIMIT 1
                            ),
                            'parentAreaName', (
                                SELECT p.name
                                FROM public.sd_area_code p
                                WHERE p.is_active = TRUE
                                  AND (
                                      (c.level = 'SIGUNGU'
                                       AND p.level = 'SIDO'
                                       AND p.sido_code = c.sido_code)
                                      OR (
                                          c.level = 'EUPMYEONDONG'
                                          AND p.level = 'SIGUNGU'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                      )
                                      OR (
                                          c.level = 'JIPGYEGU'
                                          AND p.level = 'EUPMYEONDONG'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                          AND p.eupmyeondong_code = c.eupmyeondong_code
                                      )
                                  )
                                ORDER BY p.area_code
                                LIMIT 1
                            ),
                            'parentFullName', (
                                SELECT p.full_name
                                FROM public.sd_area_code p
                                WHERE p.is_active = TRUE
                                  AND (
                                      (c.level = 'SIGUNGU'
                                       AND p.level = 'SIDO'
                                       AND p.sido_code = c.sido_code)
                                      OR (
                                          c.level = 'EUPMYEONDONG'
                                          AND p.level = 'SIGUNGU'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                      )
                                      OR (
                                          c.level = 'JIPGYEGU'
                                          AND p.level = 'EUPMYEONDONG'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                          AND p.eupmyeondong_code = c.eupmyeondong_code
                                      )
                                  )
                                ORDER BY p.area_code
                                LIMIT 1
                            ),
                            'parentLevel', (
                                SELECT p.level
                                FROM public.sd_area_code p
                                WHERE p.is_active = TRUE
                                  AND (
                                      (c.level = 'SIGUNGU'
                                       AND p.level = 'SIDO'
                                       AND p.sido_code = c.sido_code)
                                      OR (
                                          c.level = 'EUPMYEONDONG'
                                          AND p.level = 'SIGUNGU'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                      )
                                      OR (
                                          c.level = 'JIPGYEGU'
                                          AND p.level = 'EUPMYEONDONG'
                                          AND p.sido_code = c.sido_code
                                          AND p.sigungu_code = c.sigungu_code
                                          AND p.eupmyeondong_code = c.eupmyeondong_code
                                      )
                                  )
                                ORDER BY p.area_code
                                LIMIT 1
                            ),
                            'childLevel', CASE c.level
                                WHEN 'SIDO' THEN 'SIGUNGU'
                                WHEN 'SIGUNGU' THEN 'EUPMYEONDONG'
                                WHEN 'EUPMYEONDONG' THEN 'JIPGYEGU'
                                ELSE NULL
                            END,
                            'canDrillDown', c.level != 'JIPGYEGU'
            """;
    private static final List<String> POPULATION_AGE_LABELS = List.of(
            "0-9", "10-14", "15-19", "20-24", "25-29", "30-34", "35-39",
            "40-44", "45-49", "50-54", "55-59", "60-64", "65-69", "70-74");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DashboardPopulationMapper populationMapper;

    public DashboardBoundaryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            DashboardPopulationMapper populationMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.populationMapper = populationMapper;
    }

    public String getAreaBoundaries(String level, String sidoCode, String parentAreaCode, String bbox) {
        String resolvedLevel = resolveLevel(level);
        String resolvedSidoCode = resolveSidoCode(sidoCode);
        String resolvedParentAreaCode = resolveOptionalAreaCode(parentAreaCode);
        Bbox resolvedBbox = resolveBbox(bbox);
        validateBboxArea(resolvedLevel, resolvedBbox);

        if ("SIDO".equals(resolvedLevel)) {
            return getSidoBoundaries(resolvedSidoCode, resolvedBbox);
        }

        if ("SIGUNGU".equals(resolvedLevel)) {
            return getSigunguBoundaries(resolvedSidoCode, resolvedParentAreaCode, resolvedBbox);
        }

        if ("EUPMYEONDONG".equals(resolvedLevel)) {
            return getEupmyeondongBoundaries(resolvedSidoCode, resolvedParentAreaCode, resolvedBbox);
        }

        return getJipgyeguBoundaries(resolvedSidoCode, resolvedParentAreaCode, resolvedBbox);
    }

    public AreaNavigationResponse getAreaNavigation(String areaCode) {
        AreaMeta areaMeta = findAreaMeta(resolveAreaCode(areaCode));
        Optional<AreaMeta> parentArea = findParentAreaMeta(areaMeta);

        return AreaNavigationResponse.builder()
                .areaCode(areaMeta.areaCode())
                .areaName(areaMeta.name())
                .fullName(areaMeta.fullName())
                .areaLevel(areaMeta.level())
                .parentAreaCode(parentArea.map(AreaMeta::areaCode).orElse(null))
                .parentAreaName(parentArea.map(AreaMeta::name).orElse(null))
                .parentFullName(parentArea.map(AreaMeta::fullName).orElse(null))
                .parentLevel(parentArea.map(AreaMeta::level).orElse(null))
                .childLevel(childLevel(areaMeta.level()).orElse(null))
                .canDrillDown(childLevel(areaMeta.level()).isPresent())
                .build();
    }

    public AreaPopulationChartResponse getAreaPopulation(String areaCode, String areaLevel, String date, String hour) {
        String resolvedAreaCode = resolveAreaCode(areaCode);
        String resolvedAreaLevel = resolveAreaLevel(areaLevel, "SIGUNGU");
        LocalDate resolvedDate = resolveDate(date);
        String resolvedHour = resolveHour(hour);

        AreaPopulationDto population = populationMapper.findAreaPopulation(
                resolvedAreaCode,
                resolvedAreaLevel,
                resolvedDate,
                resolvedHour);

        if (population == null) {
            throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "조회 가능한 생활인구 데이터가 없습니다.");
        }

        return AreaPopulationChartResponse.builder()
                .areaCode(population.getAreaCode())
                .areaName(population.getAreaName())
                .fullName(population.getFullName())
                .baseDate(population.getBaseDate())
                .hour(population.getHour())
                .totalPopulation(population.getTotalPopulation())
                .malePopulation(population.getMalePopulation())
                .femalePopulation(population.getFemalePopulation())
                .labels(POPULATION_AGE_LABELS)
                .datasets(List.of(
                        PopulationChartDataset.builder()
                                .label("남성")
                                .data(maleAgeData(population))
                                .backgroundColor("rgba(54, 162, 235, 0.35)")
                                .borderColor("rgb(54, 162, 235)")
                                .build(),
                        PopulationChartDataset.builder()
                                .label("여성")
                                .data(femaleAgeData(population))
                                .backgroundColor("rgba(255, 99, 132, 0.35)")
                                .borderColor("rgb(255, 99, 132)")
                                .build()))
                .build();
    }

    public AreaPopulationChartResponse getAreaPopulation(String areaCode, String date, String hour) {
        return getAreaPopulation(areaCode, null, date, hour);
    }

    public FloatingPopulationChartResponse getFloatingPopulation(
            String areaCode,
            String areaLevel,
            String date,
            String hour) {
        String resolvedAreaCode = resolveAreaCode(areaCode);
        AreaMeta areaMeta = findAreaMeta(resolvedAreaCode);
        String resolvedAreaLevel = resolveAreaLevel(areaLevel, areaMeta.level());
        LocalDate resolvedDate = resolveDate(date);
        String resolvedHour = resolveHour(hour);

        List<FloatingPopulationPoint> points = findFloatingPopulationPoints(
                areaMeta.areaCode(),
                resolvedAreaLevel,
                resolvedDate,
                resolvedHour);

        if (points.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 가능한 유동인구 데이터가 없습니다.");
        }

        FloatingPopulationPoint firstPoint = points.get(0);

        return FloatingPopulationChartResponse.builder()
                .areaCode(areaMeta.areaCode())
                .areaName(areaMeta.name())
                .fullName(areaMeta.fullName())
                .areaLevel(resolvedAreaLevel)
                .baseDate(firstPoint.baseDate())
                .hour(firstPoint.hour())
                .totalVisitorCount(firstPoint.totalVisitorCount())
                .rowCount(firstPoint.totalRowCount())
                .sensorCount(firstPoint.totalSensorCount())
                .notice(floatingPopulationNotice(resolvedAreaLevel))
                .labels(points.stream()
                        .map(FloatingPopulationPoint::label)
                        .toList())
                .datasets(List.of(
                        PopulationChartDataset.builder()
                                .label("유동인구")
                                .data(points.stream()
                                        .map(FloatingPopulationPoint::visitorCount)
                                        .toList())
                                .backgroundColor("rgba(20, 184, 166, 0.35)")
                                .borderColor("rgb(15, 118, 110)")
                                .build()))
                .rankings(points.stream()
                        .limit(5)
                        .map(point -> FloatingPopulationRankItem.builder()
                                .label(point.label())
                                .visitorCount(point.visitorCount())
                                .build())
                        .toList())
                .build();
    }

    private String getSidoBoundaries(String sidoCode, Bbox bbox) {
        String sidoFilter = sidoCode == null ? "" : "AND c.sido_code = :sidoCode";
        String sql = """
                WITH features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(
                            ST_SimplifyPreserveTopology(
                                ST_MakeValid(
                                    ST_Buffer(
                                        ST_UnaryUnion(ST_Collect(
                                            ST_Buffer(
                                                ST_SimplifyPreserveTopology(ST_MakeValid(b.geom), 0.01),
                                                0.003
                                            )
                                        )),
                                        -0.003
                                    )
                                ),
                                0.005
                            ),
                            5
                        )::jsonb,
                        'properties', jsonb_build_object(
                            'areaCode', c.area_code,
                            'sidoCode', c.sido_code,
                            'sigunguCode', c.sigungu_code,
                            'eupmyeondongCode', c.eupmyeondong_code,
                            'name', c.name,
                            'fullName', c.full_name,
                            'level', c.level,
%s
                        )
                    ) AS feature
                    FROM public.sd_area_code c
                    JOIN public.sd_area_code child
                        ON child.sido_code = c.sido_code
                       AND child.level = 'SIGUNGU'
                       AND child.is_active = TRUE
                    JOIN public.sd_area_boundary b
                        ON b.area_code = child.area_code
                       AND b.boundary_type = 'SIGUNGU'
                    WHERE c.level = 'SIDO'
                      AND c.is_active = TRUE
                      AND ST_Intersects(
                          b.geom,
                          ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                      )
                      %s
                    GROUP BY
                        c.area_code,
                        c.sido_code,
                        c.sigungu_code,
                        c.eupmyeondong_code,
                        c.name,
                        c.full_name,
                        c.level
                    ORDER BY c.sido_code
                )
                SELECT jsonb_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(jsonb_agg(feature), '[]'::jsonb)
                )::text
                FROM features
                """.formatted(NAVIGATION_PROPERTIES_SQL, sidoFilter);

        String geoJson = queryGeoJson(sql, sidoCode, null, bbox);
        return geoJson == null ? EMPTY_FEATURE_COLLECTION : geoJson;
    }

    private String getSigunguBoundaries(String sidoCode, String parentAreaCode, Bbox bbox) {
        String sidoFilter = sidoCode == null ? "" : "AND c.sido_code = :sidoCode";
        String parentFilter = sigunguParentFilter(parentAreaCode);
        String sql = """
                WITH features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(ST_SimplifyPreserveTopology(ST_MakeValid(b.geom), 0.001), 5)::jsonb,
                        'properties', jsonb_build_object(
                            'areaCode', c.area_code,
                            'sidoCode', c.sido_code,
                            'sigunguCode', c.sigungu_code,
                            'eupmyeondongCode', c.eupmyeondong_code,
                            'name', c.name,
                            'fullName', c.full_name,
                            'level', c.level,
%s
                        )
                    ) AS feature
                    FROM public.sd_area_boundary b
                    JOIN public.sd_area_code c
                        ON c.area_code = b.area_code
                    WHERE c.level = 'SIGUNGU'
                      AND c.is_active = TRUE
                      AND b.boundary_type = 'SIGUNGU'
                      AND ST_Intersects(
                          b.geom,
                          ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                      )
                      %s
                      %s
                    ORDER BY c.sigungu_code
                )
                SELECT jsonb_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(jsonb_agg(feature), '[]'::jsonb)
                )::text
                FROM features
                """.formatted(NAVIGATION_PROPERTIES_SQL, sidoFilter, parentFilter);

        String geoJson = queryGeoJson(sql, sidoCode, parentAreaCode, bbox);
        return geoJson == null ? EMPTY_FEATURE_COLLECTION : geoJson;
    }

    private String getEupmyeondongBoundaries(String sidoCode, String parentAreaCode, Bbox bbox) {
        String sidoFilter = sidoCode == null ? "" : "AND c.sido_code = :sidoCode";
        String parentFilter = eupmyeondongParentFilter(parentAreaCode);
        String sql = """
                WITH features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(ST_SimplifyPreserveTopology(b.geom, 0.0005), 5)::jsonb,
                        'properties', jsonb_build_object(
                            'areaCode', c.area_code,
                            'sidoCode', c.sido_code,
                            'sigunguCode', c.sigungu_code,
                            'eupmyeondongCode', c.eupmyeondong_code,
                            'name', c.name,
                            'fullName', c.full_name,
                            'level', c.level,
%s
                        )
                    ) AS feature
                    FROM public.sd_area_boundary b
                    JOIN public.sd_area_code c
                        ON c.area_code = b.area_code
                    WHERE c.level = 'EUPMYEONDONG'
                      AND c.is_active = TRUE
                      AND b.boundary_type = 'EUPMYEONDONG'
                      AND ST_Intersects(
                          b.geom,
                          ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                      )
                      %s
                      %s
                    ORDER BY c.area_code
                )
                SELECT jsonb_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(jsonb_agg(feature), '[]'::jsonb)
                )::text
                FROM features
                """.formatted(NAVIGATION_PROPERTIES_SQL, sidoFilter, parentFilter);

        String geoJson = queryGeoJson(sql, sidoCode, parentAreaCode, bbox);
        return geoJson == null ? EMPTY_FEATURE_COLLECTION : geoJson;
    }

    private String getJipgyeguBoundaries(String sidoCode, String parentAreaCode, Bbox bbox) {
        String sidoFilter = sidoCode == null ? "" : "AND c.sido_code = :sidoCode";
        String parentFilter = jipgyeguParentFilter(parentAreaCode);
        String sql = """
                WITH features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(ST_SimplifyPreserveTopology(b.geom, 0.0002), 5)::jsonb,
                        'properties', jsonb_build_object(
                            'areaCode', c.area_code,
                            'sidoCode', c.sido_code,
                            'sigunguCode', c.sigungu_code,
                            'eupmyeondongCode', c.eupmyeondong_code,
                            'name', c.name,
                            'fullName', c.full_name,
                            'level', c.level,
%s
                        )
                    ) AS feature
                    FROM public.sd_area_boundary b
                    JOIN public.sd_area_code c
                        ON c.area_code = b.area_code
                    WHERE c.level = 'JIPGYEGU'
                      AND c.is_active = TRUE
                      AND COALESCE(b.boundary_type, c.level) = 'JIPGYEGU'
                      AND ST_Intersects(
                          b.geom,
                          ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                      )
                      %s
                      %s
                    ORDER BY c.area_code
                )
                SELECT jsonb_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(jsonb_agg(feature), '[]'::jsonb)
                )::text
                FROM features
                """.formatted(NAVIGATION_PROPERTIES_SQL, sidoFilter, parentFilter);

        String geoJson = queryGeoJson(sql, sidoCode, parentAreaCode, bbox);
        return geoJson == null ? EMPTY_FEATURE_COLLECTION : geoJson;
    }

    private String sigunguParentFilter(String parentAreaCode) {
        if (parentAreaCode == null) {
            return "";
        }

        return """
                AND EXISTS (
                    SELECT 1
                    FROM public.sd_area_code p
                    WHERE p.area_code = :parentAreaCode
                      AND p.level = 'SIDO'
                      AND p.sido_code = c.sido_code
                )
                """;
    }

    private String eupmyeondongParentFilter(String parentAreaCode) {
        if (parentAreaCode == null) {
            return "";
        }

        return """
                AND EXISTS (
                    SELECT 1
                    FROM public.sd_area_code p
                    WHERE p.area_code = :parentAreaCode
                      AND (
                          (p.level = 'SIDO' AND p.sido_code = c.sido_code)
                          OR (
                              p.level = 'SIGUNGU'
                              AND p.sido_code = c.sido_code
                              AND p.sigungu_code = c.sigungu_code
                          )
                      )
                )
                """;
    }

    private String jipgyeguParentFilter(String parentAreaCode) {
        if (parentAreaCode == null) {
            return "";
        }

        return """
                AND EXISTS (
                    SELECT 1
                    FROM public.sd_area_code p
                    WHERE p.area_code = :parentAreaCode
                      AND (
                          (p.level = 'SIDO' AND p.sido_code = c.sido_code)
                          OR (
                              p.level = 'SIGUNGU'
                              AND p.sido_code = c.sido_code
                              AND p.sigungu_code = c.sigungu_code
                          )
                          OR (
                              p.level = 'EUPMYEONDONG'
                              AND p.sido_code = c.sido_code
                              AND p.sigungu_code = c.sigungu_code
                              AND p.eupmyeondong_code = c.eupmyeondong_code
                          )
                      )
                )
                """;
    }

    private String queryGeoJson(String sql, String sidoCode, String parentAreaCode, Bbox bbox) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("minLon", bbox.minLon())
                .addValue("minLat", bbox.minLat())
                .addValue("maxLon", bbox.maxLon())
                .addValue("maxLat", bbox.maxLat());

        if (sidoCode != null) {
            params.addValue("sidoCode", sidoCode);
        }
        if (parentAreaCode != null) {
            params.addValue("parentAreaCode", parentAreaCode);
        }

        return jdbcTemplate.queryForObject(sql, params, String.class);
    }

    private String resolveLevel(String level) {
        if (level == null || level.isBlank()) {
            return "SIGUNGU";
        }

        String upperLevel = level.trim().toUpperCase();
        if ("SIDO".equals(upperLevel)
                || "SIGUNGU".equals(upperLevel)
                || "EUPMYEONDONG".equals(upperLevel)
                || "JIPGYEGU".equals(upperLevel)) {
            return upperLevel;
        }

        return "SIGUNGU";
    }

    private String resolveAreaLevel(String areaLevel, String defaultLevel) {
        if (areaLevel == null || areaLevel.isBlank()) {
            return defaultLevel;
        }

        String upperLevel = areaLevel.trim().toUpperCase();
        if ("SIDO".equals(upperLevel)
                || "SIGUNGU".equals(upperLevel)
                || "EUPMYEONDONG".equals(upperLevel)
                || "JIPGYEGU".equals(upperLevel)) {
            return upperLevel;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "areaLevel은 SIDO, SIGUNGU, EUPMYEONDONG, JIPGYEGU 중 하나여야 합니다.");
    }

    private String resolveSidoCode(String sidoCode) {
        if (sidoCode == null || sidoCode.isBlank() || "ALL".equalsIgnoreCase(sidoCode.trim())) {
            return null;
        }
        return sidoCode.trim();
    }

    private String resolveAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "areaCode는 필수입니다.");
        }
        return areaCode.trim();
    }

    private String resolveOptionalAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            return null;
        }
        return areaCode.trim();
    }

    private LocalDate resolveDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "date는 yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    private String resolveHour(String hour) {
        if (hour == null || hour.isBlank()) {
            return null;
        }

        try {
            int parsedHour = Integer.parseInt(hour.trim());
            if (parsedHour < 0 || parsedHour > 23) {
                throw invalidHour();
            }
            return "%02d".formatted(parsedHour);
        } catch (NumberFormatException e) {
            throw invalidHour();
        }
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
        double maxArea = switch (level) {
            case "SIDO" -> MAX_SIDO_BBOX_AREA;
            case "EUPMYEONDONG" -> MAX_EUPMYEONDONG_BBOX_AREA;
            case "JIPGYEGU" -> MAX_JIPGYEGU_BBOX_AREA;
            default -> MAX_SIGUNGU_BBOX_AREA;
        };
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

    private ResponseStatusException invalidHour() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "hour는 0부터 23 사이의 값이어야 합니다.");
    }

    private List<BigDecimal> maleAgeData(AreaPopulationDto population) {
        return List.of(
                zeroIfNull(population.getMale0To9()),
                zeroIfNull(population.getMale10To14()),
                zeroIfNull(population.getMale15To19()),
                zeroIfNull(population.getMale20To24()),
                zeroIfNull(population.getMale25To29()),
                zeroIfNull(population.getMale30To34()),
                zeroIfNull(population.getMale35To39()),
                zeroIfNull(population.getMale40To44()),
                zeroIfNull(population.getMale45To49()),
                zeroIfNull(population.getMale50To54()),
                zeroIfNull(population.getMale55To59()),
                zeroIfNull(population.getMale60To64()),
                zeroIfNull(population.getMale65To69()),
                zeroIfNull(population.getMale70To74()));
    }

    private List<BigDecimal> femaleAgeData(AreaPopulationDto population) {
        return List.of(
                zeroIfNull(population.getFemale0To9()),
                zeroIfNull(population.getFemale10To14()),
                zeroIfNull(population.getFemale15To19()),
                zeroIfNull(population.getFemale20To24()),
                zeroIfNull(population.getFemale25To29()),
                zeroIfNull(population.getFemale30To34()),
                zeroIfNull(population.getFemale35To39()),
                zeroIfNull(population.getFemale40To44()),
                zeroIfNull(population.getFemale45To49()),
                zeroIfNull(population.getFemale50To54()),
                zeroIfNull(population.getFemale55To59()),
                zeroIfNull(population.getFemale60To64()),
                zeroIfNull(population.getFemale65To69()),
                zeroIfNull(population.getFemale70To74()));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private AreaMeta findAreaMeta(String areaCode) {
        String sql = """
                SELECT *
                FROM (
                    SELECT
                        area_code,
                        sido_code,
                        sigungu_code,
                        eupmyeondong_code,
                        name,
                        full_name,
                        level,
                        CASE
                            WHEN area_code = :areaCode THEN 0
                            WHEN LENGTH(:areaCode) = 5
                                 AND level = 'SIGUNGU'
                                 AND sigungu_code = :areaCode THEN 1
                            WHEN LENGTH(:areaCode) = 10
                                 AND SUBSTRING(:areaCode, 6, 5) = '00000'
                                 AND level = 'SIGUNGU'
                                 AND sigungu_code = SUBSTRING(:areaCode, 1, 5) THEN 2
                            WHEN LENGTH(:areaCode) = 8
                                 AND level = 'EUPMYEONDONG'
                                 AND eupmyeondong_code = :areaCode THEN 3
                            WHEN LENGTH(:areaCode) = 10
                                 AND SUBSTRING(:areaCode, 9, 2) = '00'
                                 AND level = 'EUPMYEONDONG'
                                 AND eupmyeondong_code = SUBSTRING(:areaCode, 1, 8) THEN 4
                            ELSE 9
                        END AS match_priority
                    FROM public.sd_area_code
                    WHERE area_code = :areaCode
                       OR (
                           LENGTH(:areaCode) = 5
                           AND level = 'SIGUNGU'
                           AND sigungu_code = :areaCode
                       )
                       OR (
                           LENGTH(:areaCode) = 10
                           AND SUBSTRING(:areaCode, 6, 5) = '00000'
                           AND level = 'SIGUNGU'
                           AND sigungu_code = SUBSTRING(:areaCode, 1, 5)
                       )
                       OR (
                           LENGTH(:areaCode) = 8
                           AND level = 'EUPMYEONDONG'
                           AND eupmyeondong_code = :areaCode
                       )
                       OR (
                           LENGTH(:areaCode) = 10
                           AND SUBSTRING(:areaCode, 9, 2) = '00'
                           AND level = 'EUPMYEONDONG'
                           AND eupmyeondong_code = SUBSTRING(:areaCode, 1, 8)
                       )
                ) matched_area
                ORDER BY match_priority, area_code
                LIMIT 1
                """;

        List<AreaMeta> areas = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("areaCode", areaCode),
                (rs, rowNum) -> mapAreaMeta(rs));

        if (areas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "존재하지 않는 지역코드입니다.");
        }

        return areas.get(0);
    }

    private Optional<AreaMeta> findParentAreaMeta(AreaMeta areaMeta) {
        String sql = """
                SELECT
                    area_code,
                    sido_code,
                    sigungu_code,
                    eupmyeondong_code,
                    name,
                    full_name,
                    level
                FROM public.sd_area_code p
                WHERE p.is_active = TRUE
                  AND (
                      (:areaLevel = 'SIGUNGU'
                       AND p.level = 'SIDO'
                       AND p.sido_code = :sidoCode)
                      OR (
                          :areaLevel = 'EUPMYEONDONG'
                          AND p.level = 'SIGUNGU'
                          AND p.sido_code = :sidoCode
                          AND p.sigungu_code = :sigunguCode
                      )
                      OR (
                          :areaLevel = 'JIPGYEGU'
                          AND p.level = 'EUPMYEONDONG'
                          AND p.sido_code = :sidoCode
                          AND p.sigungu_code = :sigunguCode
                          AND p.eupmyeondong_code = :eupmyeondongCode
                      )
                  )
                ORDER BY p.area_code
                LIMIT 1
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("areaLevel", areaMeta.level())
                .addValue("sidoCode", areaMeta.sidoCode())
                .addValue("sigunguCode", areaMeta.sigunguCode())
                .addValue("eupmyeondongCode", areaMeta.eupmyeondongCode());

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapAreaMeta(rs))
                .stream()
                .findFirst();
    }

    private Optional<String> childLevel(String areaLevel) {
        return switch (areaLevel) {
            case "SIDO" -> Optional.of("SIGUNGU");
            case "SIGUNGU" -> Optional.of("EUPMYEONDONG");
            case "EUPMYEONDONG" -> Optional.of("JIPGYEGU");
            default -> Optional.empty();
        };
    }

    private List<FloatingPopulationPoint> findFloatingPopulationPoints(
            String areaCode,
            String areaLevel,
            LocalDate baseDate,
            String hour) {
        String dateFilter = baseDate == null ? "" : "AND f.base_date = :baseDate";
        String hourFilter = hour == null ? "" : "AND f.hour = :hour";
        String labelExpression = "SIDO".equals(areaLevel)
                ? "COALESCE(sg.name, ac.name, NULLIF(f.autonomous_district, ''), '미분류')"
                : "COALESCE(NULLIF(f.administrative_district, ''), NULLIF(f.autonomous_district, ''), '미분류')";

        String sql = """
                WITH selected_area AS (
                    SELECT
                        area_code,
                        sido_code,
                        sigungu_code,
                        eupmyeondong_code,
                        level
                    FROM public.sd_area_code
                    WHERE area_code = :areaCode
                    LIMIT 1
                ),
                scope_candidates AS (
                    SELECT DISTINCT area_code, priority
                    FROM (
                        SELECT c.area_code, 1 AS priority
                        FROM public.sd_area_code c
                        CROSS JOIN selected_area s
                        WHERE :areaLevel = 'SIDO'
                          AND c.level = 'EUPMYEONDONG'
                          AND c.sido_code = s.sido_code

                        UNION ALL

                        SELECT c.area_code, 2 AS priority
                        FROM public.sd_area_code c
                        CROSS JOIN selected_area s
                        WHERE :areaLevel = 'SIDO'
                          AND c.level = 'SIGUNGU'
                          AND c.sido_code = s.sido_code

                        UNION ALL

                        SELECT s.area_code, 3 AS priority
                        FROM selected_area s
                        WHERE :areaLevel = 'SIDO'

                        UNION ALL

                        SELECT c.area_code, 1 AS priority
                        FROM public.sd_area_code c
                        CROSS JOIN selected_area s
                        WHERE :areaLevel = 'SIGUNGU'
                          AND c.level = 'EUPMYEONDONG'
                          AND c.sido_code = s.sido_code
                          AND c.sigungu_code = s.sigungu_code

                        UNION ALL

                        SELECT s.area_code, 2 AS priority
                        FROM selected_area s
                        WHERE :areaLevel = 'SIGUNGU'

                        UNION ALL

                        SELECT s.area_code, 1 AS priority
                        FROM selected_area s
                        WHERE :areaLevel = 'EUPMYEONDONG'

                        UNION ALL

                        SELECT s.area_code, 1 AS priority
                        FROM selected_area s
                        WHERE :areaLevel = 'JIPGYEGU'

                        UNION ALL

                        SELECT c.area_code, 2 AS priority
                        FROM public.sd_area_code c
                        CROSS JOIN selected_area s
                        WHERE :areaLevel = 'JIPGYEGU'
                          AND c.level = 'EUPMYEONDONG'
                          AND c.sido_code = s.sido_code
                          AND c.sigungu_code = s.sigungu_code
                          AND c.eupmyeondong_code = s.eupmyeondong_code

                        UNION ALL

                        SELECT c.area_code, 2 AS priority
                        FROM public.sd_area_code c
                        CROSS JOIN selected_area s
                        WHERE :areaLevel = 'EUPMYEONDONG'
                          AND c.level = 'SIGUNGU'
                          AND c.sido_code = s.sido_code
                          AND c.sigungu_code = s.sigungu_code

                        UNION ALL

                        SELECT c.area_code, 3 AS priority
                        FROM public.sd_area_code c
                        CROSS JOIN selected_area s
                        WHERE :areaLevel = 'JIPGYEGU'
                          AND c.level = 'SIGUNGU'
                          AND c.sido_code = s.sido_code
                          AND c.sigungu_code = s.sigungu_code
                    ) candidate
                ),
                available_priority AS (
                    SELECT MIN(sc.priority) AS priority
                    FROM scope_candidates sc
                    JOIN public.sd_area_floating_population f
                        ON f.area_code = sc.area_code
                    WHERE 1 = 1
                      %s
                      %s
                ),
                filtered_rows AS (
                    SELECT f.*
                    FROM public.sd_area_floating_population f
                    JOIN scope_candidates sc
                        ON sc.area_code = f.area_code
                    JOIN available_priority ap
                        ON ap.priority = sc.priority
                    WHERE 1 = 1
                      %s
                      %s
                ),
                latest_key AS (
                    SELECT base_date, hour
                    FROM filtered_rows
                    ORDER BY base_date DESC, hour DESC
                    LIMIT 1
                ),
                target_rows AS (
                    SELECT f.*
                    FROM filtered_rows f
                    JOIN latest_key k
                        ON k.base_date = f.base_date
                       AND k.hour = f.hour
                ),
                totals AS (
                    SELECT
                        COALESCE(SUM(visitor_count), 0)::numeric AS total_visitor_count,
                        COUNT(*)::int AS total_row_count,
                        COUNT(DISTINCT serial_no)::int AS total_sensor_count,
                        MAX(base_date) AS base_date,
                        MAX(hour) AS hour
                    FROM target_rows
                ),
                aggregated AS (
                    SELECT
                        %s AS label,
                        COALESCE(SUM(f.visitor_count), 0)::numeric AS visitor_count
                    FROM target_rows f
                    LEFT JOIN public.sd_area_code ac
                        ON ac.area_code = f.area_code
                    LEFT JOIN public.sd_area_code sg
                        ON sg.level = 'SIGUNGU'
                       AND sg.sido_code = ac.sido_code
                       AND sg.sigungu_code = ac.sigungu_code
                    GROUP BY label
                )
                SELECT
                    a.label,
                    a.visitor_count,
                    t.total_visitor_count,
                    t.total_row_count,
                    t.total_sensor_count,
                    t.base_date,
                    t.hour
                FROM aggregated a
                CROSS JOIN totals t
                ORDER BY a.visitor_count DESC, a.label
                LIMIT 10
                """.formatted(dateFilter, hourFilter, dateFilter, hourFilter, labelExpression);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("areaCode", areaCode)
                .addValue("areaLevel", areaLevel);

        if (baseDate != null) {
            params.addValue("baseDate", baseDate);
        }
        if (hour != null) {
            params.addValue("hour", hour);
        }

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapFloatingPopulationPoint(rs));
    }

    private String floatingPopulationNotice(String areaLevel) {
        if ("EUPMYEONDONG".equals(areaLevel) || "JIPGYEGU".equals(areaLevel)) {
            return "집계구/행정동 단위 S-DoT 매핑 데이터가 없으면 소속 자치구 기준으로 표시합니다.";
        }

        return null;
    }

    private AreaMeta mapAreaMeta(ResultSet rs) throws SQLException {
        return new AreaMeta(
                rs.getString("area_code"),
                rs.getString("sido_code"),
                rs.getString("sigungu_code"),
                rs.getString("eupmyeondong_code"),
                rs.getString("name"),
                rs.getString("full_name"),
                rs.getString("level"));
    }

    private FloatingPopulationPoint mapFloatingPopulationPoint(ResultSet rs) throws SQLException {
        return new FloatingPopulationPoint(
                rs.getString("label"),
                rs.getBigDecimal("visitor_count"),
                rs.getBigDecimal("total_visitor_count"),
                rs.getInt("total_row_count"),
                rs.getInt("total_sensor_count"),
                rs.getObject("base_date", LocalDate.class),
                rs.getString("hour"));
    }

    private record AreaMeta(
            String areaCode,
            String sidoCode,
            String sigunguCode,
            String eupmyeondongCode,
            String name,
            String fullName,
            String level) {
    }

    private record FloatingPopulationPoint(
            String label,
            BigDecimal visitorCount,
            BigDecimal totalVisitorCount,
            int totalRowCount,
            int totalSensorCount,
            LocalDate baseDate,
            String hour) {
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
