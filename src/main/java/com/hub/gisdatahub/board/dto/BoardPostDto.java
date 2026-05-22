package com.hub.gisdatahub.board.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class BoardPostDto {
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
}
