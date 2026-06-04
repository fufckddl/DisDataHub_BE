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
        System.out.println("========== [관리자 권한 검사 시작] ==========");
        System.out.println("[관리자 권한 검사] authentication = " + authentication);

        if (authentication == null) {
            System.out.println("[관리자 권한 검사 실패] authentication이 null입니다.");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        System.out.println("[관리자 권한 검사] isAuthenticated = " + authentication.isAuthenticated());
        System.out.println("[관리자 권한 검사] principal = " + authentication.getPrincipal());
        System.out.println("[관리자 권한 검사] authorities = " + authentication.getAuthorities());

        if (!authentication.isAuthenticated()) {
            System.out.println("[관리자 권한 검사 실패] 인증되지 않은 사용자입니다.");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        Integer userId;

        try {
            userId = Integer.parseInt((String) authentication.getPrincipal());
        } catch (Exception e) {
            System.out.println("[관리자 권한 검사 실패] principal에서 userId 변환 실패");
            System.out.println("[관리자 권한 검사 실패] principal 값 = " + authentication.getPrincipal());
            e.printStackTrace();

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "잘못된 인증 정보입니다."
            );
        }

        System.out.println("[관리자 권한 검사] token userId = " + userId);

        User user = userMapper.findById(userId);

        if (user == null) {
            System.out.println("[관리자 권한 검사 실패] DB에서 사용자를 찾을 수 없습니다. userId = " + userId);

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "사용자 정보를 찾을 수 없습니다."
            );
        }

        System.out.println("[관리자 권한 검사] DB user id = " + user.getId());
        System.out.println("[관리자 권한 검사] DB username = " + user.getUsername());
        System.out.println("[관리자 권한 검사] DB role = " + user.getRole());

        String role = String.valueOf(user.getRole()).trim();

        System.out.println("[관리자 권한 검사] 비교용 role = " + role);

        if (!"ADMIN".equals(role)) {
            System.out.println("[관리자 권한 검사 실패] ADMIN이 아닙니다. 현재 role = " + role);

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "관리자만 접근할 수 있습니다."
            );
        }

        System.out.println("[관리자 권한 검사 성공] ADMIN 접근 허용. userId = " + userId);
        System.out.println("========== [관리자 권한 검사 종료] ==========");
    }
}