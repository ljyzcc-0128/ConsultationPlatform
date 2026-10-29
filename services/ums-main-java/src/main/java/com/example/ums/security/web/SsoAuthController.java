package com.example.ums.security.web;

import com.example.ums.security.JwtTokenProvider;
import com.example.ums.security.UserPrincipal;
import com.example.ums.security.iam.sso.SsoProperties;
import com.example.ums.security.iam.sso.SsoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 晶彩单点登录回调接口。
 *
 * <p>
 * 链路：晶彩工作台/消息卡片 → IAM 授权端点（静默下发 code）→ 302 回本接口 →
 * 换取用户信息并签发本系统 JWT → 302 跳转前端落地页 /sso/landing?token=...。
 *
 * <p>
 * 错误一律 302 到落地页并携带 error 参数（浏览器跳转流程中直接返回 JSON
 * 会导致用户看到裸报文），落地页负责展示。
 */
@RestController
@RequestMapping("/api/auth/sso")
@EnableConfigurationProperties(SsoProperties.class)
public class SsoAuthController {

    private static final Logger log = LoggerFactory.getLogger(SsoAuthController.class);

    private final SsoService ssoService;
    private final SsoProperties ssoProperties;
    private final JwtTokenProvider jwtTokenProvider;

    public SsoAuthController(SsoService ssoService, SsoProperties ssoProperties,
            JwtTokenProvider jwtTokenProvider) {
        this.ssoService = ssoService;
        this.ssoProperties = ssoProperties;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 前端登录页动态读取 SSO 授权配置（公开接口，/api/auth/sso/** 已 permitAll）。
     * authorize-url / client-id 随后端 yml 或环境变量切换，
     * 前端无需写死地址、换环境无需重建。client_id 本身即 OAuth 公开字段。
     */
    @GetMapping("/config")
    public java.util.Map<String, String> config() {
        return java.util.Map.of(
                "enabled", String.valueOf(ssoProperties.isEnabled()),
                "authorizeUrl", orEmpty(ssoProperties.getAuthorizeUrl()),
                "clientId", orEmpty(ssoProperties.getClientId()));
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * OAuth 2.0 回调：接收 IAM 重定向携带的授权码。
     *
     * @param code         授权码
     * @param targetUserId 待办/消息链接透传的目标用户标识（防越权校验用，可空）
     * @param target       登录后要落地的前端路径深链（如 /supply-chain，可空）
     */
    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
            @RequestParam(name = "target_user_id", required = false) String targetUserId,
            @RequestParam(required = false) String target,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        // 记录 IAM 实际回传的完整参数串，用于核对回调格式（code / token / 其他）
        log.info("SSO 回调进入: query={}", request.getQueryString());

        String landing = ssoProperties.getFrontendLandingUrl();
        if (landing == null || landing.isBlank()) {
            log.error("未配置 ums.security.sso.frontend-landing-url / UMS_SECURITY_SSO_FRONTENDLANDINGURL，无法回跳前端");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "SSO 未配置前端落地页（ums.security.sso.frontend-landing-url）");
            return;
        }
        String targetParam = (target != null && !target.isBlank())
                ? "&target=" + URLEncoder.encode(target, StandardCharsets.UTF_8)
                : "";

        // 联调旁路（debug-bypass=true，仅测试环境）：跳过 IAM 全部校验，
        // 只要落地页地址可读即直接以测试账号登录进入业务页面
        if (ssoProperties.isDebugBypass()) {
            UserPrincipal bypassPrincipal = ssoService.debugLogin();
            String bypassToken = jwtTokenProvider.generate(bypassPrincipal);
            log.warn("SSO 联调旁路生效: 跳过 IAM 校验，直接回跳落地页 {}", landing);
            response.sendRedirect(landing + "?token="
                    + URLEncoder.encode(bypassToken, StandardCharsets.UTF_8) + targetParam);
            return;
        }

        try {
            UserPrincipal principal = ssoService.ssoLogin(code, targetUserId);
            String token = jwtTokenProvider.generate(principal);
            response.sendRedirect(landing + "?token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8) + targetParam);
        } catch (Exception e) {
            log.warn("SSO 回调失败: {}", e.getMessage());
            response.sendRedirect(landing + "?error="
                    + URLEncoder.encode(e.getMessage() == null ? "单点登录失败" : e.getMessage(),
                            StandardCharsets.UTF_8));
        }
    }
}
