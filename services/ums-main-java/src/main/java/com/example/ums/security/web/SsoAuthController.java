package com.example.ums.security.web;

import com.example.ums.security.JwtTokenProvider;
import com.example.ums.security.UserPrincipal;
import com.example.ums.security.iam.sso.SsoProperties;
import com.example.ums.security.iam.sso.SsoService;
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
            HttpServletResponse response) throws IOException {
        String landing = ssoProperties.getFrontendLandingUrl();
        String targetParam = (target != null && !target.isBlank())
                ? "&target=" + URLEncoder.encode(target, StandardCharsets.UTF_8)
                : "";
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
