package com.hub.gisdatahub.opendata.collect.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CollectAreaCodeMappingXmlTest {

    @Test
    void seoulPopulationMapperResolvesApiCodesAgainstAreaCodeTable() throws IOException {
        String mapperXml = new ClassPathResource("mapper/SeoulPopulationMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapperXml)
                .contains("findAreaCodeByLivingPopulationApiCode")
                .contains("AND c.sigungu_code = #{apiAreaCode}")
                .contains("SUBSTRING(#{apiAreaCode}, 6, 5) = '00000'")
                .contains("AND c.eupmyeondong_code = SUBSTRING(#{apiAreaCode}, 1, 8)");
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
