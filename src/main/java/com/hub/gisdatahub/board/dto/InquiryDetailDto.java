package com.hub.gisdatahub.board.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class InquiryDetailDto {
    private Long postId;
    private String inquiryCategoryCode;
    private String inquiryStatusCode;
    private LocalDateTime createAt;
    private LocalDateTime updatedAt;
}
