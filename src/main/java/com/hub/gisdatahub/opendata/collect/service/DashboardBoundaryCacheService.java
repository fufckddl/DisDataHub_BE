package com.hub.gisdatahub.opendata.collect.service;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardBoundaryCacheService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DashboardBoundaryCacheService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> refreshSidoBoundaryCache() {
        int deletedCount = jdbcTemplate.update(
                "DELETE FROM public.sd_area_boundary WHERE boundary_type = 'SIDO'",
                new MapSqlParameterSource());

        String insertSql = """
                WITH generated AS (
                    SELECT
                        c.area_code,
                        (
                            SELECT ST_Multi(
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
                                )
                            )
                            FROM public.sd_area_code child
                            JOIN public.sd_area_boundary b
                                ON b.area_code = child.area_code
                               AND b.boundary_type = 'SIGUNGU'
                            WHERE child.sido_code = c.sido_code
                              AND child.level = 'SIGUNGU'
                              AND child.is_active = TRUE
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
                      )
                )
                INSERT INTO public.sd_area_boundary (
                    area_code,
                    boundary_type,
                    geom,
                    center,
                    source_name,
                    base_date,
                    created_at,
                    updated_at
                )
                SELECT
                    area_code,
                    'SIDO',
                    geom,
                    ST_PointOnSurface(geom),
                    'SIGUNGU_CACHE',
                    CURRENT_DATE,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM generated
                WHERE geom IS NOT NULL
                  AND NOT ST_IsEmpty(geom)
                """;
        int insertedCount = jdbcTemplate.update(insertSql, new MapSqlParameterSource());
        return Map.of(
                "target", "sd_area_boundary",
                "boundaryType", "SIDO",
                "deletedCount", deletedCount,
                "insertedCount", insertedCount);
    }
}
