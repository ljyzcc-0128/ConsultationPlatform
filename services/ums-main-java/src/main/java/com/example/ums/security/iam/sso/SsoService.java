package com.example.ums.security.iam.sso;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.ums.security.UserPrincipal;
import com.example.ums.security.iam.IamUserStore;
import com.example.ums.user.model.CpUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 晶彩单点登录服务（OAuth 2.0 授权码模式，RFC 6749）。
 *
 * <p>
 * 链路（对应《晶科能源IAM-OAuth2.0单点认证集成文档》方案二 + 第 2 节接口）：
 * <ol>
 * <li>晶彩/OA 入口或业务应用跳转 IAM 授权端点，IAM 颁发一次性 code，
 *     重定向回 /api/auth/sso/callback（文档 2.1 节）</li>
 * <li>本类 POST token_url，表单携带 code + client_id + client_secret + grant_type
 *     + redirect_uri 换 access_token（文档 2.2 节，凭证放表单，网关鉴权头可选透传）</li>
 * <li>拿到 access_token 即视为登录成功（2026-11 联调决定：JIP 的 getUserInfo 端点
 *     需要 KeyAuth 鉴权 Key 暂未提供，用户信息回查下线；待办链接携带 target_user_id
 *     时以其为账户标识，否则落共享联调账号 iam_sso_user）</li>
 * <li>查/建本地 cp_user（首次登录默认普通用户 USER 角色），
 *     返回 UserPrincipal，由 Controller 签发本系统 JWT</li>
 * </ol>
 */
@Service
@EnableConfigurationProperties(SsoProperties.class)
@RequiredArgsConstructor
public class SsoService {

    private static final Logger log = LoggerFactory.getLogger(SsoService.class);

    private final SsoProperties props;
    private final IamUserStore userStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            // 部分网关/IAM 端点会 302，标准浏览器语义下应跟随（不跟随 HTTPS→HTTPS 重定向）
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 单点登录主流程：授权码 → access_token（即登录成功）→ 本地账户。
     *
     * @param code         IAM 重定向回的授权码
     * @param targetUserId 待办/消息链接携带的目标用户标识，可为空；非空时作为登录账户标识
     */
    public UserPrincipal ssoLogin(String code, String targetUserId) {
        if (!props.isEnabled()) {
            throw new BadCredentialsException("单点登录未启用");
        }
        if (code == null || code.isBlank()) {
            throw new BadCredentialsException("缺少授权码（code）");
        }
        requireConfigured(props.getTokenUrl(), "ums.security.sso.token-url / UMS_SECURITY_SSO_TOKENURL");
        requireConfigured(props.getClientId(), "ums.security.sso.client-id / UMS_SECURITY_SSO_CLIENTID");
        requireConfigured(props.getClientSecret(), "ums.security.sso.client-secret / UMS_SECURITY_SSO_CLIENTSECRET");
        requireConfigured(props.getCallbackUrl(), "ums.security.sso.callback-url / UMS_SECURITY_SSO_CALLBACKURL");

        // 1. 授权码换 access_token（文档 2.2 节）
        JsonNode tokenResp = exchangeForAccessToken(code);
        String accessToken = extractField(tokenResp, "access_token", "accessToken", "token");
        if (accessToken == null || accessToken.isBlank()) {
            log.error("token 端点未返回 access_token: {}", safeBrief(tokenResp));
            throw new BadCredentialsException("IAM token 端点响应异常");
        }
        log.info("SSO token 获取成功: token_type={}, expires_in={}, access_token={}",
                extractField(tokenResp, "token_type", "tokenType"),
                extractField(tokenResp, "expires_in", "expiresIn"),
                brief(accessToken));

        // 2. 拿到 access_token 即视为登录成功（getUserInfo 已下线，token 响应不含用户字段；
        //    待办/消息链接携带的 target_user_id 即 IAM 用户标识，缺省落共享联调账号）
        boolean hasTarget = targetUserId != null && !targetUserId.isBlank();
        String externalId = hasTarget ? targetUserId : "iam_sso_user";
        String displayName = hasTarget ? targetUserId : "IAM 用户";
        log.info("SSO token 获取成功即登录成功: externalId={}", externalId);

        // 3. 查/建本地账户 + 状态检查
        CpUser user = userStore.findOrCreate(externalId, externalId, displayName, null,
                List.of(), props.isAutoProvision(), props.getDefaultRoles());
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BadCredentialsException("用户已禁用");
        }

        log.info("SSO login success: externalId={} username={}", externalId, user.getUsername());
        return new UserPrincipal(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                userStore.parseRoles(user.getRoles()),
                user.getProvider(),
                user.getExternalId());
    }

    /**
     * 联调旁路登录（仅 debug-bypass=true 时由回调调用）：
     * 跳过 IAM 交互，直接以固定测试账号查/建 cp_user 并签发会话。
     */
    public UserPrincipal debugLogin() {
        CpUser user = userStore.findOrCreate("DEBUG_TEST_USER", "debug_test", "联调测试用户",
                null, List.of("USER", "ADMIN"), true, props.getDefaultRoles());
        log.warn("SSO 联调旁路登录（未经过 IAM 校验）: username={}", user.getUsername());
        return new UserPrincipal(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                userStore.parseRoles(user.getRoles()),
                user.getProvider(),
                user.getExternalId());
    }

    /**
     * 步骤 1：POST token_url 换 access_token（文档 2.2 节）。
     * 表单固定携带 grant_type / code / redirect_uri / client_id / client_secret
     * （文档要求凭证放表单，无 BASIC 头方式）；JIP 网关鉴权头按配置可选透传。
     */
    private JsonNode exchangeForAccessToken(String code) {
        String form = new StringBuilder("grant_type=authorization_code")
                .append("&code=").append(urlEncode(code))
                .append("&redirect_uri=").append(urlEncode(orEmpty(props.getCallbackUrl())))
                .append("&client_id=").append(urlEncode(orEmpty(props.getClientId())))
                .append("&client_secret=").append(urlEncode(orEmpty(props.getClientSecret())))
                .toString();

        log.info("SSO token 端点请求: url={}, form={}", props.getTokenUrl(), maskForm(form));

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(props.getTokenUrl()))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json");
        applyGatewayAuth(rb, "token 端点");

        HttpRequest request = rb
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        return executeForJson(request, "token 端点");
    }

    /**
     * JIP 网关鉴权（《晶科能源JIP-接口鉴权说明》第 2 节）：
     * <ul>
     * <li>JWT —— 内网推荐方式：每次请求动态生成网关 JWT 放入 Authorization: Bearer 头
     *     （iss=租户 jwtKey，iat=now，nbf=now-5min，exp=now+5min，HMAC256 签名）</li>
     * <li>NONE —— 不做动态生成：gateway-authorization 静态透传
     *     （KeyAuth-Header，或手工验证用的 HMAC 签名串 + date 头）</li>
     * </ul>
     */
    private void applyGatewayAuth(HttpRequest.Builder builder, String endpointName) {
        if (props.getGatewayAuthMode() == SsoProperties.GatewayAuthMode.JWT) {
            String jwt = generateGatewayJwt();
            log.info("SSO 网关鉴权[{}]: JWT 模式, iss={}, Authorization: Bearer {}",
                    endpointName, props.getGatewayUsername(), brief(jwt));
            builder.header("Authorization", "Bearer " + jwt);
            return;
        }
        String auth = props.getGatewayAuthorization();
        if (auth != null && !auth.isBlank()) {
            log.info("SSO 网关鉴权[{}]: 静态透传 Authorization={}", endpointName, brief(auth));
            builder.header("Authorization", auth.trim());
        }
        String date = props.getGatewayDateHeader();
        if (date != null && !date.isBlank()) {
            log.info("SSO 网关鉴权[{}]: 附带 date 头={}", endpointName, date.trim());
            builder.header("date", date.trim());
        }
        if ((auth == null || auth.isBlank()) && (date == null || date.isBlank())) {
            log.info("SSO 网关鉴权[{}]: 未配置（NONE 且无静态头），请求不携带网关鉴权头", endpointName);
        }
    }

    /**
     * 动态生成 JIP 网关 JWT（鉴权说明 2.2.2 节示例）：
     * iss=租户 jwtKey，iat=当前时间，nbf=当前时间-5min，exp=当前时间+5min，HMAC256(secret)。
     * token 有效期仅 5 分钟且网关校验 nbf，故每次请求都重新生成，不做缓存。
     */
    private String generateGatewayJwt() {
        String jwtKey = props.getGatewayUsername();
        String secret = props.getGatewaySecret();
        if (jwtKey == null || jwtKey.isBlank() || secret == null || secret.isBlank()) {
            throw new BadCredentialsException(
                    "网关 JWT 鉴权配置缺失：需设置 gateway-username / gateway-secret"
                            + "（环境变量 UMS_SECURITY_SSO_GATEWAYUSERNAME / UMS_SECURITY_SSO_GATEWAYSECRET）");
        }
        java.util.Date now = new java.util.Date();
        long fiveMinutes = Duration.ofMinutes(5).toMillis();
        return JWT.create()
                .withIssuer(jwtKey)
                .withIssuedAt(now)
                .withNotBefore(new java.util.Date(now.getTime() - fiveMinutes))
                .withExpiresAt(new java.util.Date(now.getTime() + fiveMinutes))
                .sign(Algorithm.HMAC256(secret));
    }

    /** 执行 HTTP 请求并解析 JSON；非 2xx 抛出带状态码的认证异常 */
    private JsonNode executeForJson(HttpRequest request, String endpointName) {
        String url = request.uri().toString();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("SSO {} 调用失败: url={}, status={}, body={}",
                        endpointName, url, response.statusCode(), response.body());
                throw new BadCredentialsException("IAM " + endpointName + " 调用失败: HTTP "
                        + response.statusCode());
            }
            // 成功也打印完整返回报文（脱敏），联调时核对 IAM/网关实际响应结构
            log.info("SSO {} 响应: url={}, status={}, body={}",
                    endpointName, url, response.statusCode(), maskSensitive(response.body()));
            return objectMapper.readTree(response.body());
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.error("SSO {} 调用异常: url={}", endpointName, url, e);
            throw new BadCredentialsException("IAM " + endpointName + " 调用异常: " + e.getMessage());
        }
    }

    /** 从 JSON 按优先级提取字段值 */
    private String extractField(JsonNode json, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode node = json.path(field);
            if (!node.isMissingNode() && !node.isNull() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 配置项缺失时给出可定位的报错（联调排错指南 6.2 第 4/5 步） */
    private void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BadCredentialsException("单点登录配置缺失：" + name);
        }
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 日志脱敏：只保留节点字段名，不打印完整用户信息 */
    private String safeBrief(JsonNode json) {
        try {
            List<String> fields = new java.util.ArrayList<>();
            json.fieldNames().forEachRemaining(fields::add);
            return "fields=" + fields;
        } catch (Exception e) {
            return "(unparseable)";
        }
    }

    /** 掩去 JSON 报文中 token/secret 类字段的值，仅保留前 6 位便于比对 */
    private String maskSensitive(String body) {
        if (body == null) {
            return "";
        }
        return body.replaceAll(
                "(\"(?:access_token|accessToken|client_secret|secret|token)\"\\s*:\\s*\")([^\"]{0,6})[^\"]*(\")",
                "$1$2***$3");
    }

    /** 掩去表单串中 client_secret 的值 */
    private String maskForm(String form) {
        return form.replaceAll("(client_secret=)([^&]{0,6})[^&]*", "$1$2***");
    }

    /** 敏感值摘要：保留前 6 位 + ***，空值显式标注 */
    private String brief(String secret) {
        if (secret == null || secret.isBlank()) {
            return "(empty)";
        }
        return secret.length() <= 6 ? "***" : secret.substring(0, 6) + "***";
    }
}
