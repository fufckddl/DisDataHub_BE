package com.hub.gisdatahub.board.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoCodeResponse {
    private String result;
    private String address;
    private Double latitude;
    private Double longitude;
    private String message;
}
