package com.hub.gisdatahub.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    void getAreaNavigationReturnsParentAndChildContractForSigunguBackButton() throws SQLException {
        whenAreaMetaQueriesReturn(
                Map.of(
                        "area_code", "4155000000",
                        "sido_code", "41",
                        "sigungu_code", "41550",
                        "eupmyeondong_code", "41550000",
                        "name", "안성시",
                        "full_name", "경기도 안성시",
                        "level", "SIGUNGU"),
                Map.of(
                        "area_code", "4100000000",
                        "sido_code", "41",
                        "sigungu_code", "41000",
                        "eupmyeondong_code", "41000000",
                        "name", "경기도",
                        "full_name", "경기도",
                        "level", "SIDO"));

        AreaNavigationResponse response = service.getAreaNavigation(" 41550 ");

        assertThat(response.getAreaCode()).isEqualTo("4155000000");
        assertThat(response.getAreaName()).isEqualTo("안성시");
        assertThat(response.getAreaLevel()).isEqualTo("SIGUNGU");
        assertThat(response.getParentAreaCode()).isEqualTo("4100000000");
        assertThat(response.getParentAreaName()).isEqualTo("경기도");
        assertThat(response.getParentLevel()).isEqualTo("SIDO");
        assertThat(response.getChildLevel()).isEqualTo("EUPMYEONDONG");
        assertThat(response.isCanDrillDown()).isTrue();
    }

    @Test
    void getAreaNavigationDisablesDrillDownAtJipgyeguAndReturnsEupmyeondongParent() throws SQLException {
        whenAreaMetaQueriesReturn(
                Map.of(
                        "area_code", "4155031021001",
                        "sido_code", "41",
                        "sigungu_code", "41550",
                        "eupmyeondong_code", "41550310",
                        "name", "집계구",
                        "full_name", "경기도 안성시 공도읍 집계구",
                        "level", "JIPGYEGU"),
                Map.of(
                        "area_code", "4155031000",
                        "sido_code", "41",
                        "sigungu_code", "41550",
                        "eupmyeondong_code", "41550310",
                        "name", "공도읍",
                        "full_name", "경기도 안성시 공도읍",
                        "level", "EUPMYEONDONG"));

        AreaNavigationResponse response = service.getAreaNavigation("4155031021001");

        assertThat(response.getAreaCode()).isEqualTo("4155031021001");
        assertThat(response.getAreaLevel()).isEqualTo("JIPGYEGU");
        assertThat(response.getParentAreaCode()).isEqualTo("4155031000");
        assertThat(response.getParentAreaName()).isEqualTo("공도읍");
        assertThat(response.getParentLevel()).isEqualTo("EUPMYEONDONG");
        assertThat(response.getChildLevel()).isNull();
        assertThat(response.isCanDrillDown()).isFalse();
    }

    @Test
    void getAreaBoundariesIncludesNavigationPropertiesForDrillDownAndBackButton() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("{\"type\":\"FeatureCollection\",\"features\":[]}");

        String response = service.getAreaBoundaries(
                "SIGUNGU",
                "41",
                "4100000000",
                "126,36,128,38");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(String.class));

        assertThat(response).contains("FeatureCollection");
        assertThat(sqlCaptor.getValue())
                .contains("'parentAreaCode'")
                .contains("'parentAreaName'")
                .contains("'parentLevel'")
                .contains("'childLevel'")
                .contains("'canDrillDown'")
                .contains("p.level = 'SIDO'");
        assertThat(paramsCaptor.getValue().getValue("sidoCode")).isEqualTo("41");
        assertThat(paramsCaptor.getValue().getValue("parentAreaCode")).isEqualTo("4100000000");
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
                .male10To19(new BigDecimal("2.2"))
                .male20To29(new BigDecimal("3.3"))
                .male30To39(new BigDecimal("4.4"))
                .male40To49(new BigDecimal("5.5"))
                .male50To59(new BigDecimal("6.6"))
                .male60To69(new BigDecimal("7.7"))
                .male70To79(new BigDecimal("8.8"))
                .male80To89(new BigDecimal("9.9"))
                .male90To99(new BigDecimal("10.1"))
                .male100Over(new BigDecimal("11.1"))
                .female0To9(new BigDecimal("12.1"))
                .female10To19(new BigDecimal("13.1"))
                .female20To29(new BigDecimal("14.1"))
                .female30To39(new BigDecimal("15.1"))
                .female40To49(new BigDecimal("16.1"))
                .female50To59(new BigDecimal("17.1"))
                .female60To69(new BigDecimal("18.1"))
                .female70To79(new BigDecimal("19.1"))
                .female80To89(new BigDecimal("20.1"))
                .female90To99(new BigDecimal("21.1"))
                .female100Over(new BigDecimal("22.1"))
                .build();

        when(populationMapper.findAreaPopulation("11110", "SIGUNGU", baseDate, "03"))
                .thenReturn(population);

        AreaPopulationChartResponse response = service.getAreaPopulation(" 11110 ", "2026-05-20", "3");

        verify(populationMapper).findAreaPopulation("11110", "SIGUNGU", baseDate, "03");
        assertThat(response.getLabels()).containsExactly(
                "0-9", "10-19", "20-29", "30-39", "40-49", "50-59",
                "60-69", "70-79", "80-89", "90-99", "100+");
        assertThat(response.getDatasets()).hasSize(2);
        assertThat(response.getDatasets().get(0).getLabel()).isEqualTo("남성");
        assertThat(response.getDatasets().get(0).getData()).containsExactlyElementsOf(List.of(
                new BigDecimal("1.1"), new BigDecimal("2.2"), new BigDecimal("3.3"),
                new BigDecimal("4.4"), new BigDecimal("5.5"), new BigDecimal("6.6"),
                new BigDecimal("7.7"), new BigDecimal("8.8"), new BigDecimal("9.9"),
                new BigDecimal("10.1"), new BigDecimal("11.1")));
        assertThat(response.getDatasets().get(1).getLabel()).isEqualTo("여성");
        assertThat(response.getDatasets().get(1).getData()).containsExactlyElementsOf(List.of(
                new BigDecimal("12.1"), new BigDecimal("13.1"), new BigDecimal("14.1"),
                new BigDecimal("15.1"), new BigDecimal("16.1"), new BigDecimal("17.1"),
                new BigDecimal("18.1"), new BigDecimal("19.1"), new BigDecimal("20.1"),
                new BigDecimal("21.1"), new BigDecimal("22.1")));
    }

    @Test
    void getAreaPopulationUsesZeroForMissingAgeBuckets() {
        AreaPopulationDto population = AreaPopulationDto.builder()
                .areaCode("11110")
                .male0To9(new BigDecimal("1"))
                .female100Over(new BigDecimal("2"))
                .build();

        when(populationMapper.findAreaPopulation("11110", "SIGUNGU", null, null)).thenReturn(population);

        AreaPopulationChartResponse response = service.getAreaPopulation("11110", null, null);

        assertThat(response.getDatasets().get(0).getData())
                .containsExactly(
                        new BigDecimal("1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(response.getDatasets().get(1).getData())
                .containsExactly(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2"));
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

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void getDashboardGisObservationsFiltersKmaWeatherToTemperature() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.getDashboardGisObservations("KMA_VILAGE_FCST_MAIN", null, 10);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        assertThat(sqlCaptor.getValue()).contains("o.dimensions ->> 'category' = :kmaTemperatureCategory");
        assertThat(paramsCaptor.getValue().getValue("kmaTemperatureCategory")).isEqualTo("T1H");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void getDashboardGisObservationsUsesSelectedAreaLabelForAirKorea() throws SQLException {
        whenAreaMetaQueriesReturn(
                Map.of(
                        "area_code", "4420000000",
                        "sido_code", "44",
                        "sigungu_code", "44200",
                        "eupmyeondong_code", "44200000",
                        "name", "아산시",
                        "full_name", "충청남도 아산시",
                        "level", "SIGUNGU"),
                Map.of(
                        "area_code", "4400000000",
                        "sido_code", "44",
                        "sigungu_code", "44000",
                        "eupmyeondong_code", "44000000",
                        "name", "충청남도",
                        "full_name", "충청남도",
                        "level", "SIDO"));

        service.getDashboardGisObservations("AIRKOREA_AIR_QUALITY_MAIN", "4420000000", 10);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(3)).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        assertThat(sqlCaptor.getAllValues().get(2)).contains("NULLIF(:selectedAreaLabel, '')");
        assertThat(paramsCaptor.getAllValues().get(2).getValue("selectedAreaLabel")).isEqualTo("충청남도 아산시");
    }

    @Test
    void getDashboardGisFeaturesIncludesStandardPointDatasetProperties() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("{\"type\":\"FeatureCollection\",\"features\":[]}");

        service.getDashboardGisFeatures("STANDARD_LIBRARY_MAIN", null, null, 100);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(String.class));

        assertThat(sqlCaptor.getValue())
                .contains("'openTime'")
                .contains("'closeTime'")
                .contains("'phoneNumber'")
                .contains("'facilityType'")
                .contains("'managerName'");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void getDashboardGisFeaturesMatchesSelectedRegionBySourceAreaCodeHierarchy() throws SQLException {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper rowMapper = invocation.getArgument(2);
                    return List.of(rowMapper.mapRow(resultSet(Map.of(
                            "area_code", "5013000000",
                            "sido_code", "50",
                            "sigungu_code", "50130",
                            "eupmyeondong_code", "50130000",
                            "name", "서귀포시",
                            "full_name", "제주특별자치도 서귀포시",
                            "level", "SIGUNGU")), 0));
                });
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("{\"type\":\"FeatureCollection\",\"features\":[]}");

        service.getDashboardGisFeatures("STANDARD_BUS_STOP_MAIN", null, "5013000000", 100);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(String.class));

        assertThat(sqlCaptor.getValue())
                .contains("f.source_area_code IN (:scopeAreaCodes)")
                .contains("selected_area.level = 'SIGUNGU'")
                .contains("f.source_area_code LIKE selected_area.sigungu_code")
                .contains("b.area_code = :selectedAreaCode");
        assertThat(paramsCaptor.getValue().getValue("selectedAreaCode")).isEqualTo("5013000000");
        @SuppressWarnings("unchecked")
        Iterable<String> scopeAreaCodes = (Iterable<String>) paramsCaptor.getValue().getValue("scopeAreaCodes");
        assertThat(scopeAreaCodes)
                .contains("5013000000", "50", "50130");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void whenAreaMetaQueriesReturn(
            Map<String, String> areaRow,
            Map<String, String> parentRow) throws SQLException {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper rowMapper = invocation.getArgument(2);
                    if (sql.contains("matched_area")) {
                        return List.of(rowMapper.mapRow(resultSet(areaRow), 0));
                    }
                    if (sql.contains("FROM public.sd_area_code p")) {
                        return parentRow == null
                                ? List.of()
                                : List.of(rowMapper.mapRow(resultSet(parentRow), 0));
                    }
                    return List.of();
                });
    }

    private ResultSet resultSet(Map<String, String> row) throws SQLException {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getString("area_code")).thenReturn(row.get("area_code"));
        when(resultSet.getString("sido_code")).thenReturn(row.get("sido_code"));
        when(resultSet.getString("sigungu_code")).thenReturn(row.get("sigungu_code"));
        when(resultSet.getString("eupmyeondong_code")).thenReturn(row.get("eupmyeondong_code"));
        when(resultSet.getString("name")).thenReturn(row.get("name"));
        when(resultSet.getString("full_name")).thenReturn(row.get("full_name"));
        when(resultSet.getString("level")).thenReturn(row.get("level"));
        return resultSet;
    }

}
