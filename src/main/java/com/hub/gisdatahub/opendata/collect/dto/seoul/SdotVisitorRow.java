package com.hub.gisdatahub.opendata.collect.dto.seoul;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SdotVisitorRow(
    String areaCode,
    String sourceCode,
    LocalDate baseDate,
    String hour,
    LocalDateTime sensingTime,
    LocalDateTime registeredAt,
    String modelName,
    String serialNo,
    String region,
    String autonomousDistrict,
    String administrativeDistrict,
    int visitorCount,
    String metadata
) {
}
