package com.hub.gisdatahub.admin.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hub.gisdatahub.admin.dto.UserManagementLogDto;
import com.hub.gisdatahub.admin.dto.UserManagementTypeDto;
import com.hub.gisdatahub.admin.mapper.AdminUserMapper;
import com.hub.gisdatahub.user.domain.User;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    @Autowired
    public AdminUserMapper adminUserMapper;

    @Override
    public List<User> getUserList() {
        return adminUserMapper.findUserAll();
    }

    @Override
    public User getUser(Integer id) {
        return adminUserMapper.findUserById(id);
    }

    @Override
    public List<UserManagementTypeDto> getManagementTypeList() {
        return adminUserMapper.findUserManagementTypeList();
    }

    @Override
    public void applyUserManagement(UserManagementLogDto userManagementLogDto, String status) {

        Integer targetUserId = userManagementLogDto.getTargetUserId();

        adminUserMapper.updateUserStatus(targetUserId, status);
        adminUserMapper.insertUserManagementLog(userManagementLogDto);
    }

    @Override
    public List<UserManagementLogDto> getUserManagementLogList() {
        return adminUserMapper.findUserManagementLogList();
    }
}