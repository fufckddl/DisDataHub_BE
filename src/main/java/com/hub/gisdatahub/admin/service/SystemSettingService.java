package com.hub.gisdatahub.admin.service;

import java.util.List;

import com.hub.gisdatahub.admin.dto.SystemSettingConfigLogDto;
import com.hub.gisdatahub.admin.dto.SystemSettingDto;

public interface SystemSettingService {
    
    public void updateSystemSetting(SystemSettingConfigLogDto systemSettingConfigLogDto);
    public List<SystemSettingDto> getSystemSettingList();
    public List<SystemSettingConfigLogDto> getSystemSettingConfigLogList();
}