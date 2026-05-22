package com.hub.gisdatahub.board.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class GisReportDetailDto {
    private Long postId;
    private String reportCategoryCode;
    private String errorTypeCode;
    private String processStatusCode;
    private String targetDataName;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String sido;
    private String sigungu;
    private String eupmyeondong;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
