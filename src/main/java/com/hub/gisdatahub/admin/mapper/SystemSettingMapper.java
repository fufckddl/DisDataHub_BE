package com.hub.gisdatahub.admin.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.admin.dto.SystemSettingConfigLogDto;

@Mapper
public interface SystemSettingMapper {
    Integer insertSystemSettingConfigLog(SystemSettingConfigLogDto systemSettingConfigLogDto);
}
