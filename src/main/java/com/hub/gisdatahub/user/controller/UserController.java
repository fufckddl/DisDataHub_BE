package com.hub.gisdatahub.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.user.domain.User;
import com.hub.gisdatahub.user.dto.UserJoinRequest;
import com.hub.gisdatahub.user.dto.UserLoginRequestDto;
import com.hub.gisdatahub.user.dto.UserLoginResponseDto;
import com.hub.gisdatahub.user.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;



@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("join")
    public ResponseEntity<String> join(@RequestBody UserJoinRequest entity) {
        userService.join(entity);
        return ResponseEntity.ok("회원가입 완료");
    }
    @PostMapping("login")
    public ResponseEntity<UserLoginResponseDto> login(@RequestBody UserLoginRequestDto entity) {
        UserLoginResponseDto userLoginResponseDto = userService.login(entity);
        return ResponseEntity.ok(userLoginResponseDto);
    }
    @PostMapping("admin/login")
    public ResponseEntity<UserLoginResponseDto> adminLogin(@RequestBody UserLoginRequestDto entity) {
        UserLoginResponseDto userLoginResponseDto = userService.adminLogin(entity);
        return ResponseEntity.ok(userLoginResponseDto);
    }
    @GetMapping("me")
    public ResponseEntity<User> getMethodName(Authentication authentication) {
        int userId = Integer.parseInt((String) authentication.getPrincipal());
        User user = userService.getMe(userId);
        return ResponseEntity.ok(user);
    }
}
