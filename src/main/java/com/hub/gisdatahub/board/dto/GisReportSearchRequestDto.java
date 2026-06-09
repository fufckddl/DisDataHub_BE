package com.hub.gisdatahub.board.dto;

import lombok.Data;

@Data
public class GisReportSearchRequestDto {
    private String searchWord;
    private String reportCategoryCode;
    private String errorTypeCode;
    private String processStatusCode;
    private String sido;
    private String sigungu;
    private String eupmyeondong;
}
