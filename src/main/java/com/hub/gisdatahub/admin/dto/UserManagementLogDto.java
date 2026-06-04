package com.hub.gisdatahub.admin.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserManagementLogDto {
    private Integer logId;
    private Integer adminUserId;
    private Integer targetUserId;
    private Integer typeId;
    private String description;
    private LocalDateTime createdAt;
}
