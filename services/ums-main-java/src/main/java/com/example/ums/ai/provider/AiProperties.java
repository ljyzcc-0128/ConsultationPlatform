package com.example.ums.ai.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 供应商配置（全部环境变量可覆盖，key 不入代码仓库——ADR-04/ADR-10）。
 */
@ConfigurationProperties(prefix = "ums.ai")
public class AiProperties {

    /** 总开关：false 时消费链路直接 SKIPPED */
    private boolean enabled = true;

    /** 智谱 API key；为空视为未接入（SKIPPED 放行，AI-004 不阻断采集） */
    private String apiKey = "";

    /** OpenAI 兼容端点（智谱） */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    private String model = "glm-4-flash";

    private int timeoutSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
