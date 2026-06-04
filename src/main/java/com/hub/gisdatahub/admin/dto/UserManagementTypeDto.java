package com.hub.gisdatahub.admin.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserManagementTypeDto {
    private Integer targetId;
    private String typeCode;
    private String typeName;
    private String targetRole;
    private String durationDays;
    private LocalDateTime createdAt;
}
