package com.hub.gisdatahub.dashboard.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DashboardPopulationMapperXmlTest {

    @Test
    void findAreaPopulationAliasesAgeColumnsToDtoFieldNames() throws IOException {
        String mapperXml = new ClassPathResource("mapper/DashboardPopulationMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapperXml)
                .contains("p.male_0_9 AS male0To9")
                .contains("p.male_10_14 AS male10To14")
                .contains("p.male_15_19 AS male15To19")
                .contains("p.male_20_24 AS male20To24")
                .contains("p.male_25_29 AS male25To29")
                .contains("p.male_30_34 AS male30To34")
                .contains("p.male_35_39 AS male35To39")
                .contains("p.male_40_44 AS male40To44")
                .contains("p.male_45_49 AS male45To49")
                .contains("p.male_50_54 AS male50To54")
                .contains("p.male_55_59 AS male55To59")
                .contains("p.male_60_64 AS male60To64")
                .contains("p.male_65_69 AS male65To69")
                .contains("p.male_70_74 AS male70To74")
                .contains("p.female_0_9 AS female0To9")
                .contains("p.female_10_14 AS female10To14")
                .contains("p.female_15_19 AS female15To19")
                .contains("p.female_20_24 AS female20To24")
                .contains("p.female_25_29 AS female25To29")
                .contains("p.female_30_34 AS female30To34")
                .contains("p.female_35_39 AS female35To39")
                .contains("p.female_40_44 AS female40To44")
                .contains("p.female_45_49 AS female45To49")
                .contains("p.female_50_54 AS female50To54")
                .contains("p.female_55_59 AS female55To59")
                .contains("p.female_60_64 AS female60To64")
                .contains("p.female_65_69 AS female65To69")
                .contains("p.female_70_74 AS female70To74");
    }
}
