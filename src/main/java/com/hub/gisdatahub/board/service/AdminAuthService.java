package com.hub.gisdatahub.board.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.user.domain.User;
import com.hub.gisdatahub.user.mapper.UserMapper;

@Service
public class AdminAuthService {

    private final UserMapper userMapper;

    public AdminAuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public void requireAdmin(Authentication authentication) {

        if (authentication == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        if (!authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        Integer userId;

        try {
            userId = Integer.parseInt((String) authentication.getPrincipal());
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "잘못된 인증 정보입니다."
            );
        }

        User user = userMapper.findById(userId);

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "사용자 정보를 찾을 수 없습니다."
            );
        }
        String role = String.valueOf(user.getRole()).trim();

        if (!"ADMIN".equals(role)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "관리자만 접근할 수 있습니다."
            );
        }
    }
}