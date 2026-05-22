package com.hub.gisdatahub.opendata.collect.dto.seoul;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SeoulPopulationRow(
    String areaCode,
    String sourceCode,
    LocalDate baseDate,
    String hour,
    BigDecimal totalPopulation,
    BigDecimal malePopulation,
    BigDecimal femalePopulation,
    BigDecimal male0To9,
    BigDecimal male10To14,
    BigDecimal male15To19,
    BigDecimal male20To24,
    BigDecimal male25To29,
    BigDecimal male30To34,
    BigDecimal male35To39,
    BigDecimal male40To44,
    BigDecimal male45To49,
    BigDecimal male50To54,
    BigDecimal male55To59,
    BigDecimal male60To64,
    BigDecimal male65To69,
    BigDecimal male70To74,
    BigDecimal female0To9,
    BigDecimal female10To14,
    BigDecimal female15To19,
    BigDecimal female20To24,
    BigDecimal female25To29,
    BigDecimal female30To34,
    BigDecimal female35To39,
    BigDecimal female40To44,
    BigDecimal female45To49,
    BigDecimal female50To54,
    BigDecimal female55To59,
    BigDecimal female60To64,
    BigDecimal female65To69,
    BigDecimal female70To74,
    String metadata
) {
}
