package com.example.ums.security.iam.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 晶彩单点登录（SSO）配置属性（OAuth 2.0 授权码模式）。
 *
 * <p>接入流程参见《晶科能源IAM-晶彩内单点认证集成开发文档》：
 * <ol>
 *   <li>向 IAM 负责人申请 client_id / client_secret 与 token / userinfo 端点</li>
 *   <li>晶彩后台配置出口 IP 白名单与接口权限，工作台地址填 IAM 授权链接</li>
 *   <li>本系统提供回调接口 /api/auth/sso/callback 接收授权码</li>
 * </ol>
 *
 * <p>启用方式：设置 ums.security.sso.enabled=true，并通过环境变量注入凭证：
 * client_secret 只走环境变量 UMS_SSO_CLIENT_SECRET，不进代码与仓库。
 */
@ConfigurationProperties(prefix = "ums.security.sso")
public class SsoProperties {

    /** 换取 token 时 client 凭证的传递方式 */
    public enum TokenAuthMode { FORM, BASIC }


    /** 是否启用晶彩单点登录 */
    private boolean enabled = false;

    /** IAM 授权端点（晶彩工作台/消息卡片配置的入口链接） */
    private String authorizeUrl = "https://iam.jinkosolar.com/sso/oidc1/dAuthorize";

    /** IAM 换取 Token 端点（POST 授权码换 access_token） */
    private String tokenUrl;

    /**
     * 换取 token 时 client 凭证的传递方式（RFC 6749 §2.3.1 两种合法方式）：
     * FORM  —— client_id/client_secret 放请求表单（默认）；
     * BASIC —— 放 Authorization: Basic base64(client_id:client_secret) 头。
     * 按 IAM 提供的《OAuth2.0 单点认证集成文档》要求选择。
     */
    private TokenAuthMode tokenAuthMode = TokenAuthMode.FORM;

    /** IAM 获取用户信息端点（GET Bearer access_token） */
    private String userinfoUrl;

    /** OAuth 2.0 应用编码（IAM 分配） */
    private String clientId;

    /** OAuth 2.0 应用密钥（仅环境变量 UMS_SSO_CLIENT_SECRET 注入） */
    private String clientSecret;

    /**
     * 本系统回调地址（redirect_uri，与 IAM 登记完全一致）。
     * 例如 https://ums.example.com/api/auth/sso/callback
     */
    private String callbackUrl;

    /**
     * 前端落地页地址：回调完成后 302 跳转至此并携带 token 参数。
     * 例如 http://localhost:5173/sso/landing（开发）
     *      https://ums.example.com/sso/landing（生产）
     */
    private String frontendLandingUrl = "http://localhost:5173/sso/landing";

    /** 调用 IAM 接口超时秒数 */
    private int timeoutSeconds = 10;

    /** 首次单点登录是否自动创建 cp_user */
    private boolean autoProvision = true;

    /** 新建用户的默认角色 */
    private List<String> defaultRoles = List.of("USER");

    // --- getters / setters ---

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAuthorizeUrl() { return authorizeUrl; }
    public void setAuthorizeUrl(String authorizeUrl) { this.authorizeUrl = authorizeUrl; }

    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

    public TokenAuthMode getTokenAuthMode() { return tokenAuthMode; }
    public void setTokenAuthMode(TokenAuthMode tokenAuthMode) { this.tokenAuthMode = tokenAuthMode; }

    public String getUserinfoUrl() { return userinfoUrl; }
    public void setUserinfoUrl(String userinfoUrl) { this.userinfoUrl = userinfoUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getFrontendLandingUrl() { return frontendLandingUrl; }
    public void setFrontendLandingUrl(String frontendLandingUrl) { this.frontendLandingUrl = frontendLandingUrl; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public boolean isAutoProvision() { return autoProvision; }
    public void setAutoProvision(boolean autoProvision) { this.autoProvision = autoProvision; }

    public List<String> getDefaultRoles() { return defaultRoles; }
    public void setDefaultRoles(List<String> defaultRoles) { this.defaultRoles = defaultRoles; }
}
