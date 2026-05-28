package com.hub.gisdatahub.dashboard.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import org.springframework.test.context.TestPropertySource;

import com.hub.gisdatahub.dashboard.dto.AreaPopulationDto;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath:mapper/DashboardPopulationMapper.xml",
        "mybatis.type-aliases-package=com.hub.gisdatahub.dashboard.dto",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
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
        jdbcTemplate.execute("DROP TABLE IF EXISTS public.sd_area_admin_legal_mapping");
        jdbcTemplate.execute("DROP TABLE IF EXISTS public.sd_resident_population");
        jdbcTemplate.execute("DROP TABLE IF EXISTS public.sd_area_code");
        jdbcTemplate.execute("""
                CREATE TABLE public.sd_area_code (
                    area_code VARCHAR(20) PRIMARY KEY,
                    sido_code VARCHAR(10),
                    sigungu_code VARCHAR(10),
                    eupmyeondong_code VARCHAR(20),
                    name VARCHAR(100) NOT NULL,
                    full_name VARCHAR(200) NOT NULL,
                    level VARCHAR(20) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE public.sd_area_admin_legal_mapping (
                    source_code VARCHAR(80) NOT NULL,
                    admin_area_code VARCHAR(10) NOT NULL,
                    legal_area_code VARCHAR(20) NOT NULL,
                    ctpv_name VARCHAR(50),
                    sigungu_name VARCHAR(80),
                    admin_dong_name VARCHAR(100),
                    legal_dong_name VARCHAR(100),
                    region_class_code VARCHAR(30),
                    revised_date DATE,
                    link_no VARCHAR(100),
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE public.sd_resident_population (
                    resident_population_id BIGINT PRIMARY KEY,
                    area_code VARCHAR(20),
                    admm_cd VARCHAR(20) NOT NULL,
                    area_level VARCHAR(30) NOT NULL,
                    api_lv VARCHAR(2),
                    reg_se_cd VARCHAR(1) NOT NULL,
                    stats_ym CHAR(6) NOT NULL,
                    ctpv_nm VARCHAR(80),
                    sgg_nm VARCHAR(80),
                    dong_nm VARCHAR(100),
                    tong VARCHAR(20),
                    ban VARCHAR(20),
                    total_population BIGINT,
                    male_population BIGINT,
                    female_population BIGINT,
                    male_0_9 BIGINT,
                    male_10_19 BIGINT,
                    male_20_29 BIGINT,
                    male_30_39 BIGINT,
                    male_40_49 BIGINT,
                    male_50_59 BIGINT,
                    male_60_69 BIGINT,
                    male_70_79 BIGINT,
                    male_80_89 BIGINT,
                    male_90_99 BIGINT,
                    male_100_over BIGINT,
                    female_0_9 BIGINT,
                    female_10_19 BIGINT,
                    female_20_29 BIGINT,
                    female_30_39 BIGINT,
                    female_40_49 BIGINT,
                    female_50_59 BIGINT,
                    female_60_69 BIGINT,
                    female_70_79 BIGINT,
                    female_80_89 BIGINT,
                    female_90_99 BIGINT,
                    female_100_over BIGINT,
                    metadata VARCHAR(500),
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);

        jdbcTemplate.update(
                """
                INSERT INTO public.sd_area_code
                    (area_code, sido_code, sigungu_code, eupmyeondong_code, name, full_name, level)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "11010", "11", "11010", "11010000", "종로구", "서울특별시 종로구", "SIGUNGU");

        jdbcTemplate.update("""
                INSERT INTO public.sd_resident_population (
                    resident_population_id, area_code, admm_cd, area_level, api_lv, reg_se_cd, stats_ym,
                    ctpv_nm, sgg_nm, dong_nm, tong, ban,
                    total_population, male_population, female_population,
                    male_0_9, male_10_19, male_20_29, male_30_39, male_40_49, male_50_59,
                    male_60_69, male_70_79, male_80_89, male_90_99, male_100_over,
                    female_0_9, female_10_19, female_20_29, female_30_39, female_40_49, female_50_59,
                    female_60_69, female_70_79, female_80_89, female_90_99, female_100_over,
                    metadata, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?
                )
                """,
                1L, "11010", "1101000000", "SIGUNGU", "2", "1", "202605",
                "서울특별시", "종로구", null, null, null,
                300L, 140L, 160L,
                1L, 2L, 3L, 4L, 5L, 6L,
                7L, 8L, 9L, 10L, 11L,
                12L, 13L, 14L, 15L, 16L, 17L,
                18L, 19L, 20L, 21L, 22L,
                "{\"source\":\"test\"}",
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 3, 10)),
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 3, 20)));
    }

    @Test
    void findAreaPopulationMapsResidentPopulationAgeColumnsToDtoFields() {
        AreaPopulationDto result = dashboardPopulationMapper.findAreaPopulation(
                "11010",
                "SIGUNGU",
                LocalDate.of(2026, 5, 26),
                "03");

        assertThat(result).isNotNull();
        assertThat(result.getAreaCode()).isEqualTo("11010");
        assertThat(result.getAreaName()).isEqualTo("종로구");
        assertThat(result.getFullName()).isEqualTo("서울특별시 종로구");
        assertThat(result.getSourceCode()).isEqualTo("MOIS_ADMM_SEXD_AGE_PPLTN");
        assertThat(result.getBaseDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.getHour()).isEqualTo("00");
        assertThat(result.getTotalPopulation()).isEqualByComparingTo("300");
        assertThat(result.getMale0To9()).isEqualByComparingTo("1");
        assertThat(result.getMale10To19()).isEqualByComparingTo("2");
        assertThat(result.getMale100Over()).isEqualByComparingTo("11");
        assertThat(result.getFemale0To9()).isEqualByComparingTo("12");
        assertThat(result.getFemale10To19()).isEqualByComparingTo("13");
        assertThat(result.getFemale100Over()).isEqualByComparingTo("22");
        assertThat(result.getMetadata()).isEqualTo("{\"source\":\"test\"}");
    }

    @Test
    void findAreaPopulationUsesAdministrativeLegalMappingWhenDongPopulationHasNoLegalAreaCode() {
        jdbcTemplate.update(
                """
                INSERT INTO public.sd_area_code
                    (area_code, sido_code, sigungu_code, eupmyeondong_code, name, full_name, level)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "4117310100", "41", "41173", "41173101", "비산동", "경기도 안양시 동안구 비산동", "EUPMYEONDONG");

        jdbcTemplate.update("""
                INSERT INTO public.sd_area_admin_legal_mapping (
                    source_code, admin_area_code, legal_area_code,
                    ctpv_name, sigungu_name, admin_dong_name, legal_dong_name,
                    region_class_code, revised_date, link_no, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "KOSIS_LEGAL_ADMIN_LINK", "4117354000", "4117310100",
                "경기도", "안양시 동안구", "부흥동", "비산동",
                "31042540", LocalDate.of(2025, 4, 1), "1647",
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 3, 10)),
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 3, 20)));

        jdbcTemplate.update("""
                INSERT INTO public.sd_resident_population (
                    resident_population_id, area_code, admm_cd, area_level, api_lv, reg_se_cd, stats_ym,
                    ctpv_nm, sgg_nm, dong_nm, tong, ban,
                    total_population, male_population, female_population,
                    male_0_9, male_10_19, male_20_29, male_30_39, male_40_49, male_50_59,
                    male_60_69, male_70_79, male_80_89, male_90_99, male_100_over,
                    female_0_9, female_10_19, female_20_29, female_30_39, female_40_49, female_50_59,
                    female_60_69, female_70_79, female_80_89, female_90_99, female_100_over,
                    metadata, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?
                )
                """,
                2L, null, "4117354000", "EUPMYEONDONG", "3", "1", "202605",
                "경기도", "안양시 동안구", "부흥동", null, null,
                123L, 60L, 63L,
                1L, 2L, 3L, 4L, 5L, 6L,
                7L, 8L, 9L, 10L, 11L,
                12L, 13L, 14L, 15L, 16L, 17L,
                18L, 19L, 20L, 21L, 22L,
                "{\"source\":\"mapping\"}",
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 3, 10)),
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 3, 20)));

        AreaPopulationDto result = dashboardPopulationMapper.findAreaPopulation(
                "4117310100",
                "EUPMYEONDONG",
                LocalDate.of(2026, 5, 26),
                "03");

        assertThat(result).isNotNull();
        assertThat(result.getAreaCode()).isEqualTo("4117310100");
        assertThat(result.getAreaName()).isEqualTo("비산동");
        assertThat(result.getTotalPopulation()).isEqualByComparingTo("123");
        assertThat(result.getMetadata()).isEqualTo("{\"source\":\"mapping\"}");
    }

    @SuppressWarnings("unused")
    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
