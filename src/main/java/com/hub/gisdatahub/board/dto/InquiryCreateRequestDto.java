package com.hub.gisdatahub.board.dto;

import lombok.Data;

@Data
public class InquiryCreateRequestDto {
    private Long postId;
    private Integer userId;
    private String title;
    private String content;
    private String visibilityStatus;
    private String inquiryCategoryCode;
}
