package com.hub.gisdatahub.board.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class InquiryDetailDto {
    // sd_board_post 공통 게시글 정보
    private Long postId;
    private String boardTypeCode;
    private Integer userId;
    private String title;
    private String content;
    private String visibilityStatus;
    private String pinnedYn;
    private LocalDateTime createdAt;
    private Integer viewCount;
    private LocalDateTime updatedAt;
    private String deletedYn;
    // sd_inquiry_detail 문의 전용 정보
    private String inquiryCategoryCode;
    private String inquiryStatusCode;
    private LocalDateTime inquiryCreatedAt;
    private LocalDateTime inquiryUpdatedAt;
    // 답변 및 상태변화 
    private Long replyId;
    private Integer answerUserId;
    private String replyWriterName;
    private String answerContent;
    private LocalDateTime answeredAt;
}
