package com.example.ums.security.iam.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 晶彩单点登录（SSO）配置属性（OAuth 2.0 授权码模式）。
 *
 * <p>接入流程参见《晶科能源IAM-OAuth2.0单点认证集成文档》：
 * <ol>
 *   <li>向 IAM 负责人申请 client_id / client_secret，订阅 JIP 网关的
 *       getToken 接口（注意网关鉴权方式：HMAC / JWT / KeyAuth）</li>
 *   <li>晶彩后台配置出口 IP 白名单与接口权限，工作台地址填 IAM 授权链接</li>
 *   <li>本系统提供回调接口 /api/auth/sso/callback 接收授权码</li>
 * </ol>
 *
 * <p>启用方式：设置 ums.security.sso.enabled=true，并通过环境变量注入凭证：
 * client_secret 只走环境变量 UMS_SECURITY_SSO_CLIENTSECRET，不进代码与仓库。
 */
@ConfigurationProperties(prefix = "ums.security.sso")
public class SsoProperties {


    /** 是否启用晶彩单点登录 */
    private boolean enabled = false;

    /** IAM 换取 Token 端点（POST 授权码换 access_token，凭证放表单，见文档 2.2 节） */
    private String tokenUrl;

    /**
     * IAM 获取登录用户信息端点（POST 表单 access_token 换 uid，见文档 2.3 节）。
     * token 响应不含用户字段，必须调本接口；接口编码随 JIP 网关鉴权方式而变
     * （IAM4_OAuth_getUserInfo_XXX，XXX = HMAC / JWT / KeyAuth，以平台实际订阅为准）。
     */
    private String userinfoUrl;

    /**
     * IAM 全局注销端点（文档 2.4 节，GET 浏览器重定向）：IAM 会注销全局登录会话。
     * 本系统前端先清本地 JWT 再跳转本系统 /api/auth/sso/logout，由后端 302 到本端点
     * 并携带 redirectUrl 回登录页。
     * 测试环境 https://iamtest.jinkosolar.com/portal/sso/logout.html，
     * 正式环境 https://iam.jinkosolar.com/portal/sso/logout.html；留空则直接回跳前端。
     */
    private String logoutUrl;

    /**
     * JIP 网关鉴权方式（《晶科能源JIP-接口鉴权说明》第 2 节）：
     * <ul>
     * <li>NONE（默认）—— 不走动态鉴权：用 gateway-authorization 静态透传
     *     （KeyAuth-Header 方式，或把 Key 拼进接口 URL 的 KeyAuth-Params 方式）</li>
     * <li>JWT —— 每次请求动态生成网关 JWT（iss=租户标识，HMAC256 签名，
     *     5 分钟有效，Authorization: Bearer 头），需要 gateway-username / gateway-secret；
     *     内网调用推荐此方式（JIP 规范：外网 HMAC、内网 JWT）</li>
     * </ul>
     */
    public enum GatewayAuthMode { NONE, JWT }

    /** 网关鉴权方式 */
    private GatewayAuthMode gatewayAuthMode = GatewayAuthMode.NONE;

    /**
     * IAM 授权端点（IAM4_OAuth_getCode，浏览器整页跳转用）。
     * 前端登录页通过 GET /api/auth/sso/config 动态读取本配置，
     * 换环境只改后端配置/环境变量（UMS_SECURITY_SSO_AUTHORIZEURL），无需重建前端。
     */
    private String authorizeUrl;

    /**
     * 静态鉴权头（仅 NONE 模式生效）：原样放进 Authorization 头。
     * KeyAuth-Header 填分配的 Key；HMAC 方式填完整签名串并配套 gateway-date-header
     * （注意签名 5 分钟有效，静态配置只适合手工验证）。
     */
    private String gatewayAuthorization;

    /** HMAC 数字签名鉴权时配套的 date 头（时间戳或 GMT 时间，精确到秒；仅 NONE 模式生效） */
    private String gatewayDateHeader;

    /** JWT 模式：租户标识（JWT iss 字段，即 JIP 平台租户 jwtKey） */
    private String gatewayUsername;

    /** JWT 模式：租户密钥（HMAC256 签名密钥，仅环境变量 UMS_SECURITY_SSO_GATEWAYSECRET 注入） */
    private String gatewaySecret;

    /** OAuth 2.0 应用编码（IAM 分配） */
    private String clientId;

    /** OAuth 2.0 应用密钥（仅环境变量 UMS_SECURITY_SSO_CLIENTSECRET 注入） */
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
     * 必须显式配置（yml 或环境变量 UMS_SECURITY_SSO_FRONTENDLANDINGURL），不写死默认值。
     */
    private String frontendLandingUrl;

    /** 调用 IAM 接口超时秒数 */
    private int timeoutSeconds = 10;

    /** 首次单点登录是否自动创建 cp_user */
    private boolean autoProvision = true;

    /**
     * 联调旁路开关（仅测试环境使用）：true 时回调跳过 IAM 全部校验
     * （不换 token、不调 userinfo、不做一致性校验），
     * 直接以固定测试账号 debug_test 签发 JWT 进入业务页面。
     * 生产环境必须为 false（默认值），或用环境变量 UMS_SECURITY_SSO_DEBUGBYPASS 覆盖。
     */
    private boolean debugBypass = false;

    /** 新建用户的默认角色 */
    private List<String> defaultRoles = List.of("USER");

    // --- getters / setters ---

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

    public String getUserinfoUrl() { return userinfoUrl; }
    public void setUserinfoUrl(String userinfoUrl) { this.userinfoUrl = userinfoUrl; }

    public String getLogoutUrl() { return logoutUrl; }
    public void setLogoutUrl(String logoutUrl) { this.logoutUrl = logoutUrl; }

    public GatewayAuthMode getGatewayAuthMode() { return gatewayAuthMode; }
    public void setGatewayAuthMode(GatewayAuthMode gatewayAuthMode) { this.gatewayAuthMode = gatewayAuthMode; }

    public String getAuthorizeUrl() { return authorizeUrl; }
    public void setAuthorizeUrl(String authorizeUrl) { this.authorizeUrl = authorizeUrl; }

    public String getGatewayUsername() { return gatewayUsername; }
    public void setGatewayUsername(String gatewayUsername) { this.gatewayUsername = gatewayUsername; }

    public String getGatewaySecret() { return gatewaySecret; }
    public void setGatewaySecret(String gatewaySecret) { this.gatewaySecret = gatewaySecret; }

    public String getGatewayAuthorization() { return gatewayAuthorization; }
    public void setGatewayAuthorization(String gatewayAuthorization) { this.gatewayAuthorization = gatewayAuthorization; }

    public String getGatewayDateHeader() { return gatewayDateHeader; }
    public void setGatewayDateHeader(String gatewayDateHeader) { this.gatewayDateHeader = gatewayDateHeader; }

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

    public boolean isDebugBypass() { return debugBypass; }
    public void setDebugBypass(boolean debugBypass) { this.debugBypass = debugBypass; }

    public List<String> getDefaultRoles() { return defaultRoles; }
    public void setDefaultRoles(List<String> defaultRoles) { this.defaultRoles = defaultRoles; }
}
