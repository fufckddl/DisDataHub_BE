package com.hub.gisdatahub.admin.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hub.gisdatahub.admin.dto.SystemSettingConfigLogDto;
import com.hub.gisdatahub.admin.dto.SystemSettingDto;
import com.hub.gisdatahub.admin.mapper.SystemSettingMapper;

@Service
public class SystemSettingServiceImpl implements SystemSettingService {

    @Autowired
    private SystemSettingMapper systemSettingMapper;

    @Override
    @Transactional
    public void updateSystemSetting(SystemSettingConfigLogDto systemSettingConfigLogDto) {

        SystemSettingDto beforeSystemSetting =
            systemSettingMapper.findSystemSettingByKey(systemSettingConfigLogDto.getSettingKey());

        systemSettingConfigLogDto.setBeforeValue(beforeSystemSetting.getSettingValue());

        systemSettingMapper.updateSystemSettingValue(
            systemSettingConfigLogDto.getSettingKey(),
            systemSettingConfigLogDto.getAfterValue()
        );

        systemSettingMapper.insertSystemSettingConfigLog(systemSettingConfigLogDto);
    }

    @Override
    public List<SystemSettingDto> getSystemSettingList() {
        return systemSettingMapper.findSystemSettingAll();
    }

    @Override
    public List<SystemSettingConfigLogDto> getSystemSettingConfigLogList() {
        return systemSettingMapper.findSystemSettingConfigLogList();
    }
}