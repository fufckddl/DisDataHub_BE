package com.hub.gisdatahub.board.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class GisReportCreateRequestDto {
    private Long postId;
    private Integer userId;
    private String title;
    private String content;
    private String visibilityStatus;
    private String reportCategoryCode;
    private String errorTypeCode;
    private String targetDataName;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String sido;
    private String sigungu;
    private String eupmyeondong;
}
