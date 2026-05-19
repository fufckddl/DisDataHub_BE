package com.hub.gisdatahub.seoul.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.seoul.dto.DistrictLivingPopulationResponseDto;
import com.hub.gisdatahub.seoul.dto.RealtimeCityAirResponseDto;
import com.hub.gisdatahub.seoul.exception.SeoulOpenApiException;

class SeoulOpenDataParserTest {

    private final SeoulOpenDataParser parser = new SeoulOpenDataParser(new ObjectMapper());

    @Test
    void parseAirQuality_mapsRows() {
        String json = """
                {
                  "RealtimeCityAir": {
                    "list_total_count": 1,
                    "RESULT": { "CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다." },
                    "row": [
                      {
                        "MSRMT_DT": "202605181500",
                        "SAREA_NM": "도심권",
                        "MSRSTN_NM": "중구",
                        "PM": 34.0,
                        "FPM": 18.0,
                        "CAI_GRD": "보통"
                      }
                    ]
                  }
                }
                """;

        RealtimeCityAirResponseDto response = parser.parseAirQuality(json);

        assertThat(response.getRealtimeCityAir().getRow()).hasSize(1);
        assertThat(response.getRealtimeCityAir().getRow().getFirst().getMSRSTN_NM()).isEqualTo("중구");
    }

    @Test
    void parseAirQuality_throwsOnApiError() {
        String json = """
                {
                  "RESULT": {
                    "CODE": "ERROR-500",
                    "MESSAGE": "서버 오류"
                  }
                }
                """;

        assertThatThrownBy(() -> parser.parseAirQuality(json))
                .isInstanceOf(SeoulOpenApiException.class)
                .hasMessageContaining("ERROR-500");
    }

    @Test
    void parseDistrictLivingPopulation_mapsServiceBody() {
        String json = """
                {
                  "SPOP_LOCAL_RESD_PPLTN": {
                    "list_total_count": 1,
                    "RESULT": { "CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다." },
                    "row": [
                      {
                        "STDR_DE_ID": "20240601",
                        "SIGNGU_NM": "종로구",
                        "TOT_LVPOPUL": "12345"
                      }
                    ]
                  }
                }
                """;

        DistrictLivingPopulationResponseDto response =
                parser.parseDistrictLivingPopulation(json, "SPOP_LOCAL_RESD_PPLTN");

        assertThat(response.getServiceRootKey()).isEqualTo("SPOP_LOCAL_RESD_PPLTN");
        assertThat(response.getBody().getRow()).hasSize(1);
        assertThat(response.getBody().getRow().getFirst().getSIGNGU_NM()).isEqualTo("종로구");
    }

}
