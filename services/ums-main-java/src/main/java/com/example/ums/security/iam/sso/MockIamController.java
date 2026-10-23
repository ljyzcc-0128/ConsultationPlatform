package com.example.ums.security.iam.sso;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地/测试环境 Mock IAM：模拟晶彩单点登录的授权、token、userinfo 三个端点，
 * 用于在无真实 IAM 凭证时调试 SSO 全链路代码。
 * 真实 IAM 静默授权（依赖晶彩 Cookie）只有正式环境能联调，见文档 6.1 节。
 *
 * <p>
 * 仅在 sso-mock profile 下注册（--spring.profiles.active=sso-mock），生产环境不加载。
 *
 * <p>
 * 调试链路（浏览器直接打开 authorize 即可走完整流程）：
 * 
 * <pre>
 * http://localhost:8080/api/mock-iam/authorize
 *   ?client_id=mock-client
 *   &response_type=code
 *   &redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fauth%2Fsso%2Fcallback
 * → 302 回调（带 code）→ 换 token → 拉用户 → 前端落地页 → 进入首页
 * </pre>
 *
 * <p>
 * 场景调试参数：
 * <ul>
 * <li>&as=EMP001&display=张三 —— 指定 mock 用户身份（默认 TEST001 / Mock 用户）</li>
 * <li>&target_user_id=OTHER —— 模拟待办链接防越权校验，身份不一致时落地页展示"拒绝访问"</li>
 * </ul>
 *
 * <p>
 * 配套环境变量（本地调试）：
 * 
 * <pre>
 * UMS_SSO_ENABLED=true
 * UMS_SSO_TOKEN_URL=http://localhost:8080/api/mock-iam/token
 * UMS_SSO_USERINFO_URL=http://localhost:8080/api/mock-iam/userinfo
 * UMS_SSO_CLIENT_ID=mock-client
 * UMS_SSO_CLIENT_SECRET=mock-secret
 * UMS_SSO_CALLBACK_URL=http://localhost:8080/api/auth/sso/callback
 * UMS_SSO_LANDING_URL=http://localhost:5173/sso/landing
 * </pre>
 */
@RestController
@RequestMapping("/api/mock-iam")
@Profile("sso-mock")
public class MockIamController {

    /** code → "externalId|displayName"（授权码一次性使用） */
    private final Map<String, String> grants = new ConcurrentHashMap<>();
    /** access_token → "externalId|displayName" */
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    /** 模拟 IAM 授权端点：静默"授权"成功，直接下发 code 并 302 到 redirect_uri */
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam String redirect_uri,
            @RequestParam(required = false) String client_id,
            @RequestParam(name = "target_user_id", required = false) String targetUserId,
            @RequestParam(required = false, defaultValue = "TEST001") String as,
            @RequestParam(required = false, defaultValue = "Mock 用户") String display) {
        String code = UUID.randomUUID().toString();
        grants.put(code, as + "|" + display);

        StringBuilder loc = new StringBuilder(redirect_uri)
                .append(redirect_uri.contains("?") ? "&" : "?")
                .append("code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8));
        if (targetUserId != null && !targetUserId.isBlank()) {
            loc.append("&target_user_id=")
                    .append(URLEncoder.encode(targetUserId, StandardCharsets.UTF_8));
        }
        return ResponseEntity.status(302).location(URI.create(loc.toString())).build();
    }

    /**
     * 模拟 token 端点：标准 OAuth2 表单换 token，code 一次性。
     * 兼容两种 client 凭证传递方式（FORM 表单 / BASIC 请求头），便于两种模式回归。
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam String grant_type,
            @RequestParam String code,
            @RequestParam(required = false) String client_id,
            @RequestParam(required = false) String client_secret,
            @RequestParam(required = false) String redirect_uri,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!"authorization_code".equals(grant_type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }
        // BASIC 模式：解析 Authorization: Basic base64(client_id:client_secret)；
        // FORM 模式：凭证在表单 client_id/client_secret（mock 不校验具体值，只校验存在性）
        if (authorization != null && authorization.startsWith("Basic ")) {
            try {
                String decoded = new String(java.util.Base64.getDecoder()
                        .decode(authorization.substring(6)), StandardCharsets.UTF_8);
                if (!decoded.contains(":") || decoded.split(":", 2)[0].isBlank()) {
                    return ResponseEntity.status(401).body(Map.of("error", "invalid_client"));
                }
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(401).body(Map.of("error", "invalid_client"));
            }
        } else if (client_id == null || client_id.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_client"));
        }
        String identity = grants.remove(code);
        if (identity == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant"));
        }
        String accessToken = UUID.randomUUID().toString();
        tokens.put(accessToken, identity);
        return ResponseEntity.ok(Map.of(
                "access_token", accessToken,
                "token_type", "Bearer",
                "expires_in", 3600));
    }

    /** 模拟 userinfo 端点：Bearer token 返回 mock 用户信息 */
    @GetMapping("/userinfo")
    public ResponseEntity<Map<String, Object>> userinfo(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        String identity = token != null ? tokens.get(token) : null;
        if (identity == null) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_token"));
        }
        String[] parts = identity.split("\\|", 2);
        String externalId = parts[0];
        String display = parts.length > 1 ? parts[1] : externalId;
        return ResponseEntity.ok(Map.of(
                "externalId", externalId,
                "username", externalId.toLowerCase(),
                "displayName", display,
                "email", externalId.toLowerCase() + "@mock.jinkosolar.com"));
    }
}
