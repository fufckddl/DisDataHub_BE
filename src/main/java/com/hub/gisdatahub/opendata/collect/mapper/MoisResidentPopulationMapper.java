package com.hub.gisdatahub.opendata.collect.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.opendata.collect.dto.mois.MoisResidentPopulationRow;

@Mapper
public interface MoisResidentPopulationMapper {

    List<String> findSidoAdmmCodes();

    List<String> findSigunguAdmmCodes();

    List<String> findEupmyeondongAdmmCodes();

    String findAreaCodeByAdmmCd(@Param("admmCd") String admmCd);

    String findAreaCodeByAdmmCodeAndSourceNames(
            @Param("admmCd") String admmCd,
            @Param("ctpvNm") String ctpvNm,
            @Param("sggNm") String sggNm,
            @Param("dongNm") String dongNm,
            @Param("areaLevel") String areaLevel);

    String findAreaCodeBySourceNames(
            @Param("ctpvNm") String ctpvNm,
            @Param("sggNm") String sggNm,
            @Param("dongNm") String dongNm,
            @Param("areaLevel") String areaLevel);

    void upsert(MoisResidentPopulationRow row);

    int upsertTotal(@Param("statsYm") String statsYm, @Param("regSeCd") String regSeCd);
}
