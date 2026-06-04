package com.hub.gisdatahub.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.hub.gisdatahub.admin.dto.UserManagementLogDto;
import com.hub.gisdatahub.admin.dto.UserManagementTypeDto;
import com.hub.gisdatahub.admin.mapper.AdminUserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserMapper adminUserMapper;

    @Override
    public List<UserManagementTypeDto> getUserManagementTypeList() {
        return adminUserMapper.selectUserManagementTypeList();
    }

    @Override
    public Integer registerUserManagementLog(UserManagementLogDto userManagementLogDto) {
        return adminUserMapper.insertUserManagementLog(userManagementLogDto);
    }

}