package com.example.ums.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 接入配置（OpenAI 兼容协议）。
 *
 * <p>api-key 只走环境变量 UMS_LLM_API_KEY，不进代码与仓库；
 * base-url 与 model 按所选服务商配置，缺一不可，未配置时调用抛出明确错误。
 */
@ConfigurationProperties(prefix = "ums.llm")
public class LlmProperties {

    /** OpenAI 兼容服务地址（不含 /chat/completions），如 GLM: https://open.bigmodel.cn/api/paas/v4 */
    private String baseUrl;

    /** API Key（仅环境变量 UMS_LLM_API_KEY） */
    private String apiKey;

    /** 默认模型，如 glm-4-flash / glm-4-plus / gpt-4o-mini */
    private String model;

    /** HTTP 超时秒数 */
    private int timeoutSeconds = 60;

    public boolean isConfigured() {
        return notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
