package com.hub.gisdatahub.opendata.collect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.exception.DataCollectException;
import com.hub.gisdatahub.opendata.collect.client.DataCollectClient;
import com.hub.gisdatahub.opendata.collect.dto.mois.MoisResidentPopulationRow;
import com.hub.gisdatahub.opendata.collect.mapper.MoisResidentPopulationMapper;
import com.hub.gisdatahub.opendata.collect.mapper.SdotVisitorMapper;

@ExtendWith(MockitoExtension.class)
class DataCollectServiceTest {

    @Mock
    private DataCollectException dataCollectException;

    @Mock
    private DataCollectClient dataCollectClient;

    @Mock
    private MoisResidentPopulationMapper moisResidentPopulationMapper;

    @Mock
    private SdotVisitorMapper sdotVisitorMapper;

    private DataCollectService service;

    @BeforeEach
    void setUp() {
        service = new DataCollectService(
                dataCollectException,
                dataCollectClient,
                moisResidentPopulationMapper,
                sdotVisitorMapper,
                new ObjectMapper());
    }

    @Test
    void collectResidentPopulationParsesMoisWrappedRowFormat() {
        when(dataCollectClient.callMoisResidentPopulation(
                eq("0000000000"),
                eq("202604"),
                eq("202604"),
                eq("1"),
                eq("1"),
                eq(1),
                eq(100)))
                .thenReturn("""
                        {
                          "admmSexdAgePpltn": [
                            {"head": [{"totalCount": 1}, {"RESULT": {"CODE": "INFO-000"}}]},
                            {"row": [{
                              "admmCd": "4100000000",
                              "statsYm": "202604",
                              "ctpvNm": "경기도",
                              "totNmprCnt": "100",
                              "maleNmprCnt": "49",
                              "femlNmprCnt": "51",
                              "male0AgeNmprCnt": "1",
                              "male10AgeNmprCnt": "2",
                              "male20AgeNmprCnt": "3",
                              "male30AgeNmprCnt": "4",
                              "male40AgeNmprCnt": "5",
                              "male50AgeNmprCnt": "6",
                              "male60AgeNmprCnt": "7",
                              "male70AgeNmprCnt": "8",
                              "male80AgeNmprCnt": "9",
                              "male90AgeNmprCnt": "10",
                              "male100AgeNmprCnt": "11",
                              "feml0AgeNmprCnt": "12",
                              "feml10AgeNmprCnt": "13",
                              "feml20AgeNmprCnt": "14",
                              "feml30AgeNmprCnt": "15",
                              "feml40AgeNmprCnt": "16",
                              "feml50AgeNmprCnt": "17",
                              "feml60AgeNmprCnt": "18",
                              "feml70AgeNmprCnt": "19",
                              "feml80AgeNmprCnt": "20",
                              "feml90AgeNmprCnt": "21",
                              "feml100AgeNmprCnt": "22"
                            }]}
                          ]
                        }
                        """);
        when(moisResidentPopulationMapper.findAreaCodeByAdmmCd("4100000000"))
                .thenReturn("4100000000");

        int savedCount = service.collectResidentPopulation("202604", "1", "1");

        assertThat(savedCount).isEqualTo(1);
        ArgumentCaptor<MoisResidentPopulationRow> rowCaptor = ArgumentCaptor.forClass(MoisResidentPopulationRow.class);
        verify(moisResidentPopulationMapper).upsert(rowCaptor.capture());
        MoisResidentPopulationRow row = rowCaptor.getValue();
        assertThat(row.areaCode()).isEqualTo("4100000000");
        assertThat(row.admmCd()).isEqualTo("4100000000");
        assertThat(row.areaLevel()).isEqualTo("SIDO");
        assertThat(row.apiLv()).isEqualTo("1");
        assertThat(row.regSeCd()).isEqualTo("1");
        assertThat(row.statsYm()).isEqualTo("202604");
        assertThat(row.ctpvNm()).isEqualTo("경기도");
        assertThat(row.totalPopulation()).isEqualTo(100L);
        assertThat(row.male10To19()).isEqualTo(2L);
        assertThat(row.female100Over()).isEqualTo(22L);
    }

    @Test
    void collectResidentPopulationParsesActualMoisResponseFormat() {
        when(dataCollectClient.callMoisResidentPopulation(
                eq("0000000000"),
                eq("202604"),
                eq("202604"),
                eq("1"),
                eq("1"),
                eq(1),
                eq(100)))
                .thenReturn("""
                        {
                          "Response": {
                            "head": {
                              "pageNo": "1",
                              "resultCode": "0",
                              "totalCount": "1",
                              "numOfRows": "100",
                              "resultMsg": "NORMAL_SERVICE"
                            },
                            "items": {"item": [{
                              "tong": "",
                              "ban": "",
                              "totNmprCnt": "9298673",
                              "maleNmprCnt": "4477462",
                              "femlNmprCnt": "4821211",
                              "ctpvNm": "서울특별시",
                              "sggNm": "",
                              "dongNm": "",
                              "admmCd": "1100000000",
                              "statsYm": "202604",
                              "male0AgeNmprCnt": "234006",
                              "male10AgeNmprCnt": "366300",
                              "male20AgeNmprCnt": "578643",
                              "male30AgeNmprCnt": "732780",
                              "male40AgeNmprCnt": "670811",
                              "male50AgeNmprCnt": "719694",
                              "male60AgeNmprCnt": "625746",
                              "male70AgeNmprCnt": "380725",
                              "male80AgeNmprCnt": "151718",
                              "male90AgeNmprCnt": "16725",
                              "male100AgeNmprCnt": "314",
                              "feml0AgeNmprCnt": "221642",
                              "feml10AgeNmprCnt": "352727",
                              "feml20AgeNmprCnt": "660879",
                              "feml30AgeNmprCnt": "734638",
                              "feml40AgeNmprCnt": "686523",
                              "feml50AgeNmprCnt": "744592",
                              "feml60AgeNmprCnt": "692876",
                              "feml70AgeNmprCnt": "464930",
                              "feml80AgeNmprCnt": "219971",
                              "feml90AgeNmprCnt": "41290",
                              "feml100AgeNmprCnt": "1143"
                            }]}
                          }
                        }
                        """);
        when(moisResidentPopulationMapper.findAreaCodeByAdmmCd("1100000000"))
                .thenReturn("1100000000");

        int savedCount = service.collectResidentPopulation("202604", "1", "1");

        assertThat(savedCount).isEqualTo(1);
        ArgumentCaptor<MoisResidentPopulationRow> rowCaptor = ArgumentCaptor.forClass(MoisResidentPopulationRow.class);
        verify(moisResidentPopulationMapper).upsert(rowCaptor.capture());
        MoisResidentPopulationRow row = rowCaptor.getValue();
        assertThat(row.areaCode()).isEqualTo("1100000000");
        assertThat(row.admmCd()).isEqualTo("1100000000");
        assertThat(row.areaLevel()).isEqualTo("SIDO");
        assertThat(row.totalPopulation()).isEqualTo(9298673L);
        assertThat(row.male100Over()).isEqualTo(314L);
        assertThat(row.female100Over()).isEqualTo(1143L);
    }

    @Test
    void collectResidentPopulationSkipsRootCallForNonSidoLevels() {
        when(moisResidentPopulationMapper.findSidoAdmmCodes()).thenReturn(List.of("1100000000"));
        when(dataCollectClient.callMoisResidentPopulation(
                eq("1100000000"),
                eq("202604"),
                eq("202604"),
                eq("2"),
                eq("1"),
                eq(1),
                eq(100)))
                .thenReturn("""
                        {
                          "Response": {
                            "head": {
                              "pageNo": "1",
                              "resultCode": "0",
                              "totalCount": "1",
                              "numOfRows": "100",
                              "resultMsg": "NORMAL_SERVICE"
                            },
                            "items": {"item": {
                              "admmCd": "1111000000",
                              "statsYm": "202604",
                              "ctpvNm": "서울특별시",
                              "sggNm": "종로구",
                              "totNmprCnt": "10",
                              "maleNmprCnt": "4",
                              "femlNmprCnt": "6"
                            }}
                          }
                        }
                        """);
        when(moisResidentPopulationMapper.findAreaCodeByAdmmCd("1111000000"))
                .thenReturn("1111000000");

        int savedCount = service.collectResidentPopulation("202604", "1", "2");

        assertThat(savedCount).isEqualTo(1);
        verify(dataCollectClient, never()).callMoisResidentPopulation(
                eq("0000000000"),
                eq("202604"),
                eq("202604"),
                eq("2"),
                eq("1"),
                eq(1),
                eq(100));
    }

    @Test
    void collectResidentPopulationMapsAdministrativeDongToLegalDongByCodePrefixAndNameNormalization() {
        when(moisResidentPopulationMapper.findSigunguAdmmCodes()).thenReturn(List.of("4117300000"));
        when(dataCollectClient.callMoisResidentPopulation(
                eq("4117300000"),
                eq("202604"),
                eq("202604"),
                eq("3"),
                eq("1"),
                eq(1),
                eq(100)))
                .thenReturn("""
                        {
                          "Response": {
                            "head": {
                              "pageNo": "1",
                              "resultCode": "0",
                              "totalCount": "1",
                              "numOfRows": "100",
                              "resultMsg": "NORMAL_SERVICE"
                            },
                            "items": {"item": {
                              "admmCd": "4117351000",
                              "statsYm": "202604",
                              "ctpvNm": "경기도",
                              "sggNm": "안양시 동안구",
                              "dongNm": "비산1동",
                              "totNmprCnt": "28155",
                              "maleNmprCnt": "14000",
                              "femlNmprCnt": "14155"
                            }}
                          }
                        }
                        """);
        when(moisResidentPopulationMapper.findAreaCodeByAdmmCd("4117351000"))
                .thenReturn(null);
        when(moisResidentPopulationMapper.findAreaCodeByAdmmCodeAndSourceNames(
                eq("4117351000"),
                eq("경기도"),
                eq("안양시 동안구"),
                eq("비산1동"),
                eq("EUPMYEONDONG")))
                .thenReturn("4117310100");

        int savedCount = service.collectResidentPopulation("202604", "1", "3");

        assertThat(savedCount).isEqualTo(1);
        ArgumentCaptor<MoisResidentPopulationRow> rowCaptor = ArgumentCaptor.forClass(MoisResidentPopulationRow.class);
        verify(moisResidentPopulationMapper).upsert(rowCaptor.capture());
        MoisResidentPopulationRow row = rowCaptor.getValue();
        assertThat(row.areaCode()).isEqualTo("4117310100");
        assertThat(row.admmCd()).isEqualTo("4117351000");
        assertThat(row.areaLevel()).isEqualTo("EUPMYEONDONG");
        assertThat(row.dongNm()).isEqualTo("비산1동");
    }
}
