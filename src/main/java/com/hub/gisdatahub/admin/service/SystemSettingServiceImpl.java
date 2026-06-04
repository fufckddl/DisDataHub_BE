package com.hub.gisdatahub.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hub.gisdatahub.admin.dto.SystemSettingConfigLogDto;
import com.hub.gisdatahub.admin.mapper.SystemSettingMapper;

@Service
public class SystemSettingServiceImpl implements SystemSettingService {

    @Autowired
    private SystemSettingMapper systemSettingMapper;

    @Override
    public Integer registerSystemSettingConfigLog(SystemSettingConfigLogDto systemSettingConfigLogDto) {
        return systemSettingMapper.insertSystemSettingConfigLog(systemSettingConfigLogDto);
    }

}