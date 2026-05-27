package com.hub.gisdatahub.dashboard.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hub.gisdatahub.dashboard.dto.AreaPopulationDto;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DashboardPopulationMapperTest {

    private final DashboardPopulationMapper dashboardPopulationMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    DashboardPopulationMapperTest(
            DashboardPopulationMapper dashboardPopulationMapper,
            DataSource dataSource) {
        this.dashboardPopulationMapper = dashboardPopulationMapper;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS public.sd_area_population");
        jdbcTemplate.execute("DROP TABLE IF EXISTS public.sd_area_code");
        jdbcTemplate.execute("""
                CREATE TABLE public.sd_area_code (
                    area_code VARCHAR(20) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    full_name VARCHAR(200) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE public.sd_area_population (
                    population_id BIGINT PRIMARY KEY,
                    area_code VARCHAR(20) NOT NULL,
                    source_code VARCHAR(20),
                    base_date DATE NOT NULL,
                    hour VARCHAR(2) NOT NULL,
                    total_population DECIMAL(12, 2),
                    male_population DECIMAL(12, 2),
                    female_population DECIMAL(12, 2),
                    male_0_9 DECIMAL(12, 2),
                    male_10_14 DECIMAL(12, 2),
                    male_15_19 DECIMAL(12, 2),
                    male_20_24 DECIMAL(12, 2),
                    male_25_29 DECIMAL(12, 2),
                    male_30_34 DECIMAL(12, 2),
                    male_35_39 DECIMAL(12, 2),
                    male_40_44 DECIMAL(12, 2),
                    male_45_49 DECIMAL(12, 2),
                    male_50_54 DECIMAL(12, 2),
                    male_55_59 DECIMAL(12, 2),
                    male_60_64 DECIMAL(12, 2),
                    male_65_69 DECIMAL(12, 2),
                    male_70_74 DECIMAL(12, 2),
                    female_0_9 DECIMAL(12, 2),
                    female_10_14 DECIMAL(12, 2),
                    female_15_19 DECIMAL(12, 2),
                    female_20_24 DECIMAL(12, 2),
                    female_25_29 DECIMAL(12, 2),
                    female_30_34 DECIMAL(12, 2),
                    female_35_39 DECIMAL(12, 2),
                    female_40_44 DECIMAL(12, 2),
                    female_45_49 DECIMAL(12, 2),
                    female_50_54 DECIMAL(12, 2),
                    female_55_59 DECIMAL(12, 2),
                    female_60_64 DECIMAL(12, 2),
                    female_65_69 DECIMAL(12, 2),
                    female_70_74 DECIMAL(12, 2),
                    metadata VARCHAR(500),
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);

        jdbcTemplate.update(
                "INSERT INTO public.sd_area_code (area_code, name, full_name) VALUES (?, ?, ?)",
                "11010", "종로구", "서울특별시 종로구");

        jdbcTemplate.update("""
                INSERT INTO public.sd_area_population (
                    population_id, area_code, source_code, base_date, hour,
                    total_population, male_population, female_population,
                    male_0_9, male_10_14, male_15_19, male_20_24, male_25_29, male_30_34, male_35_39,
                    male_40_44, male_45_49, male_50_54, male_55_59, male_60_64, male_65_69, male_70_74,
                    female_0_9, female_10_14, female_15_19, female_20_24, female_25_29, female_30_34, female_35_39,
                    female_40_44, female_45_49, female_50_54, female_55_59, female_60_64, female_65_69, female_70_74,
                    metadata, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?
                )
                """,
                1L, "11010", "SPOP", Date.valueOf(LocalDate.of(2026, 5, 26)), "03",
                bd("300.00"), bd("140.00"), bd("160.00"),
                bd("1.10"), bd("2.10"), bd("3.10"), bd("4.10"), bd("5.10"), bd("6.10"), bd("7.10"),
                bd("8.10"), bd("9.10"), bd("10.10"), bd("11.10"), bd("12.10"), bd("13.10"), bd("14.10"),
                bd("1.20"), bd("2.20"), bd("3.20"), bd("4.20"), bd("5.20"), bd("6.20"), bd("7.20"),
                bd("8.20"), bd("9.20"), bd("10.20"), bd("11.20"), bd("12.20"), bd("13.20"), bd("14.20"),
                "{\"source\":\"test\"}",
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 3, 10)),
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 3, 20)));
    }

    @Test
    void findAreaPopulationMapsNumberedAgeColumnsToDtoFields() {
        AreaPopulationDto result = dashboardPopulationMapper.findAreaPopulation(
                "11010",
                LocalDate.of(2026, 5, 26),
                "03");

        assertThat(result).isNotNull();
        assertThat(result.getAreaCode()).isEqualTo("11010");
        assertThat(result.getAreaName()).isEqualTo("종로구");
        assertThat(result.getFullName()).isEqualTo("서울특별시 종로구");
        assertThat(result.getTotalPopulation()).isEqualByComparingTo("300.00");
        assertThat(result.getMale0To9()).isEqualByComparingTo("1.10");
        assertThat(result.getMale10To14()).isEqualByComparingTo("2.10");
        assertThat(result.getMale70To74()).isEqualByComparingTo("14.10");
        assertThat(result.getFemale0To9()).isEqualByComparingTo("1.20");
        assertThat(result.getFemale10To14()).isEqualByComparingTo("2.20");
        assertThat(result.getFemale70To74()).isEqualByComparingTo("14.20");
        assertThat(result.getMetadata()).isEqualTo("{\"source\":\"test\"}");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
