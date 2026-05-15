package com.hub.gisdatahub.security;

import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * [Security] JwtAuthenticationFilter
 * 모든 HTTP 요청을 가로채서 JWT 토큰을 검증하는 필터입니다.
 * OncePerRequestFilter: 요청당 딱 한 번만 실행됨을 보장합니다.
 *
 * 처리 흐름:
 * 1. Authorization 헤더에서 토큰 추출
 * 2. 토큰 유효성 검사
 * 3. 유효하면 SecurityContext에 인증 정보 저장
 * 4. 다음 필터로 넘김 (filterChain.doFilter)
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, java.io.IOException {

        // 1. Authorization 헤더 꺼내기
        String header = request.getHeader("Authorization");

        // 2. 헤더가 없거나 "Bearer "로 시작하지 않으면 → 로그인 없이 통과
        //    (인증이 필요한 API는 SecurityConfig에서 막힘)
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. "Bearer " 제거 → 순수 토큰 문자열만 추출
        //    예: "Bearer eyJhbGci..." → "eyJhbGci..."
        String token = header.substring(7);

        // 4. 토큰 유효성 검사
        if (jwtUtil.isValid(token)) {
            // 5. 토큰에서 userId 추출
            int userId = jwtUtil.getUserIdFromToken(token);

            /*
             * 6. SecurityContext에 인증 정보 저장
             *    principal 자리에 userId(String)를 넣어둠
             *    → 컨트롤러에서 authentication.getPrincipal()로 꺼낼 수 있음
             *
             *    UsernamePasswordAuthenticationToken(principal, credentials, authorities)
             */
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            String.valueOf(userId),   // principal = userId
                            null,                     // credentials (불필요)
                            Collections.emptyList()); // 권한 목록 (Role 없음)

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 7. 다음 필터로 넘김
        filterChain.doFilter(request, response);
    }
}
