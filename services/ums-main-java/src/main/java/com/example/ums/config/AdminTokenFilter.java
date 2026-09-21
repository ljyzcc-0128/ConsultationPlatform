package com.example.ums.config;

import com.example.ums.admin.aspect.OperatorContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理接口简化鉴权（已下线）。
 * Phase 5 已迁移至 Spring Security JWT + RBAC（SecurityConfig + JwtAuthFilter）。
 * 此类保留为占位，不再注册为 Spring Bean。
 */
// @Component 已移除，由 SecurityConfig 接管
@Order(1)
public class AdminTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);

    private final String adminToken;
    private volatile boolean warned = false;

    public AdminTokenFilter(@Value("${ums.admin.token:}") String adminToken) {
        this.adminToken = adminToken == null ? "" : adminToken.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String operator = request.getHeader("X-Admin-User");
        OperatorContext.set(operator);
        try {
            if (!adminToken.isEmpty()) {
                String presented = request.getHeader("X-Admin-Token");
                if (!adminToken.equals(presented)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"invalid or missing X-Admin-Token\"}");
                    return;
                }
            } else if (!warned) {
                warned = true;
                log.warn("UMS_ADMIN_TOKEN 未配置，管理接口 /api/v1/admin/** 处于无鉴权开放状态（仅限本地开发）");
            }
            chain.doFilter(request, response);
        } finally {
            OperatorContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin");
    }
}
