package com.hub.gisdatahub.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.admin.dto.SystemSettingConfigLogDto;
import com.hub.gisdatahub.admin.dto.SystemSettingDto;

@Mapper
public interface SystemSettingMapper {
    // 시스템 설정 목록
    public SystemSettingDto findSystemSettingByKey(String settingKey);

    // 시스템 설정 업데이트
    public void updateSystemSettingValue(@Param("settingKey") String settingKey, @Param("settingValue") String settingValue);
    
    // 시스템 설정 변경 로그
    public Integer insertSystemSettingConfigLog(SystemSettingConfigLogDto systemSettingConfigLogDto);
    public List<SystemSettingConfigLogDto> findSystemSettingConfigLogList();

    // 시스템 세팅 조회
    public List<SystemSettingDto> findSystemSettingAll();
}
