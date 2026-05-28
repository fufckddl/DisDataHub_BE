package com.hub.gisdatahub.opendata.collect.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CollectAreaCodeMappingXmlTest {

    @Test
    void moisPopulationMapperResolvesApiCodesAgainstAreaCodeTable() throws IOException {
        String mapperXml = new ClassPathResource("mapper/MoisResidentPopulationMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapperXml)
                .contains("findAreaCodeByAdmmCd")
                .contains("findAreaCodeByAdmmCodeAndSourceNames")
                .contains("findSigunguAdmmCodes")
                .contains("findEupmyeondongAdmmCodes")
                .contains("AND c.sido_code = #{admmCd}")
                .contains("AND c.sigungu_code = #{admmCd}")
                .contains("SUBSTRING(#{admmCd}, 6, 5) = '00000'")
                .contains("AND c.eupmyeondong_code = SUBSTRING(#{admmCd}, 1, 8)")
                .contains("c.sigungu_code = SUBSTRING(#{admmCd}, 1, 5)")
                .contains("REGEXP_REPLACE(REPLACE(#{dongNm}, '제', ''), '[0-9]+동$', '동')");
    }

    @Test
    void sdotMapperFallsBackFromAliasTableToAreaCodeNames() throws IOException {
        String mapperXml = new ClassPathResource("mapper/SdotVisitorMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapperXml)
                .contains("FROM public.sd_area_name_alias")
                .contains("FROM public.sd_area_code dong")
                .contains("sigungu.name = #{sourceSigunguName}")
                .contains("dong.name = #{sourceEupmyeondongName}")
                .contains("FROM public.sd_area_code sigungu");
    }
}
