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
// sd_area_population 테이블에서 조회한 원본 인구 데이터를 담는 DTO입니다.
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

    // 서울 생활인구 API는 연령/성별 값을 소수점 포함 추정치로 제공하므로 BigDecimal로 관리합니다.
    private BigDecimal male0To9;
    private BigDecimal male10To14;
    private BigDecimal male15To19;
    private BigDecimal male20To24;
    private BigDecimal male25To29;
    private BigDecimal male30To34;
    private BigDecimal male35To39;
    private BigDecimal male40To44;
    private BigDecimal male45To49;
    private BigDecimal male50To54;
    private BigDecimal male55To59;
    private BigDecimal male60To64;
    private BigDecimal male65To69;
    private BigDecimal male70To74;

    private BigDecimal female0To9;
    private BigDecimal female10To14;
    private BigDecimal female15To19;
    private BigDecimal female20To24;
    private BigDecimal female25To29;
    private BigDecimal female30To34;
    private BigDecimal female35To39;
    private BigDecimal female40To44;
    private BigDecimal female45To49;
    private BigDecimal female50To54;
    private BigDecimal female55To59;
    private BigDecimal female60To64;
    private BigDecimal female65To69;
    private BigDecimal female70To74;

    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
