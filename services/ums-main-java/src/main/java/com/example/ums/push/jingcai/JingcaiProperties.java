package com.example.ums.push.jingcai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 晶彩卡片推送配置（底层为钉钉企业内部应用工作通知）。
 *
 * <p>应用信息在晶彩注册（由 IAM 负责人提供 appKey/appSecret/agentId），
 * 服务器出口 IP 需加入钉钉接口白名单（见《IAM-晶彩内单点认证集成文档》）。
 *
 * <p>卡片"打开看板"按钮链接为 IAM SSO 授权地址（在晶彩/钉钉容器内点击自动单点登录），
 * redirect_uri 指向本系统回调并携带 target=/supply-chain 深链参数，登录后直落供应链看板页。
 * app-secret 只走环境变量 UMS_JINGCAI_APP_SECRET，不进代码与仓库。
 */
@ConfigurationProperties(prefix = "ums.jingcai")
public class JingcaiProperties {

    /** 是否启用晶彩卡片推送 */
    private boolean enabled = false;

    /** 晶彩注册应用的钉钉 appKey */
    private String appKey;

    /** 晶彩注册应用的钉钉 appSecret（仅环境变量） */
    private String appSecret;

    /** 钉钉工作通知 agentId */
    private String agentId;

    /** 接收人钉钉 userid 列表（逗号分隔，环境变量配置） */
    private String receivers;

    /** 卡片"打开看板"按钮链接（IAM SSO 授权地址，含 URL 编码的 redirect_uri 与 target 参数） */
    private String cardLink;

    /** 调用钉钉接口超时秒数 */
    private int timeoutSeconds = 10;

    public List<String> receiverList() {
        if (receivers == null || receivers.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(receivers.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // --- getters / setters ---

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getReceivers() { return receivers; }
    public void setReceivers(String receivers) { this.receivers = receivers; }

    public String getCardLink() { return cardLink; }
    public void setCardLink(String cardLink) { this.cardLink = cardLink; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
