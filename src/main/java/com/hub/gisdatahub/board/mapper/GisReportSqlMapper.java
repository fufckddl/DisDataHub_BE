package com.hub.gisdatahub.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.board.dto.GisReportDetailDto;

@Mapper
public interface GisReportSqlMapper {
    public List<GisReportDetailDto> findGisReportList();
}
