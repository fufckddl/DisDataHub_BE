package com.hub.gisdatahub.opendata.collect.service;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardGisObservationSyncService {

    private static final String DATASET_CODE = "MOIS_ADMM_SEXD_AGE_PPLTN_MAIN";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DashboardGisObservationSyncService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> syncResidentPopulation(String statsYm, String regSeCd) {
        String resolvedRegSeCd = resolveRegSeCd(regSeCd);
        String resolvedStatsYm = resolveStatsYm(statsYm, resolvedRegSeCd);
        ensureDashboardMetricsExist();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("datasetCode", DATASET_CODE)
                .addValue("statsYm", resolvedStatsYm)
                .addValue("regSeCd", resolvedRegSeCd);

        int deletedCount = jdbcTemplate.update("""
                DELETE FROM public.sd_dashboard_area_observation
                WHERE dataset_code = :datasetCode
                  AND metric_code IN ('METRIC_001', 'METRIC_002', 'METRIC_003')
                  AND (
                      dimensions ->> 'statsYm' = :statsYm
                      OR dimensions ->> 'sourceStatsYm' = :statsYm
                  )
                  AND (
                      dimensions ->> 'regSeCd' = :regSeCd
                      OR dimensions ->> 'sourceRegSeCd' = :regSeCd
                  )
                """, params);

        int insertedCount = jdbcTemplate.update("""
                INSERT INTO public.sd_dashboard_area_observation (
                    dataset_code,
                    metric_code,
                    area_code,
                    area_level,
                    source_area_code,
                    source_area_name,
                    base_date,
                    base_hour,
                    numeric_value,
                    unit,
                    dimensions,
                    raw_payload,
                    created_at,
                    updated_at
                )
                SELECT
                    :datasetCode,
                    metric.metric_code,
                    p.area_code,
                    p.area_level,
                    p.admm_cd,
                    NULLIF(TRIM(CONCAT_WS(' ', p.ctpv_nm, p.sgg_nm, p.dong_nm, p.tong, p.ban)), ''),
                    TO_DATE(p.stats_ym || '01', 'YYYYMMDD'),
                    '00',
                    metric.numeric_value,
                    '명',
                    jsonb_build_object(
                        'source', 'MOIS_ADMM_SEXD_AGE_PPLTN',
                        'statsYm', p.stats_ym,
                        'regSeCd', p.reg_se_cd,
                        'areaLevel', p.area_level,
                        'apiLv', p.api_lv,
                        'metric', metric.metric_name
                    ),
                    jsonb_build_object(
                        'residentPopulationId', p.resident_population_id,
                        'admmCd', p.admm_cd,
                        'ctpvNm', p.ctpv_nm,
                        'sggNm', p.sgg_nm,
                        'dongNm', p.dong_nm,
                        'tong', p.tong,
                        'ban', p.ban
                    ),
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM public.sd_resident_population p
                CROSS JOIN LATERAL (
                    VALUES
                        ('METRIC_001', 'totalPopulation', p.total_population),
                        ('METRIC_002', 'malePopulation', p.male_population),
                        ('METRIC_003', 'femalePopulation', p.female_population)
                ) AS metric(metric_code, metric_name, numeric_value)
                WHERE p.stats_ym = :statsYm
                  AND p.reg_se_cd = :regSeCd
                  AND p.api_lv IN ('1', '2', '3')
                  AND metric.numeric_value IS NOT NULL
                """, params);

        return Map.of(
                "target", "sd_dashboard_area_observation",
                "datasetCode", DATASET_CODE,
                "statsYm", resolvedStatsYm,
                "regSeCd", resolvedRegSeCd,
                "deletedCount", deletedCount,
                "insertedCount", insertedCount);
    }

    private String resolveStatsYm(String statsYm, String regSeCd) {
        if (statsYm != null && !statsYm.isBlank()) {
            String normalized = statsYm.replace("-", "").trim();
            if (!normalized.matches("\\d{6}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "statsYm은 YYYYMM 형식이어야 합니다.");
            }
            return normalized;
        }

        String latestStatsYm = jdbcTemplate.queryForObject("""
                SELECT MAX(stats_ym)
                FROM public.sd_resident_population
                WHERE reg_se_cd = :regSeCd
                """, new MapSqlParameterSource("regSeCd", regSeCd), String.class);
        if (latestStatsYm == null || latestStatsYm.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "동기화할 주민등록 인구 데이터가 없습니다.");
        }
        return latestStatsYm;
    }

    private String resolveRegSeCd(String regSeCd) {
        if (regSeCd == null || regSeCd.isBlank()) {
            return "1";
        }
        String normalized = regSeCd.trim();
        if (!normalized.matches("[1-4]")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regSeCd는 1, 2, 3, 4 중 하나여야 합니다.");
        }
        return normalized;
    }

    private void ensureDashboardMetricsExist() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)::int
                FROM public.sd_dashboard_metric
                WHERE dataset_code = :datasetCode
                  AND metric_code IN ('METRIC_001', 'METRIC_002', 'METRIC_003')
                """, new MapSqlParameterSource("datasetCode", DATASET_CODE), Integer.class);
        if (count == null || count < 3) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "MOIS 주민등록 인구 대시보드 지표가 없습니다. 먼저 catalog seed를 실행해야 합니다.");
        }
    }
}
