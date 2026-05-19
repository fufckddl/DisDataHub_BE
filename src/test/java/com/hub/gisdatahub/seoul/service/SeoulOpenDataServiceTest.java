package com.hub.gisdatahub.seoul.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hub.gisdatahub.seoul.client.SeoulOpenApiClient;
import com.hub.gisdatahub.seoul.config.SeoulOpenApiProperties;
import com.hub.gisdatahub.seoul.dto.DistrictLivingPopulationResponseDto;
import com.hub.gisdatahub.seoul.dto.RealtimeCityAirResponseDto;
import com.hub.gisdatahub.seoul.parser.SeoulOpenDataParser;

@ExtendWith(MockitoExtension.class)
class SeoulOpenDataServiceTest {

    @Mock
    private SeoulOpenApiClient seoulOpenApiClient;

    @Mock
    private SeoulOpenDataParser seoulOpenDataParser;

    @Mock
    private SeoulOpenApiProperties properties;

    @InjectMocks
    private SeoulOpenDataService seoulOpenDataService;

    @Test
    void getRealtimeCityAir_usesCommonClientAndParser() {
        when(properties.getAirQualityStart()).thenReturn(1);
        when(properties.getAirQualityEnd()).thenReturn(25);
        when(seoulOpenApiClient.callSeoulOpenApi("RealtimeCityAir", 1, 25)).thenReturn("{\"raw\":true}");

        RealtimeCityAirResponseDto expected = new RealtimeCityAirResponseDto();
        when(seoulOpenDataParser.parseAirQuality("{\"raw\":true}")).thenReturn(expected);

        RealtimeCityAirResponseDto actual = seoulOpenDataService.getRealtimeCityAir();

        assertThat(actual).isSameAs(expected);
        verify(seoulOpenApiClient).callSeoulOpenApi("RealtimeCityAir", 1, 25);
        verify(seoulOpenDataParser).parseAirQuality("{\"raw\":true}");
    }

    @Test
    void getDistrictLivingPopulation_passesBaseDatePathParam() {
        when(properties.getDistrictLivingPopulationServiceName()).thenReturn("SPOP_LOCAL_RESD_PPLTN");
        when(properties.getDistrictLivingPopulationStart()).thenReturn(1);
        when(properties.getDistrictLivingPopulationEnd()).thenReturn(1000);
        when(seoulOpenApiClient.callSeoulOpenApi("SPOP_LOCAL_RESD_PPLTN", 1, 1000, "20240601"))
                .thenReturn("{\"raw\":true}");

        DistrictLivingPopulationResponseDto expected = new DistrictLivingPopulationResponseDto();
        when(seoulOpenDataParser.parseDistrictLivingPopulation("{\"raw\":true}", "SPOP_LOCAL_RESD_PPLTN"))
                .thenReturn(expected);

        DistrictLivingPopulationResponseDto actual =
                seoulOpenDataService.getDistrictLivingPopulation("20240601");

        assertThat(actual).isSameAs(expected);
        verify(seoulOpenApiClient).callSeoulOpenApi("SPOP_LOCAL_RESD_PPLTN", 1, 1000, "20240601");
    }

}
