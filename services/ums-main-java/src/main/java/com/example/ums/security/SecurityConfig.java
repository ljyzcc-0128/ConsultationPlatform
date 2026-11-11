package com.example.ums.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/sso/**").permitAll()
                // Mock IAM（仅 sso-mock profile 存在，生产环境无此端点）
                .requestMatchers("/api/mock-iam/**").permitAll()
                .requestMatchers("/api/news/**", "/actuator/**").permitAll()
                .requestMatchers("/api/v1/search").permitAll()
                .requestMatchers("/api/v1/contents/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // 内部服务间调用（爬虫 /internal/trigger、AI 补加工 /internal/ai 等），一期内网信任不鉴权
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers("/api/me/**").authenticated()
                .requestMatchers("/api/v1/supply-chain/**").authenticated()
                // 错误转发端点必须放行：控制器抛 500 时 Spring Boot 以 ERROR dispatch 转发到 /error，
                // 此时 JWT 过滤器不再执行、安全上下文为空，若拦截会导致 500 错误以 401 返回，
                // 前端收到 401 就会误清登录态（表现为“报错直接退出登录”）
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"未登录或登录已过期\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"没有操作权限\"}");
                }));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
