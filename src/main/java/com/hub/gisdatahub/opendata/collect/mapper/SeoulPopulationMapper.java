package com.hub.gisdatahub.opendata.collect.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.opendata.collect.dto.seoul.SeoulPopulationRow;

@Mapper
public interface SeoulPopulationMapper {

    // sd_area_population FK 저장 전에 sd_area_code에 존재하는 지역코드인지 확인합니다.
    boolean existsAreaCode(String areaCode);

    // 서울 생활인구 OpenAPI 응답을 지역/출처/기준일/시간 기준으로 저장하거나 갱신합니다.
    void upsert(SeoulPopulationRow row);
}
