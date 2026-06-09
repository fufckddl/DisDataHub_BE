package com.hub.gisdatahub.board.service;

import com.hub.gisdatahub.board.dto.RegionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegionServiceImpl implements RegionService {

    private final JdbcTemplate jdbcTemplate;

    public RegionServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RegionResponse> getSidoList() {
        String sql = """
                SELECT code, name
                FROM region_code
                WHERE depth = 1
                  AND use_yn = 'Y'
                ORDER BY code
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new RegionResponse(
                        rs.getString("code"),
                        rs.getString("name")
                )
        );
    }

    @Override
    public List<RegionResponse> getSigunguList(String sidoCode) {
        String sql = """
                SELECT code, name
                FROM region_code
                WHERE parent_code = ?
                  AND depth = 2
                  AND use_yn = 'Y'
                ORDER BY code
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new RegionResponse(
                        rs.getString("code"),
                        rs.getString("name")
                ),
                sidoCode
        );
    }

    @Override
    public List<RegionResponse> getEupmyeondongList(String sigunguCode) {
        String sql = """
                SELECT code, name
                FROM region_code
                WHERE parent_code = ?
                  AND depth = 3
                  AND use_yn = 'Y'
                ORDER BY code
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new RegionResponse(
                        rs.getString("code"),
                        rs.getString("name")
                ),
                sigunguCode
        );
    }
}