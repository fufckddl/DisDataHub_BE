package com.hub.gisdatahub.download.dto;

import lombok.Data;

// feature를 읽고와서 CSV/GeoJson/KML 파일로 조립하기 위한 DTO
@Data
public class DatasetFeatureExportDto {
    private Long featureId;
    private String featureName;
    private String spatialType;

    private String propertiesJson; // properties 컬럼을 문자열 JSON으로 받음

    private String geometryWkt; // CSV용 WKT
    private String geometryJson; // KML / 기타 확장용 GeoJson geometry
}
