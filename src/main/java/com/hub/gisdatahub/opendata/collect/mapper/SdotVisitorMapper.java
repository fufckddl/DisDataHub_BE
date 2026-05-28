package com.hub.gisdatahub.opendata.collect.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.opendata.collect.dto.seoul.SdotVisitorRow;

@Mapper
public interface SdotVisitorMapper {

    String findAreaCodeBySourceNames(
            @Param("sourceCode") String sourceCode,
            @Param("sourceSigunguName") String sourceSigunguName,
            @Param("sourceEupmyeondongName") String sourceEupmyeondongName);

    void upsert(SdotVisitorRow row);
}
