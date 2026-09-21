package com.example.ums.security.iam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.security.AuthenticationProvider;
import com.example.ums.security.AuthenticationRequest;
import com.example.ums.security.UserPrincipal;
import com.example.ums.user.mapper.UserMapper;
import com.example.ums.user.model.CpUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * IAM 认证提供方（SPI 实现，providerId = "IAM"）。
 *
 * <p>认证流程（API 调用模式，非 OAuth2 浏览器跳转）：
 * <ol>
 *   <li>用户在前端输入用户名 + 密码</li>
 *   <li>AuthController 调用 AuthenticationService.login(provider="IAM", username, credential=密码)</li>
 *   <li>本类 authenticate() 将凭据 POST 到 IAM 认证 API</li>
 *   <li>IAM 验证成功返回用户信息 → 查/建 cp_user → 返回 UserPrincipal</li>
 *   <li>AuthenticationServiceImpl 用 UserPrincipal 签发 JWT</li>
 * </ol>
 *
 * <p>IAM API 约定：
 * <pre>
 * POST {auth-api-url}
 * Headers:
 *   X-API-Key: {apiKey}          （当 authMode=API_KEY）
 *   Authorization: Bearer {token}（当 authMode=BEARER）
 *   Content-Type: application/json
 * Body:
 *   {"username": "xxx", "password": "xxx"}
 *
 * Response 200:
 *   {"externalId": "EMP001", "username": "zhangsan", "displayName": "张三",
 *    "email": "zhangsan@company.com", "roles": ["USER"], "status": "ACTIVE"}
 *
 * Response 401:
 *   {"error": "用户名或密码错误"}
 * </pre>
 *
 * <p>IAM 接入时需补全的 TODO 点：
 * <ul>
 *   <li>TODO: IAM 返回的字段映射（不同 IAM 返回 JSON 结构不同，需调整 extractField 的字段名）</li>
 *   <li>TODO: 密码加密传输（如 IAM 要求 SM2/RSA 加密，需在 POST 前加密 password）</li>
 *   <li>TODO: 用户角色映射（IAM 返回的角色码可能与本系统不一致，需做映射表）</li>
 *   <li>TODO: 连接池/超时调优（生产环境建议配置 HttpClient 连接池参数）</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(IamProperties.class)
public class IamAuthProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(IamAuthProvider.class);

    private final IamProperties iamProperties;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public IamAuthProvider(IamProperties iamProperties, UserMapper userMapper) {
        this.iamProperties = iamProperties;
        this.userMapper = userMapper;
    }

    @Override
    public String providerId() {
        return "IAM";
    }

    @Override
    public UserPrincipal authenticate(AuthenticationRequest req) {
        if (!iamProperties.isEnabled()) {
            throw new BadCredentialsException("IAM 认证未启用，请在配置中设置 ums.security.iam.enabled=true");
        }

        String username = req.username();
        String password = req.credential();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BadCredentialsException("用户名和密码不能为空");
        }

        // 1. 调用 IAM 认证 API
        JsonNode iamUser = authenticateViaIam(username, password);

        // 2. 提取用户信息
        String externalId = extractField(iamUser, "externalId", "sub", "id", "employeeId");
        String iamUsername = extractField(iamUser, "username", "login", "account");
        String displayName = extractField(iamUser, "displayName", "name", "realName", "cn");
        String email = extractField(iamUser, "email", "mail");
        String status = extractField(iamUser, "status", "state");
        List<String> roles = extractRoles(iamUser);

        if (externalId == null || externalId.isBlank()) {
            externalId = iamUsername != null ? iamUsername : username;
        }

        // 3. 查找或自动创建 cp_user
        CpUser user = findOrCreateUser(externalId, iamUsername != null ? iamUsername : username,
                displayName, email, roles);

        // 4. 检查用户状态（IAM 返回 DISABLED 或本地标记 DISABLED）
        String effectiveStatus = status != null ? status : user.getStatus();
        if (!"ACTIVE".equals(effectiveStatus)) {
            throw new BadCredentialsException("用户已禁用");
        }

        return new UserPrincipal(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                parseRoles(user.getRoles()),
                user.getProvider(),
                user.getExternalId()
        );
    }

    /**
     * 调用 IAM 认证 API，验证用户名密码。
     */
    private JsonNode authenticateViaIam(String username, String password) {
        String body;
        try {
            body = objectMapper.writeValueAsString(
                    java.util.Map.of("username", username, "password", password));
        } catch (Exception e) {
            throw new BadCredentialsException("请求序列化失败");
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(iamProperties.getAuthApiUrl()))
                .timeout(Duration.ofSeconds(iamProperties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        // 按配置添加认证头
        switch (iamProperties.getAuthMode()) {
            case API_KEY -> reqBuilder.header("X-API-Key", iamProperties.getApiKey());
            case BEARER -> reqBuilder.header("Authorization", "Bearer " + iamProperties.getBearerToken());
            case NONE -> {}
        }

        try {
            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 401) {
                log.info("IAM auth failed (401) for username={}", username);
                throw new BadCredentialsException("用户名或密码错误");
            }
            if (response.statusCode() != 200) {
                log.error("IAM auth unexpected status={} body={}", response.statusCode(), response.body());
                throw new BadCredentialsException("IAM 认证服务异常: HTTP " + response.statusCode());
            }

            return objectMapper.readTree(response.body());
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.error("IAM auth call error for username={}", username, e);
            throw new BadCredentialsException("IAM 认证调用失败: " + e.getMessage());
        }
    }

    /**
     * 从 JSON 中按优先级提取字段值。
     *
     * <p>TODO: 不同 IAM 返回的字段名不同，接入时需根据实际情况调整。
     */
    private String extractField(JsonNode json, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode node = json.path(field);
            if (!node.isMissingNode() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    /**
     * 从 IAM 返回中提取角色列表。
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(JsonNode json) {
        JsonNode rolesNode = json.path("roles");
        if (rolesNode.isArray() && rolesNode.size() > 0) {
            try {
                return objectMapper.treeToValue(rolesNode, List.class);
            } catch (Exception e) {
                log.warn("Failed to parse roles from IAM response", e);
            }
        }
        return iamProperties.getDefaultRoles();
    }

    /**
     * 按 external_id + provider=IAM 查找 cp_user。
     * 若 auto-provision=true 且用户不存在，自动创建。
     */
    private CpUser findOrCreateUser(String externalId, String username,
                                     String displayName, String email, List<String> roles) {
        LambdaQueryWrapper<CpUser> qw = new LambdaQueryWrapper<>();
        qw.eq(CpUser::getExternalId, externalId)
          .eq(CpUser::getProvider, "IAM");
        CpUser user = userMapper.selectOne(qw);

        if (user != null) {
            // 更新用户信息（displayName/email/roles 可能变更）
            boolean changed = false;
            if (displayName != null && !displayName.equals(user.getDisplayName())) {
                user.setDisplayName(displayName);
                changed = true;
            }
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
                changed = true;
            }
            String rolesJson = serializeRoles(roles);
            if (rolesJson != null && !rolesJson.equals(user.getRoles())) {
                user.setRoles(rolesJson);
                changed = true;
            }
            if (changed) {
                userMapper.updateById(user);
                log.info("IAM user info updated: externalId={}", externalId);
            }
            return user;
        }

        if (!iamProperties.isAutoProvision()) {
            throw new BadCredentialsException("IAM 用户未注册，请联系管理员开通账号");
        }

        // 自动创建 cp_user
        user = new CpUser();
        user.setUserId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setRoles(serializeRoles(roles));
        user.setStatus("ACTIVE");
        user.setProvider("IAM");
        user.setExternalId(externalId);
        userMapper.insert(user);
        log.info("IAM auto-provisioned user: externalId={} username={}", externalId, username);
        return user;
    }

    private String serializeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            roles = iamProperties.getDefaultRoles();
        }
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (Exception e) {
            return "[\"USER\"]";
        }
    }

    private List<String> parseRoles(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return List.of("USER");
        }
        try {
            return objectMapper.readValue(rolesJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return List.of("USER");
        }
    }
}
