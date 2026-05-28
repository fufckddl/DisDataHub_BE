package com.hub.gisdatahub.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.hub.gisdatahub.dashboard.dto.AreaNavigationResponse;
import com.hub.gisdatahub.dashboard.dto.AreaPopulationChartResponse;
import com.hub.gisdatahub.dashboard.dto.AreaPopulationDto;
import com.hub.gisdatahub.dashboard.mapper.DashboardPopulationMapper;

@ExtendWith(MockitoExtension.class)
class DashboardBoundaryServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private DashboardPopulationMapper populationMapper;

    private DashboardBoundaryService service;

    @BeforeEach
    void setUp() {
        service = new DashboardBoundaryService(jdbcTemplate, populationMapper);
    }



    @Test
    void getAreaBoundariesIncludesNavigationPropertiesForPolygonBackButton() {
        when(jdbcTemplate.queryForObject(
                any(String.class),
                any(MapSqlParameterSource.class),
                any(Class.class)))
                .thenReturn("{\"type\":\"FeatureCollection\",\"features\":[]}");

        String response = service.getAreaBoundaries("SIGUNGU", null, "41", null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), any(Class.class));

        assertThat(response).contains("FeatureCollection");
        assertThat(sqlCaptor.getValue())
                .contains("'parentAreaCode'")
                .contains("'parentName'")
                .contains("'parentFullName'")
                .contains("'parentLevel'")
                .contains("'childLevel'")
                .contains("'canDrillDown'")
                .contains("p.area_code = :parentAreaCode")
                .contains("p.level = 'SIDO'");
        assertThat(paramsCaptor.getValue().getValue("parentAreaCode")).isEqualTo("41");
        verifyNoInteractions(populationMapper);
    }

    @Test
    void getAreaNavigationReturnsParentAndChildLevelsForSigunguBackButton() {
        stubAreaNavigationQueries(
                areaMeta("41550", "41", "41550", "41550000", "안성시", "경기도 안성시", "SIGUNGU"),
                areaMeta("41", "41", null, null, "경기도", "경기도", "SIDO"));

        AreaNavigationResponse response = service.getAreaNavigation(" 41550 ");

        assertThat(response.getAreaCode()).isEqualTo("41550");
        assertThat(response.getAreaName()).isEqualTo("안성시");
        assertThat(response.getAreaLevel()).isEqualTo("SIGUNGU");
        assertThat(response.getParentAreaCode()).isEqualTo("41");
        assertThat(response.getParentAreaName()).isEqualTo("경기도");
        assertThat(response.getParentLevel()).isEqualTo("SIDO");
        assertThat(response.getChildLevel()).isEqualTo("EUPMYEONDONG");
        assertThat(response.isCanDrillDown()).isTrue();
    }

    @Test
    void getAreaNavigationReturnsPreviousSigunguForEupmyeondongBackButton() {
        stubAreaNavigationQueries(
                areaMeta("41550310", "41", "41550", "41550310", "공도읍", "경기도 안성시 공도읍", "EUPMYEONDONG"),
                areaMeta("41550", "41", "41550", "41550000", "안성시", "경기도 안성시", "SIGUNGU"));

        AreaNavigationResponse response = service.getAreaNavigation("41550310");

        assertThat(response.getAreaCode()).isEqualTo("41550310");
        assertThat(response.getAreaLevel()).isEqualTo("EUPMYEONDONG");
        assertThat(response.getParentAreaCode()).isEqualTo("41550");
        assertThat(response.getParentAreaName()).isEqualTo("안성시");
        assertThat(response.getParentLevel()).isEqualTo("SIGUNGU");
        assertThat(response.getChildLevel()).isEqualTo("JIPGYEGU");
        assertThat(response.isCanDrillDown()).isTrue();
    }

    @Test
    void getAreaNavigationDisablesDrillDownAtJipgyeguAndKeepsParentForBackButton() {
        stubAreaNavigationQueries(
                areaMeta("4155031020001", "41", "41550", "41550310", "집계구", "경기도 안성시 공도읍 집계구", "JIPGYEGU"),
                areaMeta("41550310", "41", "41550", "41550310", "공도읍", "경기도 안성시 공도읍", "EUPMYEONDONG"));

        AreaNavigationResponse response = service.getAreaNavigation("4155031020001");

        assertThat(response.getAreaCode()).isEqualTo("4155031020001");
        assertThat(response.getAreaLevel()).isEqualTo("JIPGYEGU");
        assertThat(response.getParentAreaCode()).isEqualTo("41550310");
        assertThat(response.getParentLevel()).isEqualTo("EUPMYEONDONG");
        assertThat(response.getChildLevel()).isNull();
        assertThat(response.isCanDrillDown()).isFalse();
    }

    @Test
    void getAreaPopulationBuildsChartDatasetsFromMappedAgeBuckets() {
        LocalDate baseDate = LocalDate.of(2026, 5, 20);
        AreaPopulationDto population = AreaPopulationDto.builder()
                .areaCode("11110")
                .areaName("종로구")
                .fullName("서울특별시 종로구")
                .baseDate(baseDate)
                .hour("03")
                .totalPopulation(new BigDecimal("1000.5"))
                .malePopulation(new BigDecimal("490.5"))
                .femalePopulation(new BigDecimal("510.0"))
                .male0To9(new BigDecimal("1.1"))
                .male10To14(new BigDecimal("2.2"))
                .male15To19(new BigDecimal("3.3"))
                .male20To24(new BigDecimal("4.4"))
                .male25To29(new BigDecimal("5.5"))
                .male30To34(new BigDecimal("6.6"))
                .male35To39(new BigDecimal("7.7"))
                .male40To44(new BigDecimal("8.8"))
                .male45To49(new BigDecimal("9.9"))
                .male50To54(new BigDecimal("10.1"))
                .male55To59(new BigDecimal("11.1"))
                .male60To64(new BigDecimal("12.1"))
                .male65To69(new BigDecimal("13.1"))
                .male70To74(new BigDecimal("14.1"))
                .female0To9(new BigDecimal("15.1"))
                .female10To14(new BigDecimal("16.1"))
                .female15To19(new BigDecimal("17.1"))
                .female20To24(new BigDecimal("18.1"))
                .female25To29(new BigDecimal("19.1"))
                .female30To34(new BigDecimal("20.1"))
                .female35To39(new BigDecimal("21.1"))
                .female40To44(new BigDecimal("22.1"))
                .female45To49(new BigDecimal("23.1"))
                .female50To54(new BigDecimal("24.1"))
                .female55To59(new BigDecimal("25.1"))
                .female60To64(new BigDecimal("26.1"))
                .female65To69(new BigDecimal("27.1"))
                .female70To74(new BigDecimal("28.1"))
                .build();

        when(populationMapper.findAreaPopulation("11110", "SIGUNGU", baseDate, "03"))
                .thenReturn(population);

        AreaPopulationChartResponse response = service.getAreaPopulation(" 11110 ", "2026-05-20", "3");

        verify(populationMapper).findAreaPopulation("11110", "SIGUNGU", baseDate, "03");
        assertThat(response.getLabels()).containsExactly(
                "0-9", "10-14", "15-19", "20-24", "25-29", "30-34", "35-39",
                "40-44", "45-49", "50-54", "55-59", "60-64", "65-69", "70-74");
        assertThat(response.getDatasets()).hasSize(2);
        assertThat(response.getDatasets().get(0).getLabel()).isEqualTo("남성");
        assertThat(response.getDatasets().get(0).getData()).containsExactlyElementsOf(List.of(
                new BigDecimal("1.1"), new BigDecimal("2.2"), new BigDecimal("3.3"),
                new BigDecimal("4.4"), new BigDecimal("5.5"), new BigDecimal("6.6"),
                new BigDecimal("7.7"), new BigDecimal("8.8"), new BigDecimal("9.9"),
                new BigDecimal("10.1"), new BigDecimal("11.1"), new BigDecimal("12.1"),
                new BigDecimal("13.1"), new BigDecimal("14.1")));
        assertThat(response.getDatasets().get(1).getLabel()).isEqualTo("여성");
        assertThat(response.getDatasets().get(1).getData()).containsExactlyElementsOf(List.of(
                new BigDecimal("15.1"), new BigDecimal("16.1"), new BigDecimal("17.1"),
                new BigDecimal("18.1"), new BigDecimal("19.1"), new BigDecimal("20.1"),
                new BigDecimal("21.1"), new BigDecimal("22.1"), new BigDecimal("23.1"),
                new BigDecimal("24.1"), new BigDecimal("25.1"), new BigDecimal("26.1"),
                new BigDecimal("27.1"), new BigDecimal("28.1")));
    }

    @Test
    void getAreaPopulationUsesZeroForMissingAgeBuckets() {
        AreaPopulationDto population = AreaPopulationDto.builder()
                .areaCode("11110")
                .male0To9(new BigDecimal("1"))
                .female70To74(new BigDecimal("2"))
                .build();

        when(populationMapper.findAreaPopulation("11110", "SIGUNGU", null, null)).thenReturn(population);

        AreaPopulationChartResponse response = service.getAreaPopulation("11110", null, null);

        assertThat(response.getDatasets().get(0).getData())
                .containsExactly(
                        new BigDecimal("1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(response.getDatasets().get(1).getData())
                .containsExactly(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("2"));
    }

    @Test
    void getAreaPopulationAcceptsJipgyeguLevel() {
        AreaPopulationDto population = AreaPopulationDto.builder()
                .areaCode("1101053010001")
                .areaName("집계구")
                .fullName("서울특별시 종로구 사직동 집계구")
                .build();

        when(populationMapper.findAreaPopulation("1101053010001", "JIPGYEGU", null, null))
                .thenReturn(population);

        AreaPopulationChartResponse response = service.getAreaPopulation(
                "1101053010001",
                "JIPGYEGU",
                null,
                null);

        verify(populationMapper).findAreaPopulation("1101053010001", "JIPGYEGU", null, null);
        assertThat(response.getAreaCode()).isEqualTo("1101053010001");
        assertThat(response.getFullName()).isEqualTo("서울특별시 종로구 사직동 집계구");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubAreaNavigationQueries(Map<String, Object> areaRow, Map<String, Object> parentRow) {
        when(jdbcTemplate.query(
                any(String.class),
                any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenAnswer(invocation -> mapSingleRow(invocation.getArgument(2), areaRow))
                .thenAnswer(invocation -> mapSingleRow(invocation.getArgument(2), parentRow));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<?> mapSingleRow(RowMapper mapper, Map<String, Object> row) throws Exception {
        java.sql.ResultSet resultSet = mock(java.sql.ResultSet.class);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            when(resultSet.getString(entry.getKey())).thenReturn((String) entry.getValue());
        }
        return List.of(mapper.mapRow(resultSet, 0));
    }

    private static Map<String, Object> areaMeta(
            String areaCode,
            String sidoCode,
            String sigunguCode,
            String eupmyeondongCode,
            String name,
            String fullName,
            String level) {
        return Map.of(
                "area_code", areaCode,
                "sido_code", sidoCode,
                "sigungu_code", sigunguCode == null ? "" : sigunguCode,
                "eupmyeondong_code", eupmyeondongCode == null ? "" : eupmyeondongCode,
                "name", name,
                "full_name", fullName,
                "level", level);
    }

}
