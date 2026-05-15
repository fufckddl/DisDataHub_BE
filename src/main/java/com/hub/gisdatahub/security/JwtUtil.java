package com.hub.gisdatahub.security;

import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    // JWT 서명에 사용할 Key 객체 (application.yml의 jwt.secret 값으로 생성)
    private final Key key;

    // 토큰 유효 시간 (milliseconds, application.yml의 jwt.expiration 값)
    private final long expiration;

    // 생성자: Spring이 application.yml 값을 자동으로 주입
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        // HMAC-SHA 방식의 Key 생성 (secret은 32바이트 이상이어야 함)
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiration = expiration;
    }

    /**
     * JWT 토큰 생성
     * @param userId DB의 유저 ID
     * @return 생성된 JWT 문자열
     */
    public String generateToken(int userId) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))        // subject에 userId 저장
                .setIssuedAt(new Date())                   // 발급 시각
                .setExpiration(new Date(System.currentTimeMillis() + expiration)) // 만료 시각
                .signWith(key, SignatureAlgorithm.HS256)   // HS256 서명
                .compact();                                // 문자열로 변환
    }

    /**
     * 토큰에서 userId 추출
     * @param token JWT 문자열
     * @return 유저 ID (int)
     */
    public int getUserIdFromToken(String token) {
        String subject = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();  // generateToken에서 넣었던 userId
        return Integer.parseInt(subject);
    }

    /**
     * 토큰 유효성 검사
     * @param token JWT 문자열
     * @return 유효하면 true, 만료되거나 변조되었으면 false
     */
    public boolean isValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치, 만료, 형식 오류 등 모두 false 반환
            return false;
        }
    }
}

