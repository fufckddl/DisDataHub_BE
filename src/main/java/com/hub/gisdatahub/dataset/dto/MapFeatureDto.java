package com.hub.gisdatahub.dataset.dto;

import lombok.Data;

@Data
public class MapFeatureDto {
    private String featureName;  // 객체 이름 (클릭 시 팝업용)
    private String spatialType;  // 공간 타입 (POINT, POLYGON 등)
    private String geoJson;      // ST_AsGeoJSON으로 번역된 문자열!
}
