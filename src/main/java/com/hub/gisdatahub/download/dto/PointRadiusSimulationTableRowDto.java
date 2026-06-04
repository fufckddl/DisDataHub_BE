package com.hub.gisdatahub.download.dto;

import lombok.Data;

@Data
public class PointRadiusSimulationTableRowDto {
    private Integer rank;
    private Long featureId;
    private String featureName;
    private Integer nearbyCount;
    private Double latitude;
    private Double longitude;
}
