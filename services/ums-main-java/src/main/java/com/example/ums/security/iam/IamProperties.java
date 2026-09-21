package com.example.ums.security.iam;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * IAM（身份与访问管理）配置属性。
 *
 * <p>与 SSO/OAuth2 的浏览器跳转模式不同，IAM 通常通过 API 调用验证凭据：
 * <ol>
 *   <li>用户在前端输入用户名 + 密码</li>
 *   <li>后端 IamAuthProvider 将凭据 POST 到 IAM 认证 API</li>
 *   <li>IAM 返回用户信息（externalId / displayName / email / roles）</li>
 *   <li>后端查找或自动创建 cp_user，签发自身 JWT</li>
 * </ol>
 *
 * <p>启用方式：设置 ums.security.iam.enabled=true 并填入 IAM API 地址，
 * 前端登录时 provider 传 "IAM"。
 */
@ConfigurationProperties(prefix = "ums.security.iam")
public class IamProperties {

    /** 是否启用 IAM 认证 */
    private boolean enabled = false;

    /** IAM 认证 API 地址（POST 用户名密码，返回用户信息） */
    private String authApiUrl;

    /** 调用 IAM API 的认证方式 */
    private AuthMode authMode = AuthMode.API_KEY;

    /** API Key（当 authMode=API_KEY 时，放在请求头 X-API-Key） */
    private String apiKey;

    /** Bearer Token（当 authMode=BEARER 时，放在 Authorization 头） */
    private String bearerToken;

    /** 认证超时秒数 */
    private int timeoutSeconds = 10;

    /** 首次 IAM 登录是否自动创建 cp_user */
    private boolean autoProvision = true;

    /** 新建 IAM 用户的默认角色 */
    private List<String> defaultRoles = List.of("USER");

    /**
     * IAM API 调用认证方式。
     */
    public enum AuthMode {
        /** 通过 X-API-Key 请求头 */
        API_KEY,
        /** 通过 Authorization: Bearer 请求头 */
        BEARER,
        /** 无需额外认证（内网直连） */
        NONE
    }

    // --- getters / setters ---

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAuthApiUrl() { return authApiUrl; }
    public void setAuthApiUrl(String authApiUrl) { this.authApiUrl = authApiUrl; }

    public AuthMode getAuthMode() { return authMode; }
    public void setAuthMode(AuthMode authMode) { this.authMode = authMode; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBearerToken() { return bearerToken; }
    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public boolean isAutoProvision() { return autoProvision; }
    public void setAutoProvision(boolean autoProvision) { this.autoProvision = autoProvision; }

    public List<String> getDefaultRoles() { return defaultRoles; }
    public void setDefaultRoles(List<String> defaultRoles) { this.defaultRoles = defaultRoles; }
}
