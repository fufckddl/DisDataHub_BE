package com.hub.gisdatahub.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;

import com.hub.gisdatahub.security.JwtAuthenticationFilter;
import com.hub.gisdatahub.security.JwtUtil;

/**
 * [Config] SecurityConfig
 * Spring Security 설정 파일입니다.
 *
 * 주요 설정:
 * 1. CSRF 비활성화 → REST API에서는 사용하지 않음
 * 2. 세션 Stateless → JWT 기반이므로 서버에 세션 저장 안 함
 * 3. 요청 권한 설정 → 어떤 API에 인증이 필요한지 정의
 * 4. JWT 필터 등록 → 모든 요청에서 토큰 검사
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * BCryptPasswordEncoder 빈 등록
     * UserService에서 @Autowired(생성자 주입)로 사용합니다.
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Security 필터 체인 설정
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 보호 비활성화 (REST API는 쿠키 기반 세션을 쓰지 않으므로 불필요)
                .csrf(csrf -> csrf.disable())

                // 세션을 생성하지 않음 (JWT Stateless)
                .sessionManagement(session ->
                    // STATELESS 설정으로 인해서 이 프로젝트에서는 getattribute, setattribute 못씀
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 요청 URL별 인증 필요 여부 설정
                .authorizeHttpRequests(auth -> auth
                        // 회원가입, 로그인은 토큰 없이 사용 가능
                        .requestMatchers(HttpMethod.POST, "/api/users/join", "/api/users/login").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/opendata/collect/living-population/sigungu/collect",
                                "/api/opendata/collect/sdot/visitor/collect").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/open-data/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/opendata/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 게시판 GIS 오류 제보 게시판
                        .requestMatchers(HttpMethod.GET, "/api/board/gis-reports/**").permitAll()
                        // 게시판 문의게시판 조회 기능 
                        .requestMatchers(HttpMethod.GET, "/api/board/inquiries/**").permitAll()
                        // 게시판 공지게시판 목록 로그인 없이 조회 가능하게 만들어주는 설정
                        .requestMatchers(HttpMethod.GET, "/api/board/notices/**").permitAll()
                        // 공지사항 게시글 테스트용 코드 추가 (비로그인 게시글작성 o)
                        .requestMatchers(HttpMethod.POST, "/api/board/notices/createNotice").permitAll()
                        // 다운로드
                        .requestMatchers(HttpMethod.GET, "/api/download/datasets", "/api/download/datasets/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/download/datasets/*/preview-geojson").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/download/datasets/*/download").permitAll()
                        
                        // 게시글 목록·상세 조회는 비로그인도 가능
                        // NOTE: 일부 환경에서 requestMatchers(HttpMethod.GET, "/api/articles/**")가 "/api/articles/{id}"에 매칭되지 않는 이슈가 있어
                        // AntPathRequestMatcher로 명시합니다.
                        .requestMatchers(new AntPathRequestMatcher("/api/articles", "GET")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/articles/*", "GET")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/articles/**", "GET")).permitAll()
                        // 나머지 모든 요청은 로그인(유효한 JWT) 필요
                        .anyRequest().authenticated()
                )
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174"));
                    config.setAllowedMethods(List.of("*"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                // JWT 필터를 Spring Security의 기본 인증 필터 앞에 삽입
                // → 요청이 올 때 JwtAuthenticationFilter가 먼저 실행됨
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtUtil),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
