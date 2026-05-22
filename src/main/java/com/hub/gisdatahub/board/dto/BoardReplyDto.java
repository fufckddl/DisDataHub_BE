package com.hub.gisdatahub.board.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class BoardReplyDto {
    private Long replyId;
    private Long postId;
    private Integer userId;
    private String replyWriterName;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deletedYn;
}
