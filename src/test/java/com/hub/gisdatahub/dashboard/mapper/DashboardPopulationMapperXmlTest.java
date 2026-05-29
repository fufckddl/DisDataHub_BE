package com.hub.gisdatahub.dashboard.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DashboardPopulationMapperXmlTest {

    @Test
    void findAreaPopulationReadsResidentPopulationAndAliasesAgeColumnsToDtoFieldNames() throws IOException {
        String mapperXml = new ClassPathResource("mapper/DashboardPopulationMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapperXml)
                .contains("FROM public.sd_resident_population")
                .contains("JOIN public.sd_area_admin_legal_mapping")
                .contains("m.source_code = 'KOSIS_LEGAL_ADMIN_LINK'")
                .contains("p.admm_cd = m.admin_area_code")
                .contains("MIN(p.resident_population_id) AS population_id")
                .contains("'MOIS_ADMM_SEXD_AGE_PPLTN' AS source_code")
                .contains("p.reg_se_cd = '1'")
                .contains("SUM(p.male_0_9) AS male0To9")
                .contains("SUM(p.male_10_19) AS male10To19")
                .contains("SUM(p.male_20_29) AS male20To29")
                .contains("SUM(p.male_30_39) AS male30To39")
                .contains("SUM(p.male_40_49) AS male40To49")
                .contains("SUM(p.male_50_59) AS male50To59")
                .contains("SUM(p.male_60_69) AS male60To69")
                .contains("SUM(p.male_70_79) AS male70To79")
                .contains("SUM(p.male_80_89) AS male80To89")
                .contains("SUM(p.male_90_99) AS male90To99")
                .contains("SUM(p.male_100_over) AS male100Over")
                .contains("SUM(p.female_0_9) AS female0To9")
                .contains("SUM(p.female_10_19) AS female10To19")
                .contains("SUM(p.female_20_29) AS female20To29")
                .contains("SUM(p.female_30_39) AS female30To39")
                .contains("SUM(p.female_40_49) AS female40To49")
                .contains("SUM(p.female_50_59) AS female50To59")
                .contains("SUM(p.female_60_69) AS female60To69")
                .contains("SUM(p.female_70_79) AS female70To79")
                .contains("SUM(p.female_80_89) AS female80To89")
                .contains("SUM(p.female_90_99) AS female90To99")
                .contains("SUM(p.female_100_over) AS female100Over");
    }

    @Test
    void findAreaPopulationFallsBackFromJipgyeguToParentAreas() throws IOException {
        String mapperXml = new ClassPathResource("mapper/DashboardPopulationMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapperXml)
                .contains("WHERE s.selected_level = 'JIPGYEGU'")
                .contains("AND c.level = 'EUPMYEONDONG'")
                .contains("AND c.eupmyeondong_code = s.eupmyeondong_code")
                .contains("'TONG_BAN' AS population_area_level")
                .contains("AND c.level = 'SIGUNGU'");
    }

    @Test
    void findAreaPopulationPrefersExactAreaCodeLevelOverBroaderRequestedLevel() throws IOException {
        String mapperXml = new ClassPathResource("mapper/DashboardPopulationMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapperXml)
                .contains("WHEN match_priority = 0")
                .contains("WHEN 'EUPMYEONDONG' THEN 3")
                .contains("WHEN 'JIPGYEGU' THEN 4")
                .contains("THEN level")
                .contains("ELSE #{areaLevel}");
    }

    @Test
    void findAreaPopulationAcceptsTwoFiveEightAndTenDigitAreaCodes() throws IOException {
        String mapperXml = new ClassPathResource("mapper/DashboardPopulationMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapperXml)
                .contains("AND c.sido_code = #{areaCode}")
                .contains("SUBSTRING(#{areaCode}, 3, 8) = '00000000'")
                .contains("AND c.sigungu_code = #{areaCode}")
                .contains("SUBSTRING(#{areaCode}, 6, 5) = '00000'")
                .contains("AND c.sigungu_code = SUBSTRING(#{areaCode}, 1, 5)")
                .contains("AND c.eupmyeondong_code = SUBSTRING(#{areaCode}, 1, 8)")
                .contains("s.sigungu_code || '00000'")
                .contains("s.eupmyeondong_code || '00'");
    }
}
