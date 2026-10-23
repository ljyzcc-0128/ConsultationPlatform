package com.example.ums.security.iam.sso;

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
 * 链路（对应《晶科能源IAM-晶彩内单点认证集成开发文档》第 4 节）：
 * <ol>
 * <li>晶彩容器内打开应用 → IAM 授权端点静默下发 code，重定向回 /api/auth/sso/callback</li>
 * <li>本类用 code + client_id + client_secret 向 token_url 换 access_token</li>
 * <li>携带 access_token 请求 userinfo_url 获取用户唯一标识</li>
 * <li>（防越权）如回调携带 target_user_id，必须与 IAM 返回身份一致，否则拒绝</li>
 * <li>查/建本地 cp_user，返回 UserPrincipal，由 Controller 签发本系统 JWT</li>
 * </ol>
 *
 * <p>
 * IAM 各端点返回结构可能有差异，字段提取按优先级兼容常见命名；
 * 联调时若字段对不上，参考 6.2 节排错指南核对 IAM 后台配置。
 */
@Service
@EnableConfigurationProperties(SsoProperties.class)
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

    public SsoService(SsoProperties props, IamUserStore userStore) {
        this.props = props;
        this.userStore = userStore;
    }

    /**
     * 单点登录主流程：授权码 → access_token → 用户信息 → 本地账户。
     *
     * @param code         IAM 重定向回的授权码
     * @param targetUserId 待办/消息链接携带的目标用户标识，可为空；非空时必须一致
     */
    public UserPrincipal ssoLogin(String code, String targetUserId) {
        if (!props.isEnabled()) {
            throw new BadCredentialsException("单点登录未启用");
        }
        if (code == null || code.isBlank()) {
            throw new BadCredentialsException("缺少授权码（code）");
        }
        requireConfigured(props.getTokenUrl(), "ums.security.sso.token-url / UMS_SSO_TOKEN_URL");
        requireConfigured(props.getUserinfoUrl(), "ums.security.sso.userinfo-url / UMS_SSO_USERINFO_URL");
        requireConfigured(props.getClientId(), "ums.security.sso.client-id / UMS_SSO_CLIENT_ID");
        requireConfigured(props.getClientSecret(), "ums.security.sso.client-secret / UMS_SSO_CLIENT_SECRET");
        requireConfigured(props.getCallbackUrl(), "ums.security.sso.callback-url / UMS_SSO_CALLBACK_URL");

        // 1. 授权码换 access_token
        String accessToken = exchangeForAccessToken(code);

        // 2. 获取用户信息
        JsonNode info = fetchUserinfo(accessToken);

        String externalId = extractField(info, "externalId", "user_id", "userId", "sub", "employeeId", "id");
        String username = extractField(info, "username", "user_name", "login", "account");
        String displayName = extractField(info, "displayName", "name", "realName", "cn");
        String email = extractField(info, "email", "mail");
        List<String> roles = extractRoles(info);

        if (externalId == null || externalId.isBlank()) {
            externalId = username;
        }
        if (externalId == null || externalId.isBlank()) {
            log.error("IAM userinfo 未返回用户唯一标识: {}", safeBrief(info));
            throw new BadCredentialsException("IAM 未返回用户唯一标识，无法完成登录");
        }
        if (username == null || username.isBlank()) {
            username = externalId;
        }

        // 3. 一致性校验（防越权）：从晶彩待办/消息通知跳入时，链接携带的
        // target_user_id 必须与 IAM 返回身份一致，不一致即拒绝访问
        if (targetUserId != null && !targetUserId.isBlank()) {
            boolean match = targetUserId.equals(externalId)
                    || targetUserId.equals(username)
                    || (email != null && targetUserId.equalsIgnoreCase(email));
            if (!match) {
                log.warn("SSO 用户一致性校验失败: target_user_id={} iam_identity={}",
                        targetUserId, externalId);
                throw new BadCredentialsException("用户身份不一致，拒绝访问");
            }
        }

        // 4. 查/建本地账户 + 状态检查
        CpUser user = userStore.findOrCreate(externalId, username, displayName, email, roles,
                props.isAutoProvision(), props.getDefaultRoles());
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BadCredentialsException("用户已禁用");
        }

        log.info("SSO login success: externalId={} username={}", externalId, username);
        return new UserPrincipal(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                userStore.parseRoles(user.getRoles()),
                user.getProvider(),
                user.getExternalId());
    }

    /**
     * 步骤 2：POST token_url 换 access_token（标准 OAuth2 授权码换 token）。
     * 凭证传递方式按 token-auth-mode 配置：FORM 放表单 / BASIC 放请求头。
     */
    private String exchangeForAccessToken(String code) {
        StringBuilder form = new StringBuilder("grant_type=authorization_code")
                .append("&code=").append(urlEncode(code))
                .append("&redirect_uri=").append(urlEncode(orEmpty(props.getCallbackUrl())));

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(props.getTokenUrl()))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json");

        if (props.getTokenAuthMode() == SsoProperties.TokenAuthMode.BASIC) {
            // RFC 6749 §2.3.1：client 凭证走 Authorization: Basic（base64(client_id:client_secret)）
            String basic = java.util.Base64.getEncoder()
                    .encodeToString((props.getClientId() + ":" + props.getClientSecret())
                            .getBytes(StandardCharsets.UTF_8));
            rb.header("Authorization", "Basic " + basic);
        } else {
            form.append("&client_id=").append(urlEncode(orEmpty(props.getClientId())))
                 .append("&client_secret=").append(urlEncode(orEmpty(props.getClientSecret())));
        }

        HttpRequest request = rb
                .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                .build();

        JsonNode body = executeForJson(request, "token 端点");
        String accessToken = extractField(body, "access_token", "accessToken", "token");
        if (accessToken == null || accessToken.isBlank()) {
            log.error("token 端点未返回 access_token: {}", safeBrief(body));
            throw new BadCredentialsException("IAM token 端点响应异常");
        }
        return accessToken;
    }

    /** 步骤 3：GET userinfo_url（Bearer access_token） */
    private JsonNode fetchUserinfo(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getUserinfoUrl()))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        return executeForJson(request, "userinfo 端点");
    }

    /** 执行 HTTP 请求并解析 JSON；非 2xx 抛出带状态码的认证异常 */
    private JsonNode executeForJson(HttpRequest request, String endpointName) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("SSO {} 调用失败 status={} body={}",
                        endpointName, response.statusCode(), response.body());
                throw new BadCredentialsException("IAM " + endpointName + " 调用失败: HTTP "
                        + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            // 兼容 {"code":0,"data":{...}} / {"result":{...}} 包装结构
            if (json.isObject()) {
                for (String wrapper : List.of("data", "result")) {
                    JsonNode inner = json.path(wrapper);
                    if (inner.isObject() && hasAnyField(inner, "id", "sub", "userId", "user_id",
                            "username", "employeeId", "externalId")) {
                        return inner;
                    }
                }
            }
            return json;
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.error("SSO {} 调用异常", endpointName, e);
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

    private boolean hasAnyField(JsonNode json, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode node = json.path(field);
            if (!node.isMissingNode() && !node.isNull() && !node.asText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** 提取角色列表；无则返回空（由 IamUserStore 回退默认角色） */
    private List<String> extractRoles(JsonNode json) {
        JsonNode rolesNode = json.path("roles");
        if (rolesNode.isArray() && rolesNode.size() > 0) {
            try {
                return objectMapper.treeToValue(rolesNode,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {
                        });
            } catch (Exception e) {
                log.warn("解析 IAM roles 失败", e);
            }
        }
        return List.of();
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
}
