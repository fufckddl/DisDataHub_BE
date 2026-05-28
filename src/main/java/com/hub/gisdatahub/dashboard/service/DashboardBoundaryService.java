package com.hub.gisdatahub.dashboard.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.dashboard.dto.AreaPopulationChartResponse;
import com.hub.gisdatahub.dashboard.dto.AreaPopulationDto;
import com.hub.gisdatahub.dashboard.dto.PopulationChartDataset;
import com.hub.gisdatahub.dashboard.mapper.DashboardPopulationMapper;

@Service
public class DashboardBoundaryService {

    private static final String EMPTY_FEATURE_COLLECTION = """
            {"type":"FeatureCollection","features":[]}
            """;
    private static final Bbox DEFAULT_SEOUL_BBOX = new Bbox(126.75, 37.42, 127.20, 37.72);
    private static final double MAX_SIGUNGU_BBOX_AREA = 25.0;
    private static final double MAX_EUPMYEONDONG_BBOX_AREA = 2.0;
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

    public AreaPopulationChartResponse getAreaPopulation(String areaCode, String date, String hour) {
        String resolvedAreaCode = resolveAreaCode(areaCode);
        LocalDate resolvedDate = resolveDate(date);
        String resolvedHour = resolveHour(hour);

        AreaPopulationDto population = populationMapper.findAreaPopulation(
                resolvedAreaCode,
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

    private String getSigunguBoundaries(String sidoCode, Bbox bbox) {
        String sidoFilter = sidoCode == null ? "" : "AND c.sido_code = :sidoCode";
        String sql = """
                WITH features AS (
                    SELECT jsonb_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(ST_SimplifyPreserveTopology(ST_MakeValid(b.geom), 0.001), 5)::jsonb,
                        'properties', jsonb_build_object(
                            'areaCode', c.area_code,
                            'sigunguCode', c.sigungu_code,
                            'name', c.name,
                            'fullName', c.full_name,
                            'level', c.level
                        )
                    ) AS feature
                    FROM public.sd_area_boundary b
                    JOIN public.sd_area_code c
                        ON c.area_code = b.area_code
                    WHERE c.level = 'SIGUNGU'
                      AND b.boundary_type = 'SIGUNGU'
                      AND ST_Intersects(
                          b.geom,
                          ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
                      )
                      %s
                    ORDER BY c.sigungu_code
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
                      AND b.boundary_type = 'EUPMYEONDONG'
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

    private String resolveAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "areaCode는 필수입니다.");
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
