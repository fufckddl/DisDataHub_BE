package com.hub.gisdatahub.admin.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SystemSettingDto {
    private Integer settingId;
    private String settingKey;
    private String settingName;
    private String settingValue;
    private String description;
    private Boolean isEditable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
