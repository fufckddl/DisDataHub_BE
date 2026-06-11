package com.hub.gisdatahub.user.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.hub.gisdatahub.exception.UserException;
import com.hub.gisdatahub.security.JwtUtil;
import com.hub.gisdatahub.user.domain.User;
import com.hub.gisdatahub.user.dto.UserJoinRequest;
import com.hub.gisdatahub.user.dto.UserLoginRequestDto;
import com.hub.gisdatahub.user.dto.UserLoginResponseDto;
import com.hub.gisdatahub.user.mapper.UserMapper;
import com.hub.gisdatahub.user.type.UserRole;
import com.hub.gisdatahub.user.type.UserStatus;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserException userException;

    public UserService(
            UserMapper userMapper,
            BCryptPasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            UserException userException) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userException = userException;
    }

    public void join(UserJoinRequest userJoinDto) {
        User existing = userMapper.findByAccountName(userJoinDto.getAccountName());
        if (existing != null) {
            throw userException.alredayUsedAccountException();
        }
        User user = new User();
        user.setAccountName(userJoinDto.getAccountName());
        user.setPassword(passwordEncoder.encode(userJoinDto.getPassword()));
        user.setUsername(userJoinDto.getUsername());
        user.setEmail(userJoinDto.getEmail());
        user.setGender(userJoinDto.getGender());
        user.setOrganization(userJoinDto.getOrganization());
        user.setDepartment(userJoinDto.getDepartment());
        user.setRole(userJoinDto.getRole());
        user.setStatus(UserStatus.ACTIVATE);

        userMapper.joinUser(user);
    }

    public UserLoginResponseDto login(UserLoginRequestDto userLoginRequestDto) {
        return loginByRole(userLoginRequestDto, UserRole.USER, UserRole.RESEARCHER);
    }

    public UserLoginResponseDto adminLogin(UserLoginRequestDto userLoginRequestDto) {
        return loginByRole(userLoginRequestDto, UserRole.ADMIN);
    }

    private UserLoginResponseDto loginByRole(UserLoginRequestDto userLoginRequestDto, UserRole... allowedRoles) {
        User user = userMapper.findByAccountName(userLoginRequestDto.getAccountName());

        if (user == null) {
            return new UserLoginResponseDto(false, null, null, 0);
        }

        if (!passwordEncoder.matches(userLoginRequestDto.getPassword(), user.getPassword())) {
            return new UserLoginResponseDto(false, null, null, 0);
        }

        if (!isAllowedRole(user.getRole(), allowedRoles)) {
            return new UserLoginResponseDto(false, null, null, 0);
        }

        String token = jwtUtil.generateToken(user.getId());
        return new UserLoginResponseDto(true, token, user.getUsername(), user.getId(), user.getRole().name());
    }

    private boolean isAllowedRole(UserRole userRole, UserRole... allowedRoles) {
        if (userRole == null) {
            return false;
        }
        for (UserRole allowedRole : allowedRoles) {
            if (userRole == allowedRole) {
                return true;
            }
        }
        return false;
    }

    public User getMe(int userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        user.setPassword(null);
        return user;
    }
}
