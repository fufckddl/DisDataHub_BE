package com.hub.gisdatahub.dashboard.mapper;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.dashboard.dto.AreaPopulationDto;

@Mapper
public interface DashboardPopulationMapper {

    // 지도에서 선택한 지역의 최신 또는 지정 기준 생활인구 데이터를 조회합니다.
    AreaPopulationDto findAreaPopulation(
            @Param("areaCode") String areaCode,
            @Param("baseDate") LocalDate baseDate,
            @Param("hour") String hour);
}
