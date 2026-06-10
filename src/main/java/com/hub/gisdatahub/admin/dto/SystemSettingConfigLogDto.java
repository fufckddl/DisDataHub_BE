package com.hub.gisdatahub.admin.dto;

import lombok.Data;

@Data
public class SystemSettingConfigLogDto {
    private Long logId;
    private Long adminUserId;
    private String settingKey;
    private String beforeValue;
    private String afterValue;
    private String description;
    private String createdAt;
    private String adminUsername;
}