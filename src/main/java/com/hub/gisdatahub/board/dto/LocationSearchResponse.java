package com.hub.gisdatahub.board.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationSearchResponse {
    private String title;
    private String address;
    private Double latitude;
    private Double longitude;
    private String sido;
    private String sigungu;
    private String eupmyeondong;
}