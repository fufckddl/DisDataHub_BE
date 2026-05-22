package com.hub.gisdatahub.board.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class GisReportProcessHistoryDto {
    private Long historyId;
    private Long postId;
    private Integer userId;
    private String processStatusCode;
    private String processContent;
    private LocalDateTime createdAt;
    private String deletedYn;
}
