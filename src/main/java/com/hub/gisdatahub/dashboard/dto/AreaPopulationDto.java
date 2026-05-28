package com.hub.gisdatahub.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// sd_resident_population 테이블에서 조회한 원본 주민등록 인구 데이터를 담는 DTO입니다.
public class AreaPopulationDto {

    private Long populationId;

    private String areaCode;
    private String areaName;
    private String fullName;
    private String sourceCode;
    private LocalDate baseDate;
    private String hour;

    private BigDecimal totalPopulation;
    private BigDecimal malePopulation;
    private BigDecimal femalePopulation;

    // 행안부 주민등록 인구 API는 10세 단위 성/연령 값을 제공합니다.
    private BigDecimal male0To9;
    private BigDecimal male10To19;
    private BigDecimal male20To29;
    private BigDecimal male30To39;
    private BigDecimal male40To49;
    private BigDecimal male50To59;
    private BigDecimal male60To69;
    private BigDecimal male70To79;
    private BigDecimal male80To89;
    private BigDecimal male90To99;
    private BigDecimal male100Over;

    private BigDecimal female0To9;
    private BigDecimal female10To19;
    private BigDecimal female20To29;
    private BigDecimal female30To39;
    private BigDecimal female40To49;
    private BigDecimal female50To59;
    private BigDecimal female60To69;
    private BigDecimal female70To79;
    private BigDecimal female80To89;
    private BigDecimal female90To99;
    private BigDecimal female100Over;

    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
