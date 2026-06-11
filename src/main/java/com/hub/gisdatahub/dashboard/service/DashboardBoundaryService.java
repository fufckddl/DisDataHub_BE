package com.hub.gisdatahub.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.dashboard.dto.AreaNavigationResponse;
import com.hub.gisdatahub.dashboard.dto.AreaPopulationChartResponse;
import com.hub.gisdatahub.dashboard.dto.AreaPopulationDto;
import com.hub.gisdatahub.dashboard.dto.DashboardGisDataSourceResponse;
import com.hub.gisdatahub.dashboard.dto.DashboardGisDatasetResponse;
import com.hub.gisdatahub.dashboard.dto.DashboardGisMetricResponse;
import com.hub.gisdatahub.dashboard.dto.DashboardGisRegionStatItem;
import com.hub.gisdatahub.dashboard.dto.DashboardGisRegionStatsResponse;
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
    private static final List<String> DEFAULT_BOUNDARY_CACHE_LEVELS = List.of(
            "SIDO",
            "SIGUNGU",
            "EUPMYEONDONG");
    private static final Set<String> SUPPORTED_BOUNDARY_LEVELS = Set.of(
            "SIDO",
            "SIGUNGU",
            "EUPMYEONDONG",
            "JIPGYEGU");
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
            "0-9", "10-19", "20-29", "30-39", "40-49", "50-59",
            "60-69", "70-79", "80-89", "90-99", "100+");
    private static final List<BigDecimal> POPULATION_AGE_MIDPOINTS = List.of(
            BigDecimal.valueOf(5), BigDecimal.valueOf(15), BigDecimal.valueOf(25),
            BigDecimal.valueOf(35), BigDecimal.valueOf(45), BigDecimal.valueOf(55),
            BigDecimal.valueOf(65), BigDecimal.valueOf(75), BigDecimal.valueOf(85),
            BigDecimal.valueOf(95), BigDecimal.valueOf(105));
    private static final String EV_CHARGER_DATASET_CODE = "KECO_EV_CHARGER_MAIN";
    private static final String EV_CHARGER_COUNT_METRIC_CODE = "EV_CHARGER_COUNT";
    private static final String AIRKOREA_AIR_QUALITY_DATASET_CODE = "AIRKOREA_AIR_QUALITY_MAIN";
    private static final String KMA_VILAGE_FCST_DATASET_CODE = "KMA_VILAGE_FCST_MAIN";
    private static final String KMA_TEMPERATURE_CATEGORY = "T1H";
    private static final String MOIS_AVERAGE_AGE_DATASET_CODE = "MOIS_ADMM_AVG_AGE_MAIN";
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DashboardPopulationMapper populationMapper;
    private final ConcurrentMap<String, String> areaBoundaryCache = new ConcurrentHashMap<>();

    public DashboardBoundaryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            DashboardPopulationMapper populationMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.populationMapper = populationMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmAreaBoundaryCache() {
        CompletableFuture.runAsync(() -> getAreaBoundaryCache(String.join(",", DEFAULT_BOUNDARY_CACHE_LEVELS)));
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

    public String getAreaBoundaryCache(String levels) {
        List<String> resolvedLevels = resolveBoundaryCacheLevels(levels);
        String cacheKey = String.join(",", resolvedLevels);
        return areaBoundaryCache.computeIfAbsent(cacheKey, ignored -> queryAreaBoundaryCache(resolvedLevels));
    }

    private String queryAreaBoundaryCache(List<String> resolvedLevels) {
        String sql = """
                WITH features AS (
                    SELECT
                        c.level,
                        c.area_code,
                        jsonb_build_object(
                            'type', 'Feature',
                            'geometry', ST_AsGeoJSON(
                                ST_Simplify(
                                    b.geom,
                                    CASE c.level
                                        WHEN 'SIDO' THEN 0.01
                                        WHEN 'SIGUNGU' THEN 0.005
                                        WHEN 'EUPMYEONDONG' THEN 0.002
                                        ELSE 0.001
                                    END
                                ),
                                4
                            )::jsonb,
                            'properties', jsonb_build_object(
                                'areaCode', c.area_code,
                                'sidoCode', c.sido_code,
                                'sigunguCode', c.sigungu_code,
                                'eupmyeondongCode', c.eupmyeondong_code,
                                'name', c.name,
                                'fullName', c.full_name,
                                'level', c.level,
                                'parentAreaCode', p.area_code,
                                'parentName', p.name,
                                'parentAreaName', p.name,
                                'parentFullName', p.full_name,
                                'parentLevel', p.level,
                                'childLevel', CASE c.level
                                    WHEN 'SIDO' THEN 'SIGUNGU'
                                    WHEN 'SIGUNGU' THEN 'EUPMYEONDONG'
                                    WHEN 'EUPMYEONDONG' THEN 'JIPGYEGU'
                                    ELSE NULL
                                END,
                                'canDrillDown', c.level != 'JIPGYEGU'
                            )
                        ) AS feature
                    FROM public.sd_area_boundary b
                    JOIN public.sd_area_code c
                        ON c.area_code = b.area_code
                    LEFT JOIN LATERAL (
                        SELECT
                            parent.area_code,
                            parent.name,
                            parent.full_name,
                            parent.level
                        FROM public.sd_area_code parent
                        WHERE parent.is_active = TRUE
                          AND (
                              (
                                  c.level = 'SIGUNGU'
                                  AND parent.level = 'SIDO'
                                  AND parent.sido_code = c.sido_code
                              )
                              OR (
                                  c.level = 'EUPMYEONDONG'
                                  AND parent.level = 'SIGUNGU'
                                  AND parent.sido_code = c.sido_code
                                  AND parent.sigungu_code = c.sigungu_code
                              )
                              OR (
                                  c.level = 'JIPGYEGU'
                                  AND parent.level = 'EUPMYEONDONG'
                                  AND parent.sido_code = c.sido_code
                                  AND parent.sigungu_code = c.sigungu_code
                                  AND parent.eupmyeondong_code = c.eupmyeondong_code
                              )
                          )
                        ORDER BY parent.area_code
                        LIMIT 1
                    ) p ON TRUE
                    WHERE c.level IN (:levels)
                      AND c.is_active = TRUE
                      AND COALESCE(b.boundary_type, c.level) = c.level
                      AND b.geom IS NOT NULL
                ),
                requested_levels AS (
                    SELECT
                        DISTINCT level
                    FROM features
                )
                SELECT COALESCE(
                    jsonb_object_agg(
                        rl.level,
                        jsonb_build_object(
                            'type', 'FeatureCollection',
                            'features', COALESCE(
                                (
                                    SELECT jsonb_agg(f.feature ORDER BY f.area_code)
                                    FROM features f
                                    WHERE f.level = rl.level
                                ),
                                '[]'::jsonb
                            )
                        )
                    ),
                    '{}'::jsonb
                )::text
                FROM requested_levels rl
                """;

        String geoJsonByLevel = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("levels", resolvedLevels),
                String.class);
        return geoJsonByLevel == null ? "{}" : geoJsonByLevel;
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
                        "조회 가능한 주민등록 인구 데이터가 없습니다.");
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

    public List<DashboardGisDataSourceResponse> getDashboardGisDataSources(
            String sourceCategory,
            Integer priority,
            boolean activeOnly) {
        String resolvedSourceCategory = normalizeOptional(sourceCategory);
        String categoryFilter = resolvedSourceCategory == null ? "" : "AND s.source_category = :sourceCategory";
        String priorityFilter = priority == null ? "" : "AND s.priority = :priority";
        String activeFilter = activeOnly ? "AND s.is_active = TRUE" : "";
        String sql = """
                SELECT
                    s.source_code,
                    s.source_name,
                    s.provider_name,
                    s.provider_type,
                    s.source_category,
                    s.official_url,
                    s.api_endpoint,
                    s.api_type,
                    s.data_format,
                    s.auth_type,
                    s.spatial_coverage,
                    s.spatial_granularity,
                    s.temporal_granularity,
                    s.update_cycle,
                    s.coordinate_system,
                    s.has_geometry,
                    s.has_point_coordinate,
                    s.collection_difficulty,
                    s.priority,
                    s.verification_status,
                    s.is_active,
                    (
                        SELECT COUNT(*)::int
                        FROM public.sd_dashboard_dataset d
                        WHERE d.source_code = s.source_code
                    ) AS dataset_count,
                    (
                        SELECT COUNT(*)::int
                        FROM public.sd_dashboard_metric m
                        WHERE EXISTS (
                            SELECT 1
                            FROM public.sd_dashboard_dataset d
                            WHERE d.dataset_code = m.dataset_code
                              AND d.source_code = s.source_code
                        )
                    ) AS metric_count
                FROM public.sd_dashboard_data_source s
                WHERE 1 = 1
                  %s
                  %s
                  %s
                ORDER BY s.priority, s.source_category, s.source_code
                """.formatted(categoryFilter, priorityFilter, activeFilter);

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (resolvedSourceCategory != null) {
            params.addValue("sourceCategory", resolvedSourceCategory);
        }
        if (priority != null) {
            params.addValue("priority", priority);
        }

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapDashboardGisDataSource(rs));
    }

    public List<DashboardGisDatasetResponse> getDashboardGisDatasets(
            String sourceCode,
            String layerType,
            boolean activeOnly) {
        String resolvedSourceCode = normalizeOptional(sourceCode);
        String resolvedLayerType = normalizeOptionalUpper(layerType);
        String sourceFilter = resolvedSourceCode == null ? "" : "AND d.source_code = :sourceCode";
        String layerFilter = resolvedLayerType == null ? "" : "AND d.dashboard_layer_type = :layerType";
        String activeFilter = activeOnly ? "AND s.is_active = TRUE" : "";
        String sql = """
                SELECT
                    d.source_code,
                    d.dataset_code,
                    d.dataset_name,
                    d.dashboard_layer_type,
                    d.dashboard_metric_hint,
                    d.default_geometry_type,
                    d.default_area_level,
                    d.spatial_join_strategy,
                    d.collection_policy,
                    d.display_priority,
                    d.is_initial_candidate,
                    (
                        SELECT COUNT(*)::int
                        FROM public.sd_dashboard_metric m
                        WHERE m.dataset_code = d.dataset_code
                    ) AS metric_count,
                    (
                        SELECT COUNT(*)::int
                        FROM public.sd_dashboard_area_observation o
                        WHERE o.dataset_code = d.dataset_code
                    ) AS observation_count,
                    CASE d.dataset_code
                        WHEN 'STANDARD_LIBRARY_MAIN' THEN (
                            SELECT COUNT(*)::int
                            FROM public.sd_dashboard_standard_library_feature f
                            WHERE f.dataset_code = d.dataset_code
                        )
                        WHEN 'STANDARD_URBAN_PARK_MAIN' THEN (
                            SELECT COUNT(*)::int
                            FROM public.sd_dashboard_standard_urban_park_feature f
                            WHERE f.dataset_code = d.dataset_code
                        )
                        WHEN 'STANDARD_BUS_STOP_MAIN' THEN (
                            SELECT COUNT(*)::int
                            FROM public.sd_dashboard_standard_bus_stop_feature f
                            WHERE f.dataset_code = d.dataset_code
                        )
                        ELSE 0
                    END AS feature_count
                FROM public.sd_dashboard_dataset d
                JOIN public.sd_dashboard_data_source s
                    ON s.source_code = d.source_code
                WHERE 1 = 1
                  %s
                  %s
                  %s
                ORDER BY d.display_priority, d.source_code, d.dataset_code
                """.formatted(sourceFilter, layerFilter, activeFilter);

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (resolvedSourceCode != null) {
            params.addValue("sourceCode", resolvedSourceCode);
        }
        if (resolvedLayerType != null) {
            params.addValue("layerType", resolvedLayerType);
        }

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapDashboardGisDataset(rs));
    }

    public List<DashboardGisMetricResponse> getDashboardGisMetrics(String sourceCode, String datasetCode) {
        String resolvedSourceCode = normalizeOptional(sourceCode);
        String resolvedDatasetCode = normalizeOptional(datasetCode);
        String sourceFilter = resolvedSourceCode == null ? "" : "AND d.source_code = :sourceCode";
        String datasetFilter = resolvedDatasetCode == null ? "" : "AND m.dataset_code = :datasetCode";
        String sql = """
                SELECT
                    m.dataset_code,
                    m.metric_code,
                    m.metric_name,
                    m.value_type,
                    m.unit,
                    m.chart_group,
                    m.sort_order,
                    m.is_default
                FROM public.sd_dashboard_metric m
                JOIN public.sd_dashboard_dataset d
                    ON d.dataset_code = m.dataset_code
                WHERE 1 = 1
                  %s
                  %s
                ORDER BY d.display_priority, m.dataset_code, m.sort_order, m.metric_code
                """.formatted(sourceFilter, datasetFilter);

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (resolvedSourceCode != null) {
            params.addValue("sourceCode", resolvedSourceCode);
        }
        if (resolvedDatasetCode != null) {
            params.addValue("datasetCode", resolvedDatasetCode);
        }

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapDashboardGisMetric(rs));
    }

    public String getDashboardGisFeatures(String datasetCode, String bbox, String areaCode, int limit) {
        String resolvedDatasetCode = normalizeRequired(datasetCode, "datasetCode는 필수입니다.");
        Bbox resolvedBbox = resolveOptionalBbox(bbox);
        String resolvedAreaCode = resolveOptionalAreaCode(areaCode);
        int resolvedLimit = normalizeFeatureLimit(limit);
        Optional<AreaMeta> selectedAreaMeta = resolvedAreaCode == null
                ? Optional.empty()
                : findOptionalAreaMeta(resolvedAreaCode);
        Set<String> scopeAreaCodes = resolvedAreaCode == null
                ? Set.of()
                : dashboardObservationScopeAreaCodes(resolvedAreaCode, selectedAreaMeta.orElse(null));
        String featureTable = dashboardFeatureTable(resolvedDatasetCode);
        if (featureTable == null) {
            return emptyFeatureCollection();
        }
        String bboxFilter = resolvedBbox == null
                ? ""
                : """
                  AND COALESCE(
                      f.geom,
                      ST_SetSRID(ST_MakePoint(f.longitude::double precision, f.latitude::double precision), 4326)
                  ) && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                  """;
        String areaFilter = resolvedAreaCode == null
                ? ""
                : """
                  AND (
                      f.source_area_code = :areaCode
                      OR f.source_area_code IN (:scopeAreaCodes)
                      OR EXISTS (
                          SELECT 1
                          FROM public.sd_area_code selected_area
                          WHERE selected_area.area_code = :selectedAreaCode
                            AND (
                                (
                                    selected_area.level = 'SIDO'
                                    AND f.source_area_code LIKE selected_area.sido_code || '%%'
                                )
                                OR (
                                    selected_area.level = 'SIGUNGU'
                                    AND f.source_area_code LIKE selected_area.sigungu_code || '%%'
                                )
                            )
                      )
                      OR EXISTS (
                          SELECT 1
                          FROM public.sd_area_boundary b
                          WHERE b.area_code = :selectedAreaCode
                            AND ST_Covers(
                                ST_MakeValid(b.geom),
                                COALESCE(
                                    f.geom,
                                    ST_SetSRID(ST_MakePoint(f.longitude::double precision, f.latitude::double precision), 4326)
                                )
                            )
                      )
                  )
                  """;

        String sql = """
                WITH features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(
                            COALESCE(
                                f.geom,
                                ST_SetSRID(ST_MakePoint(f.longitude::double precision, f.latitude::double precision), 4326)
                            ),
                            6
                        )::jsonb,
                        'properties', jsonb_build_object(
                            'datasetCode', f.dataset_code,
                            'metricCode', f.metric_code,
                            'externalId', f.external_id,
                            'featureName', f.feature_name,
                            'featureCategory', f.feature_category,
                            'sourceAreaCode', f.source_area_code,
                            'sourceAreaName', f.source_area_name,
                            'address', f.address,
                            'roadAddress', f.road_address,
                            'longitude', f.longitude,
                            'latitude', f.latitude,
                            'baseDate', f.base_date,
                            'statusCode', COALESCE(f.status_code, f.raw_payload ->> 'stat'),
                            'statusName', COALESCE(f.status_name, f.raw_payload ->> 'statNm'),
                            'chargerType', f.raw_payload ->> 'chgerType',
                            'output', f.raw_payload ->> 'output',
                            'useTime', f.raw_payload ->> 'useTime',
                            'businessName', f.raw_payload ->> 'busiNm',
                            'businessCall', f.raw_payload ->> 'busiCall',
                            'parkingFree', f.raw_payload ->> 'parkingFree'
                        ) || CASE
                            WHEN f.dataset_code IN (
                                'STANDARD_URBAN_PARK_MAIN',
                                'STANDARD_LIBRARY_MAIN',
                                'STANDARD_AED_MAIN'
                            ) THEN jsonb_build_object(
                            'areaSize', COALESCE(f.raw_payload ->> 'parkArea', f.raw_payload ->> 'areaSize'),
                            'openTime', COALESCE(f.raw_payload ->> 'weekdayOperOpenHhmm', f.raw_payload ->> 'openTime'),
                            'closeTime', COALESCE(f.raw_payload ->> 'weekdayOperColseHhmm', f.raw_payload ->> 'closeTime'),
                            'phoneNumber', COALESCE(f.raw_payload ->> 'phoneNumber', f.raw_payload ->> 'phone'),
                            'facilityType', COALESCE(f.raw_payload ->> 'libraryType', f.raw_payload ->> 'institutionType'),
                            'managerName', COALESCE(f.raw_payload ->> 'institutionNm', f.raw_payload ->> 'managerNm')
                            )
                            ELSE '{}'::jsonb
                        END
                    ) AS feature
                    FROM %s f
                    WHERE f.dataset_code = :datasetCode
                      AND f.longitude IS NOT NULL
                      AND f.latitude IS NOT NULL
                      %s
                      %s
                    ORDER BY f.geo_feature_id DESC
                    LIMIT :limit
                )
                SELECT jsonb_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(jsonb_agg(feature), '[]'::jsonb)
                )::text
                FROM features
                """.formatted(featureTable, bboxFilter, areaFilter);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("datasetCode", resolvedDatasetCode)
                .addValue("limit", resolvedLimit);
        if (resolvedAreaCode != null) {
            params.addValue("areaCode", resolvedAreaCode);
            params.addValue("selectedAreaCode", selectedAreaMeta
                    .map(AreaMeta::areaCode)
                    .orElse(resolvedAreaCode));
            params.addValue("scopeAreaCodes", scopeAreaCodes.isEmpty()
                    ? Set.of(resolvedAreaCode)
                    : scopeAreaCodes);
        }
        if (resolvedBbox != null) {
            params.addValue("minLon", resolvedBbox.minLon());
            params.addValue("minLat", resolvedBbox.minLat());
            params.addValue("maxLon", resolvedBbox.maxLon());
            params.addValue("maxLat", resolvedBbox.maxLat());
        }

        String geoJson = jdbcTemplate.queryForObject(sql, params, String.class);
        return geoJson == null ? EMPTY_FEATURE_COLLECTION : geoJson;
    }

    public DashboardGisRegionStatsResponse getDashboardGisRegionStats(String datasetCode) {
        String resolvedDatasetCode = normalizeRequired(datasetCode, "datasetCode는 필수입니다.");
        String featureTable = dashboardFeatureTable(resolvedDatasetCode);
        if (featureTable != null) {
            return getDashboardPointFeatureRegionStats(resolvedDatasetCode, featureTable);
        }

        if (!EV_CHARGER_DATASET_CODE.equals(resolvedDatasetCode)) {
            return DashboardGisRegionStatsResponse.builder()
                    .datasetCode(resolvedDatasetCode)
                    .totalCount(BigDecimal.ZERO)
                    .items(List.of())
                    .notice("현재 전체 원천 건수 기준 시도별 통계를 제공하지 않는 데이터셋입니다.")
                    .build();
        }

        String sql = """
                WITH latest_rows AS (
                    SELECT
                        o.area_observation_id,
                        o.dataset_code,
                        o.metric_code,
                        o.area_code,
                        o.area_level,
                        o.source_area_code,
                        o.source_area_name,
                        o.base_date,
                        o.observed_at,
                        o.numeric_value,
                        o.dimensions,
                        d.dataset_name,
                        m.metric_name
                    FROM public.sd_dashboard_area_observation o
                    JOIN public.sd_dashboard_dataset d
                        ON d.dataset_code = o.dataset_code
                    JOIN public.sd_dashboard_metric m
                        ON m.dataset_code = o.dataset_code
                       AND m.metric_code = o.metric_code
                    WHERE o.dataset_code = :datasetCode
                      AND o.metric_code = :metricCode
                      AND o.dimensions ->> 'statsType' = 'SIDO_DISTRIBUTION'
                      AND o.base_date = (
                          SELECT MAX(latest.base_date)
                          FROM public.sd_dashboard_area_observation latest
                          WHERE latest.dataset_code = :datasetCode
                            AND latest.metric_code = :metricCode
                            AND latest.dimensions ->> 'statsType' = 'SIDO_DISTRIBUTION'
                      )
                ),
                stats_rows AS (
                    SELECT
                        lr.*,
                        c.name AS area_name,
                        c.full_name AS full_name,
                        CASE
                            WHEN (lr.dimensions ->> 'apiTotalCount') ~ '^[0-9]+(\\.[0-9]+)?$'
                                THEN (lr.dimensions ->> 'apiTotalCount')::numeric
                            ELSE NULL
                        END AS api_total_count,
                        SUM(lr.numeric_value) OVER () AS summed_total_count,
                        MAX(lr.observed_at) OVER () AS collected_at
                    FROM latest_rows lr
                    LEFT JOIN public.sd_area_code c
                        ON c.area_code = lr.area_code
                )
                SELECT
                    dataset_code,
                    dataset_name,
                    metric_code,
                    metric_name,
                    area_code,
                    COALESCE(area_name, source_area_name) AS area_name,
                    COALESCE(full_name, source_area_name) AS full_name,
                    area_level,
                    source_area_code,
                    base_date,
                    collected_at,
                    numeric_value,
                    COALESCE(api_total_count, summed_total_count, 0) AS total_count
                FROM stats_rows
                ORDER BY numeric_value DESC NULLS LAST, area_name
                """;

        List<RegionStatRow> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("datasetCode", resolvedDatasetCode)
                        .addValue("metricCode", EV_CHARGER_COUNT_METRIC_CODE),
                (rs, rowNum) -> mapRegionStatRow(rs));

        if (rows.isEmpty()) {
            return DashboardGisRegionStatsResponse.builder()
                    .datasetCode(resolvedDatasetCode)
                    .metricCode(EV_CHARGER_COUNT_METRIC_CODE)
                    .totalCount(BigDecimal.ZERO)
                    .items(List.of())
                    .notice("아직 전기차 충전소 전체 건수 통계가 수집되지 않았습니다.")
                    .build();
        }

        BigDecimal totalCount = rows.stream()
                .map(RegionStatRow::totalCount)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElseGet(() -> rows.stream()
                        .map(RegionStatRow::count)
                        .filter(value -> value != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        List<DashboardGisRegionStatItem> items = new ArrayList<>();
        for (RegionStatRow row : rows) {
            BigDecimal count = row.count() == null ? BigDecimal.ZERO : row.count();
            BigDecimal percent = totalCount.compareTo(BigDecimal.ZERO) > 0
                    ? count.multiply(BigDecimal.valueOf(100)).divide(totalCount, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            items.add(DashboardGisRegionStatItem.builder()
                    .areaCode(row.areaCode())
                    .areaName(row.areaName())
                    .fullName(row.fullName())
                    .areaLevel(row.areaLevel())
                    .sourceAreaCode(row.sourceAreaCode())
                    .count(count)
                    .percent(percent)
                    .build());
        }

        RegionStatRow first = rows.get(0);
        return DashboardGisRegionStatsResponse.builder()
                .datasetCode(first.datasetCode())
                .datasetName(first.datasetName())
                .metricCode(first.metricCode())
                .metricName(first.metricName())
                .baseDate(first.baseDate())
                .collectedAt(first.collectedAt())
                .totalCount(totalCount)
                .items(items)
                .notice("지도는 현재 범위 최대 500건만 표시하고, 원형 그래프는 원천 API 전체 건수 기준 시도별 비율입니다.")
                .build();
    }

    private DashboardGisRegionStatsResponse getDashboardPointFeatureRegionStats(
            String datasetCode,
            String featureTable) {
        String sql = """
                WITH feature_rows AS (
                    SELECT
                        f.dataset_code,
                        s.area_code,
                        s.area_name,
                        s.full_name,
                        s.sido_code
                    FROM %s f
                    JOIN LATERAL (
                        SELECT
                            c.area_code,
                            c.name AS area_name,
                            c.full_name,
                            c.sido_code
                        FROM public.sd_area_code c
                        WHERE c.level = 'SIDO'
                          AND c.is_active = TRUE
                          AND (
                              f.source_area_code LIKE c.sido_code || '%%'
                              OR f.source_area_name = c.name
                              OR f.source_area_name = c.full_name
                              OR f.source_area_name LIKE c.name || '%%'
                              OR f.source_area_name LIKE c.full_name || '%%'
                              OR c.name = REPLACE(REPLACE(REPLACE(REPLACE(f.source_area_name, '특별자치도', ''), '특별자치시', ''), '광역시', ''), '특별시', '')
                              OR REPLACE(REPLACE(REPLACE(REPLACE(f.source_area_name, '특별자치도', ''), '특별자치시', ''), '광역시', ''), '특별시', '') LIKE c.name || '%%'
                              OR (f.source_area_name = '전라북도' AND c.name = '전북특별자치도')
                              OR (f.source_area_name = '강원도' AND c.name = '강원특별자치도')
                              OR (f.source_area_name LIKE '전라북도%%' AND c.name = '전북특별자치도')
                              OR (f.source_area_name LIKE '강원도%%' AND c.name = '강원특별자치도')
                          )
                        ORDER BY
                            CASE
                                WHEN f.source_area_code LIKE c.sido_code || '%%' THEN 0
                                WHEN f.source_area_name = c.name OR f.source_area_name = c.full_name THEN 1
                                WHEN f.source_area_name LIKE c.name || '%%'
                                  OR f.source_area_name LIKE c.full_name || '%%'
                                  OR (f.source_area_name LIKE '전라북도%%' AND c.name = '전북특별자치도')
                                  OR (f.source_area_name LIKE '강원도%%' AND c.name = '강원특별자치도') THEN 2
                                ELSE 3
                            END,
                            c.area_code
                        LIMIT 1
                    ) s ON TRUE
                    WHERE f.dataset_code = :datasetCode
                      AND f.longitude IS NOT NULL
                      AND f.latitude IS NOT NULL
                ),
                stats_rows AS (
                    SELECT DISTINCT ON (fr.dataset_code, fr.area_code)
                        fr.dataset_code,
                        d.dataset_name,
                        fr.area_code,
                        fr.area_name,
                        fr.full_name,
                        fr.sido_code,
                        COUNT(*) OVER (PARTITION BY fr.dataset_code, fr.area_code)::numeric AS numeric_value,
                        COUNT(*) OVER ()::numeric AS total_count
                    FROM feature_rows fr
                    JOIN public.sd_dashboard_dataset d
                        ON d.dataset_code = fr.dataset_code
                    ORDER BY fr.dataset_code, fr.area_code
                )
                SELECT
                    dataset_code,
                    dataset_name,
                    'FEATURE_COUNT' AS metric_code,
                    '저장 피처 수' AS metric_name,
                    area_code,
                    area_name,
                    full_name,
                    'SIDO' AS area_level,
                    sido_code AS source_area_code,
                    NULL::date AS base_date,
                    NULL::timestamp AS collected_at,
                    numeric_value,
                    total_count
                FROM stats_rows
                ORDER BY numeric_value DESC NULLS LAST, area_name
                """.formatted(featureTable);

        List<RegionStatRow> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("datasetCode", datasetCode),
                (rs, rowNum) -> mapRegionStatRow(rs));

        if (rows.isEmpty()) {
            return DashboardGisRegionStatsResponse.builder()
                    .datasetCode(datasetCode)
                    .metricCode("FEATURE_COUNT")
                    .metricName("저장 피처 수")
                    .totalCount(BigDecimal.ZERO)
                    .items(List.of())
                    .notice("아직 시도별로 집계할 저장 피처가 없습니다.")
                    .build();
        }

        BigDecimal totalCount = rows.stream()
                .map(RegionStatRow::totalCount)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElseGet(() -> rows.stream()
                        .map(RegionStatRow::count)
                        .filter(value -> value != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        List<DashboardGisRegionStatItem> items = new ArrayList<>();
        for (RegionStatRow row : rows) {
            BigDecimal count = row.count() == null ? BigDecimal.ZERO : row.count();
            BigDecimal percent = totalCount.compareTo(BigDecimal.ZERO) > 0
                    ? count.multiply(BigDecimal.valueOf(100)).divide(totalCount, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            items.add(DashboardGisRegionStatItem.builder()
                    .areaCode(row.areaCode())
                    .areaName(row.areaName())
                    .fullName(row.fullName())
                    .areaLevel(row.areaLevel())
                    .sourceAreaCode(row.sourceAreaCode())
                    .count(count)
                    .percent(percent)
                    .build());
        }

        RegionStatRow first = rows.get(0);
        return DashboardGisRegionStatsResponse.builder()
                .datasetCode(first.datasetCode())
                .datasetName(first.datasetName())
                .metricCode(first.metricCode())
                .metricName(first.metricName())
                .totalCount(totalCount)
                .items(items)
                .notice("전체 보기 지도는 저장 피처 전체 건수를 시도 단위로 집계해 표시합니다.")
                .build();
    }

    public Map<String, Object> getDashboardGisObservations(String datasetCode, String areaCode, int limit) {
        String resolvedDatasetCode = normalizeRequired(datasetCode, "datasetCode는 필수입니다.");
        String resolvedAreaCode = resolveOptionalAreaCode(areaCode);
        int resolvedLimit = normalizeObservationLimit(limit);

        List<Map<String, Object>> rows = resolvedAreaCode != null && MOIS_AVERAGE_AGE_DATASET_CODE.equals(resolvedDatasetCode)
                ? findAverageAgeRows(resolvedAreaCode)
                : findDashboardGisObservationRows(
                        resolvedDatasetCode,
                        resolvedAreaCode,
                        resolvedLimit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasetCode", resolvedDatasetCode);
        response.put("areaCode", resolvedAreaCode);
        response.put("areaFiltered", resolvedAreaCode != null);
        response.put("fallbackToAll", false);
        response.put("limit", resolvedLimit);
        response.put("totalRowCount", rows.isEmpty() ? 0 : rows.get(0).get("totalRowCount"));
        response.put("totalNumericValue", rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).get("totalNumericValue"));
        response.put("baseDate", rows.isEmpty() ? null : rows.get(0).get("baseDate"));
        response.put("baseHour", rows.isEmpty() ? null : rows.get(0).get("baseHour"));
        response.put("collectedAt", rows.isEmpty() ? null : rows.get(0).get("collectedAt"));
        response.put("metricName", rows.isEmpty() ? null : rows.get(0).get("metricName"));
        response.put("unit", rows.isEmpty() ? null : rows.get(0).get("unit"));
        response.put("notice", rows.isEmpty() && resolvedAreaCode != null
                ? "선택 지역 관측값이 없습니다."
                : null);
        response.put("items", rows);
        return response;
    }

    private List<Map<String, Object>> findDashboardGisObservationRows(String datasetCode, String areaCode, int limit) {
        Optional<AreaMeta> selectedAreaMeta = areaCode == null
                ? Optional.empty()
                : findOptionalAreaMeta(areaCode);
        Set<String> scopeAreaCodes = areaCode == null
                ? Set.of()
                : dashboardObservationScopeAreaCodes(areaCode, selectedAreaMeta.orElse(null));
        String selectedAreaLabel = AIRKOREA_AIR_QUALITY_DATASET_CODE.equals(datasetCode)
                ? selectedAreaMeta.map(this::displayAreaName).orElse(null)
                : null;
        String metricFilter = KMA_VILAGE_FCST_DATASET_CODE.equals(datasetCode)
                ? "AND o.dimensions ->> 'category' = :kmaTemperatureCategory"
                : "";
        String areaFilter = areaCode == null ? "" : """
                  AND (
                      o.area_code = :areaCode
                      OR o.source_area_code = :areaCode
                      OR (
                          o.dataset_code IN ('KMA_VILAGE_FCST_MAIN', 'AIRKOREA_AIR_QUALITY_MAIN')
                          AND o.area_code IN (:scopeAreaCodes)
                      )
                      OR EXISTS (
                          SELECT 1
                          FROM public.sd_area_code selected_area
                          JOIN public.sd_area_code observation_area
                              ON observation_area.area_code = o.area_code
                          WHERE selected_area.area_code = :areaCode
                            AND selected_area.sido_code = observation_area.sido_code
                            AND (
                                NULLIF(NULLIF(observation_area.sigungu_code, ''), '00000') IS NULL
                                OR selected_area.level = 'SIDO'
                                OR selected_area.sigungu_code = observation_area.sigungu_code
                            )
                            AND (
                                NULLIF(NULLIF(observation_area.eupmyeondong_code, ''), '00000000') IS NULL
                                OR selected_area.level IN ('SIDO', 'SIGUNGU')
                                OR selected_area.eupmyeondong_code = observation_area.eupmyeondong_code
                            )
                      )
                      OR EXISTS (
                          SELECT 1
                          FROM public.sd_area_code selected_area
                          LEFT JOIN public.sd_area_admin_legal_mapping legal_mapping
                              ON legal_mapping.legal_area_code = selected_area.area_code
                             AND legal_mapping.source_code = 'KOSIS_LEGAL_ADMIN_LINK'
                          WHERE selected_area.area_code = :areaCode
                            AND (
                                (
                                    legal_mapping.admin_area_code IS NOT NULL
                                    AND o.source_area_code = legal_mapping.admin_area_code
                                )
                                OR (
                                    selected_area.level = 'SIDO'
                                    AND o.source_area_code LIKE selected_area.sido_code || '%%'
                                )
                                OR (
                                    selected_area.level = 'SIGUNGU'
                                    AND o.source_area_code LIKE selected_area.sigungu_code || '%%'
                                )
                            )
                      )
                  )
                """;
        String sql = """
                WITH scoped_rows AS (
                    SELECT
                        o.area_observation_id,
                        o.dataset_code,
                        o.metric_code,
                        o.area_code,
                        o.area_level,
                        o.source_area_code,
                        o.source_area_name,
                        o.grid_x,
                        o.grid_y,
                        o.base_date,
                        o.base_hour,
                        o.observed_at,
                        o.numeric_value,
                        o.text_value,
                        o.json_value,
                        o.raw_payload,
                        o.unit,
                        o.dimensions,
                        o.created_at,
                        d.dataset_name,
                        m.metric_name,
                        m.unit AS metric_unit,
                        c.name AS area_name,
                        c.full_name AS full_name
                    FROM public.sd_dashboard_area_observation o
                    JOIN public.sd_dashboard_dataset d
                        ON d.dataset_code = o.dataset_code
                    LEFT JOIN public.sd_dashboard_metric m
                        ON m.dataset_code = o.dataset_code
                       AND m.metric_code = o.metric_code
                    LEFT JOIN public.sd_area_code c
                        ON c.area_code = o.area_code
                    WHERE o.dataset_code = :datasetCode
                      AND o.numeric_value IS NOT NULL
                      %s
                      %s
                ),
                latest_key AS (
                    SELECT base_date, COALESCE(base_hour, '') AS base_hour
                    FROM scoped_rows
                    ORDER BY base_date DESC, COALESCE(base_hour, '') DESC, created_at DESC
                    LIMIT 1
                ),
                ranked_rows AS (
                    SELECT
                        sr.*,
                        ROW_NUMBER() OVER (
                            PARTITION BY
                                CASE
                                    WHEN sr.dataset_code = 'AIRKOREA_AIR_QUALITY_MAIN'
                                        THEN COALESCE(NULLIF(sr.source_area_code, ''), NULLIF(sr.area_code, ''), sr.area_observation_id::text)
                                    ELSE '__latest_key__'
                                END
                            ORDER BY
                                COALESCE(sr.observed_at, sr.created_at) DESC,
                                sr.base_date DESC,
                                COALESCE(sr.base_hour, '') DESC,
                                sr.area_observation_id DESC
                        ) AS recency_rank
                    FROM scoped_rows sr
                ),
                latest_rows AS (
                    SELECT
                        sr.*,
                        COALESCE(
                            CASE
                                WHEN sr.dataset_code = 'AIRKOREA_AIR_QUALITY_MAIN'
                                    THEN NULLIF(:selectedAreaLabel, '')
                                ELSE NULL
                            END,
                            CASE
                                WHEN sr.source_area_code LIKE 'KMA:%%'
                                    THEN COALESCE(NULLIF(sr.dimensions ->> 'metricLabel', ''), NULLIF(sr.dimensions ->> 'category', ''))
                                ELSE NULL
                            END,
                            NULLIF(sr.dimensions ->> 'stationName', ''),
                            NULLIF(sr.full_name, ''),
                            NULLIF(sr.area_name, ''),
                            NULLIF(sr.raw_payload ->> 'dongNm', ''),
                            NULLIF(sr.raw_payload ->> 'emdNm', ''),
                            NULLIF(sr.source_area_name, ''),
                            NULLIF(sr.dimensions ->> 'category', ''),
                            NULLIF(CONCAT_WS('/', NULLIF(sr.grid_x, ''), NULLIF(sr.grid_y, '')), ''),
                            NULLIF(REPLACE(REPLACE(sr.source_area_code, 'AIR:', ''), 'KMA:', ''), ''),
                            '미분류'
                        ) AS display_label,
                        COALESCE(
                            CASE sr.dataset_code
                                WHEN 'MOIS_ADMM_HSMB_HH_MAIN' THEN '총 세대수'
                                WHEN 'MOIS_ADMM_AVG_AGE_MAIN' THEN '전체 평균연령'
                                WHEN 'MOIS_ADMM_POP_CHANGE_MAIN' THEN '전체 인구증감'
                                ELSE NULL
                            END,
                            NULLIF(sr.dimensions ->> 'metricLabel', ''),
                            NULLIF(sr.dimensions ->> 'category', ''),
                            NULLIF(sr.metric_name, ''),
                            sr.metric_code
                        ) AS display_metric_name,
                        CASE
                            WHEN sr.dataset_code = 'KMA_VILAGE_FCST_MAIN' THEN NULLIF(sr.unit, '')
                            ELSE COALESCE(NULLIF(sr.unit, ''), NULLIF(sr.metric_unit, ''))
                        END AS display_unit,
                        COUNT(*) OVER () AS total_row_count,
                        COALESCE(SUM(sr.numeric_value) OVER (), 0) AS total_numeric_value,
                        MAX(COALESCE(sr.observed_at, sr.created_at)) OVER () AS collected_at
                    FROM ranked_rows sr
                    LEFT JOIN latest_key lk
                        ON lk.base_date = sr.base_date
                       AND lk.base_hour = COALESCE(sr.base_hour, '')
                    WHERE (
                            sr.dataset_code = 'AIRKOREA_AIR_QUALITY_MAIN'
                            AND sr.recency_rank = 1
                        )
                       OR (
                            sr.dataset_code <> 'AIRKOREA_AIR_QUALITY_MAIN'
                            AND lk.base_date IS NOT NULL
                        )
                )
                SELECT
                    dataset_code,
                    dataset_name,
                    metric_code,
                    display_metric_name AS metric_name,
                    area_code,
                    area_level,
                    source_area_code,
                    display_label AS label,
                    base_date,
                    base_hour,
                    collected_at,
                    numeric_value,
                    display_unit AS unit,
                    total_row_count,
                    total_numeric_value
                FROM latest_rows
                ORDER BY numeric_value DESC NULLS LAST, label
                LIMIT :limit
                """.formatted(metricFilter, areaFilter);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("datasetCode", datasetCode)
                .addValue("limit", limit)
                .addValue("selectedAreaLabel", selectedAreaLabel);
        if (KMA_VILAGE_FCST_DATASET_CODE.equals(datasetCode)) {
            params.addValue("kmaTemperatureCategory", KMA_TEMPERATURE_CATEGORY);
        }
        if (areaCode != null) {
            params.addValue("areaCode", areaCode);
            params.addValue("scopeAreaCodes", scopeAreaCodes);
        }

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapObservationRow(rs));
    }

    private Optional<AreaMeta> findOptionalAreaMeta(String areaCode) {
        try {
            return Optional.of(findAreaMeta(resolveAreaCode(areaCode)));
        } catch (ResponseStatusException ignored) {
            return Optional.empty();
        }
    }

    private String displayAreaName(AreaMeta areaMeta) {
        if (areaMeta.fullName() != null && !areaMeta.fullName().isBlank()) {
            return areaMeta.fullName();
        }
        return areaMeta.name();
    }

    private Set<String> dashboardObservationScopeAreaCodes(String areaCode, AreaMeta knownAreaMeta) {
        LinkedHashSet<String> scopeAreaCodes = new LinkedHashSet<>();
        if (areaCode == null || areaCode.isBlank()) {
            return scopeAreaCodes;
        }
        scopeAreaCodes.add(areaCode);
        try {
            AreaMeta areaMeta = knownAreaMeta != null ? knownAreaMeta : findAreaMeta(resolveAreaCode(areaCode));
            addIfPresent(scopeAreaCodes, areaMeta.areaCode());
            addIfPresent(scopeAreaCodes, areaMeta.sidoCode());
            addIfPresent(scopeAreaCodes, areaMeta.sigunguCode());
            addIfPresent(scopeAreaCodes, areaMeta.eupmyeondongCode());
            addIfPresent(scopeAreaCodes, composeAreaCode(areaMeta.sidoCode(), "00000000"));
            addIfPresent(scopeAreaCodes, composeAreaCode(areaMeta.sigunguCode(), "00000"));
            addIfPresent(scopeAreaCodes, composeAreaCode(areaMeta.eupmyeondongCode(), "00"));
            findParentAreaMeta(areaMeta).ifPresent(parentArea -> addIfPresent(scopeAreaCodes, parentArea.areaCode()));
        } catch (ResponseStatusException ignored) {
            // If an external code cannot be resolved, keep exact-code matching only.
        }
        return scopeAreaCodes;
    }

    private String composeAreaCode(String codePrefix, String suffix) {
        if (codePrefix == null || codePrefix.isBlank()) {
            return null;
        }
        return codePrefix + suffix;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private List<Map<String, Object>> findAverageAgeRows(String areaCode) {
        try {
            AreaMeta areaMeta = findAreaMeta(areaCode);
            AreaPopulationChartResponse population = getAreaPopulation(areaCode, areaMeta.level(), null, "00");
            List<PopulationChartDataset> datasets = population.getDatasets() == null
                    ? List.of()
                    : population.getDatasets();
            List<BigDecimal> maleValues = datasets.size() > 0 && datasets.get(0).getData() != null
                    ? datasets.get(0).getData()
                    : List.of();
            List<BigDecimal> femaleValues = datasets.size() > 1 && datasets.get(1).getData() != null
                    ? datasets.get(1).getData()
                    : List.of();
            List<BigDecimal> totalValues = new ArrayList<>();
            int maxSize = Math.max(maleValues.size(), femaleValues.size());
            for (int index = 0; index < maxSize; index++) {
                totalValues.add(valueAt(maleValues, index).add(valueAt(femaleValues, index)));
            }

            BigDecimal totalAverageAge = weightedAverageAge(totalValues);
            BigDecimal maleAverageAge = weightedAverageAge(maleValues);
            BigDecimal femaleAverageAge = weightedAverageAge(femaleValues);

            if (totalAverageAge == null && maleAverageAge == null && femaleAverageAge == null) {
                return List.of();
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            addAverageAgeRow(rows, population, areaMeta, "AVG_AGE_TOTAL", "전체 평균연령", "전체", totalAverageAge);
            addAverageAgeRow(rows, population, areaMeta, "AVG_AGE_MALE", "남성 평균연령", "남성", maleAverageAge);
            addAverageAgeRow(rows, population, areaMeta, "AVG_AGE_FEMALE", "여성 평균연령", "여성", femaleAverageAge);
            BigDecimal totalNumericValue = rows.stream()
                    .map(row -> (BigDecimal) row.get("value"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            for (Map<String, Object> row : rows) {
                row.put("totalRowCount", rows.size());
                row.put("totalNumericValue", totalNumericValue);
            }
            return rows;
        } catch (ResponseStatusException exception) {
            return List.of();
        }
    }

    private BigDecimal valueAt(List<BigDecimal> values, int index) {
        if (index < 0 || index >= values.size() || values.get(index) == null) {
            return BigDecimal.ZERO;
        }
        return values.get(index);
    }

    private BigDecimal weightedAverageAge(List<BigDecimal> values) {
        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal populationTotal = BigDecimal.ZERO;
        for (int index = 0; index < values.size() && index < POPULATION_AGE_MIDPOINTS.size(); index++) {
            BigDecimal count = values.get(index) == null ? BigDecimal.ZERO : values.get(index);
            weightedTotal = weightedTotal.add(count.multiply(POPULATION_AGE_MIDPOINTS.get(index)));
            populationTotal = populationTotal.add(count);
        }
        if (populationTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return weightedTotal.divide(populationTotal, 1, RoundingMode.HALF_UP);
    }

    private void addAverageAgeRow(
            List<Map<String, Object>> rows,
            AreaPopulationChartResponse population,
            AreaMeta areaMeta,
            String metricCode,
            String metricName,
            String label,
            BigDecimal value) {
        if (value == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("datasetCode", MOIS_AVERAGE_AGE_DATASET_CODE);
        row.put("datasetName", "성별 주민등록 평균연령");
        row.put("metricCode", metricCode);
        row.put("metricName", metricName);
        row.put("areaCode", population.getAreaCode());
        row.put("areaLevel", areaMeta.level());
        row.put("sourceAreaCode", population.getAreaCode());
        row.put("label", label);
        row.put("baseDate", population.getBaseDate());
        row.put("baseHour", population.getHour());
        row.put("collectedAt", null);
        row.put("value", value);
        row.put("unit", "세");
        rows.add(row);
    }

    private String getSidoBoundaries(String sidoCode, Bbox bbox) {
        String cachedGeoJson = getCachedSidoBoundaries(sidoCode, bbox);
        if (!isEmptyFeatureCollection(cachedGeoJson)) {
            return cachedGeoJson;
        }
        return getSidoBoundariesFromSigungu(sidoCode, bbox);
    }

    private String getCachedSidoBoundaries(String sidoCode, Bbox bbox) {
        String sidoFilter = sidoCode == null ? "" : "AND c.sido_code = :sidoCode";
        String sql = """
                WITH features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(
                            ST_SimplifyPreserveTopology(ST_MakeValid(b.geom), 0.005),
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
                    FROM public.sd_area_boundary b
                    JOIN public.sd_area_code c
                        ON c.area_code = b.area_code
                    WHERE c.level = 'SIDO'
                      AND c.is_active = TRUE
                      AND b.boundary_type = 'SIDO'
                      AND ST_Intersects(
                          b.geom,
                          ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                      )
                      %s
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

    private String getSidoBoundariesFromSigungu(String sidoCode, Bbox bbox) {
        String sidoFilter = sidoCode == null ? "" : "AND c.sido_code = :sidoCode";
        String sql = """
                WITH sido_rows AS (
                    SELECT
                        c.area_code,
                        c.sido_code,
                        c.sigungu_code,
                        c.eupmyeondong_code,
                        c.name,
                        c.full_name,
                        c.level,
                        (
                            SELECT ST_SimplifyPreserveTopology(
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
                            )
                            FROM public.sd_area_code child
                            JOIN public.sd_area_boundary b
                                ON b.area_code = child.area_code
                               AND b.boundary_type = 'SIGUNGU'
                            WHERE child.sido_code = c.sido_code
                              AND child.level = 'SIGUNGU'
                              AND child.is_active = TRUE
                              AND ST_Intersects(
                                  b.geom,
                                  ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                              )
                        ) AS geom
                    FROM public.sd_area_code c
                    WHERE c.level = 'SIDO'
                      AND c.is_active = TRUE
                      AND EXISTS (
                          SELECT 1
                          FROM public.sd_area_code child
                          JOIN public.sd_area_boundary b
                              ON b.area_code = child.area_code
                             AND b.boundary_type = 'SIGUNGU'
                          WHERE child.sido_code = c.sido_code
                            AND child.level = 'SIGUNGU'
                            AND child.is_active = TRUE
                            AND ST_Intersects(
                                b.geom,
                                ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                            )
                      )
                      %s
                ),
                features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(c.geom, 5)::jsonb,
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
                    FROM sido_rows c
                    WHERE c.geom IS NOT NULL
                      AND NOT ST_IsEmpty(c.geom)
                    ORDER BY c.sido_code
                )
                SELECT jsonb_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(jsonb_agg(feature), '[]'::jsonb)
                )::text
                FROM features
                """.formatted(sidoFilter, NAVIGATION_PROPERTIES_SQL);

        String geoJson = queryGeoJson(sql, sidoCode, null, bbox);
        return geoJson == null ? EMPTY_FEATURE_COLLECTION : geoJson;
    }

    private boolean isEmptyFeatureCollection(String geoJson) {
        if (geoJson == null || geoJson.isBlank()) {
            return true;
        }
        return geoJson.replaceAll("\\s+", "").contains("\"features\":[]");
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

    private List<String> resolveBoundaryCacheLevels(String levels) {
        if (levels == null || levels.isBlank()) {
            return DEFAULT_BOUNDARY_CACHE_LEVELS;
        }

        List<String> resolvedLevels = new ArrayList<>();
        for (String token : levels.split(",")) {
            String resolvedLevel = token.trim().toUpperCase();
            if (resolvedLevel.isBlank()) {
                continue;
            }
            if (!SUPPORTED_BOUNDARY_LEVELS.contains(resolvedLevel)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "levels는 SIDO, SIGUNGU, EUPMYEONDONG, JIPGYEGU 중 하나 이상이어야 합니다.");
            }
            if (!resolvedLevels.contains(resolvedLevel)) {
                resolvedLevels.add(resolvedLevel);
            }
        }

        return resolvedLevels.isEmpty() ? DEFAULT_BOUNDARY_CACHE_LEVELS : resolvedLevels;
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
                zeroIfNull(population.getMale10To19()),
                zeroIfNull(population.getMale20To29()),
                zeroIfNull(population.getMale30To39()),
                zeroIfNull(population.getMale40To49()),
                zeroIfNull(population.getMale50To59()),
                zeroIfNull(population.getMale60To69()),
                zeroIfNull(population.getMale70To79()),
                zeroIfNull(population.getMale80To89()),
                zeroIfNull(population.getMale90To99()),
                zeroIfNull(population.getMale100Over()));
    }

    private List<BigDecimal> femaleAgeData(AreaPopulationDto population) {
        return List.of(
                zeroIfNull(population.getFemale0To9()),
                zeroIfNull(population.getFemale10To19()),
                zeroIfNull(population.getFemale20To29()),
                zeroIfNull(population.getFemale30To39()),
                zeroIfNull(population.getFemale40To49()),
                zeroIfNull(population.getFemale50To59()),
                zeroIfNull(population.getFemale60To69()),
                zeroIfNull(population.getFemale70To79()),
                zeroIfNull(population.getFemale80To89()),
                zeroIfNull(population.getFemale90To99()),
                zeroIfNull(population.getFemale100Over()));
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
                labeled_rows AS (
                    SELECT
                        %s AS label,
                        f.visitor_count
                    FROM target_rows f
                    LEFT JOIN public.sd_area_code ac
                        ON ac.area_code = f.area_code
                    LEFT JOIN public.sd_area_code sg
                        ON sg.level = 'SIGUNGU'
                       AND sg.sido_code = ac.sido_code
                       AND sg.sigungu_code = ac.sigungu_code
                ),
                distinct_labels AS (
                    SELECT DISTINCT label
                    FROM labeled_rows
                ),
                aggregated AS (
                    SELECT
                        dl.label,
                        (
                            SELECT COALESCE(SUM(lr.visitor_count), 0)::numeric
                            FROM labeled_rows lr
                            WHERE lr.label = dl.label
                        ) AS visitor_count
                    FROM distinct_labels dl
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

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeOptionalUpper(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private Bbox resolveOptionalBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }
        return resolveBbox(bbox);
    }

    private int normalizeFeatureLimit(int limit) {
        if (limit < 1) {
            return 100;
        }
        return Math.min(limit, 1000);
    }

    private int normalizeObservationLimit(int limit) {
        if (limit < 1) {
            return 12;
        }
        return Math.min(limit, 50);
    }

    private String dashboardFeatureTable(String datasetCode) {
        return switch (datasetCode) {
            case "STANDARD_LIBRARY_MAIN" -> "public.sd_dashboard_standard_library_feature";
            case "STANDARD_URBAN_PARK_MAIN" -> "public.sd_dashboard_standard_urban_park_feature";
            case "STANDARD_BUS_STOP_MAIN" -> "public.sd_dashboard_standard_bus_stop_feature";
            default -> null;
        };
    }

    private String emptyFeatureCollection() {
        return """
                {"type":"FeatureCollection","features":[]}
                """;
    }

    private DashboardGisDataSourceResponse mapDashboardGisDataSource(ResultSet rs) throws SQLException {
        return DashboardGisDataSourceResponse.builder()
                .sourceCode(rs.getString("source_code"))
                .sourceName(rs.getString("source_name"))
                .providerName(rs.getString("provider_name"))
                .providerType(rs.getString("provider_type"))
                .sourceCategory(rs.getString("source_category"))
                .officialUrl(rs.getString("official_url"))
                .apiEndpoint(rs.getString("api_endpoint"))
                .apiType(rs.getString("api_type"))
                .dataFormat(rs.getString("data_format"))
                .authType(rs.getString("auth_type"))
                .spatialCoverage(rs.getString("spatial_coverage"))
                .spatialGranularity(rs.getString("spatial_granularity"))
                .temporalGranularity(rs.getString("temporal_granularity"))
                .updateCycle(rs.getString("update_cycle"))
                .coordinateSystem(rs.getString("coordinate_system"))
                .hasGeometry(rs.getBoolean("has_geometry"))
                .hasPointCoordinate(rs.getBoolean("has_point_coordinate"))
                .collectionDifficulty(rs.getString("collection_difficulty"))
                .priority(nullableInt(rs, "priority"))
                .verificationStatus(rs.getString("verification_status"))
                .isActive(rs.getBoolean("is_active"))
                .datasetCount(nullableInt(rs, "dataset_count"))
                .metricCount(nullableInt(rs, "metric_count"))
                .build();
    }

    private DashboardGisDatasetResponse mapDashboardGisDataset(ResultSet rs) throws SQLException {
        return DashboardGisDatasetResponse.builder()
                .sourceCode(rs.getString("source_code"))
                .datasetCode(rs.getString("dataset_code"))
                .datasetName(rs.getString("dataset_name"))
                .dashboardLayerType(rs.getString("dashboard_layer_type"))
                .dashboardMetricHint(rs.getString("dashboard_metric_hint"))
                .defaultGeometryType(rs.getString("default_geometry_type"))
                .defaultAreaLevel(rs.getString("default_area_level"))
                .spatialJoinStrategy(rs.getString("spatial_join_strategy"))
                .collectionPolicy(rs.getString("collection_policy"))
                .displayPriority(nullableInt(rs, "display_priority"))
                .isInitialCandidate(rs.getBoolean("is_initial_candidate"))
                .metricCount(nullableInt(rs, "metric_count"))
                .observationCount(nullableInt(rs, "observation_count"))
                .featureCount(nullableInt(rs, "feature_count"))
                .build();
    }

    private DashboardGisMetricResponse mapDashboardGisMetric(ResultSet rs) throws SQLException {
        return DashboardGisMetricResponse.builder()
                .datasetCode(rs.getString("dataset_code"))
                .metricCode(rs.getString("metric_code"))
                .metricName(rs.getString("metric_name"))
                .valueType(rs.getString("value_type"))
                .unit(rs.getString("unit"))
                .chartGroup(rs.getString("chart_group"))
                .sortOrder(nullableInt(rs, "sort_order"))
                .isDefault(rs.getBoolean("is_default"))
                .build();
    }

    private RegionStatRow mapRegionStatRow(ResultSet rs) throws SQLException {
        return new RegionStatRow(
                rs.getString("dataset_code"),
                rs.getString("dataset_name"),
                rs.getString("metric_code"),
                rs.getString("metric_name"),
                rs.getString("area_code"),
                rs.getString("area_name"),
                rs.getString("full_name"),
                rs.getString("area_level"),
                rs.getString("source_area_code"),
                rs.getObject("base_date", LocalDate.class),
                rs.getObject("collected_at", LocalDateTime.class),
                rs.getBigDecimal("numeric_value"),
                rs.getBigDecimal("total_count"));
    }

    private Map<String, Object> mapObservationRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("datasetCode", rs.getString("dataset_code"));
        row.put("datasetName", rs.getString("dataset_name"));
        row.put("metricCode", rs.getString("metric_code"));
        row.put("metricName", rs.getString("metric_name"));
        row.put("areaCode", rs.getString("area_code"));
        row.put("areaLevel", rs.getString("area_level"));
        row.put("sourceAreaCode", rs.getString("source_area_code"));
        row.put("label", rs.getString("label"));
        row.put("baseDate", rs.getObject("base_date", LocalDate.class));
        row.put("baseHour", rs.getString("base_hour"));
        row.put("collectedAt", rs.getObject("collected_at", LocalDateTime.class));
        row.put("value", rs.getBigDecimal("numeric_value"));
        row.put("unit", rs.getString("unit"));
        row.put("totalRowCount", rs.getLong("total_row_count"));
        row.put("totalNumericValue", rs.getBigDecimal("total_numeric_value"));
        return row;
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
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

    private record RegionStatRow(
            String datasetCode,
            String datasetName,
            String metricCode,
            String metricName,
            String areaCode,
            String areaName,
            String fullName,
            String areaLevel,
            String sourceAreaCode,
            LocalDate baseDate,
            LocalDateTime collectedAt,
            BigDecimal count,
            BigDecimal totalCount) {
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
