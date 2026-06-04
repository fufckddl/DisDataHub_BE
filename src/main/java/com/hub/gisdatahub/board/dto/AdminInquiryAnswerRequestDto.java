package com.hub.gisdatahub.board.dto;

import lombok.Data;

@Data
public class AdminInquiryAnswerRequestDto {
    private Integer adminUserId;
    private String replyWriterName;
    private String answerContent;
    private String inquiryStatusCode;
}
