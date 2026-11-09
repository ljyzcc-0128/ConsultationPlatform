package com.example.ums.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议实现（/chat/completions）。
 * 覆盖 GLM 公网 API、OpenAI、公司 AI 网关、内网 vLLM 等同协议服务，
 * 仅通过 ums.llm 配置切换端点与模型，调用方无感知。
 */
@Component
@EnableConfigurationProperties(LlmProperties.class)
@RequiredArgsConstructor
public class OpenAiCompatAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatAdapter.class);

    /** 结构化输出的追加指令：仅输出 JSON */
    private static final String JSON_ONLY_SUFFIX =
            "\n\n【输出要求】仅输出一个合法的 JSON 对象，不要输出任何解释、前后缀或 Markdown 代码围栏。";

    private final LlmProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, LlmOptions.defaults());
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, LlmOptions options) {
        requireConfigured();

        String model = options.getModel() != null && !options.getModel().isBlank()
                ? options.getModel()
                : props.getModel();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)));
        if (options.getTemperature() != null) {
            body.put("temperature", options.getTemperature());
        }
        if (options.getMaxTokens() != null) {
            body.put("max_tokens", options.getMaxTokens());
        }

        JsonNode resp = postChatCompletions(body);
        String content = resp.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM 返回内容为空（model=" + model + "）");
        }
        logUsage(model, resp);
        return content;
    }

    @Override
    public <T> T structured(String systemPrompt, String userPrompt, Class<T> type) {
        String raw = chat(systemPrompt, userPrompt + JSON_ONLY_SUFFIX,
                LlmOptions.builder().temperature(0.1).build());
        String json = stripCodeFence(raw.trim());
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("LLM 结构化输出解析失败: type={} raw={}", type.getSimpleName(), raw);
            throw new IllegalStateException("LLM 结构化输出解析失败（"
                    + type.getSimpleName() + "），原始输出已记录日志", e);
        }
    }

    /** 调用 /chat/completions 并返回响应 JSON；非 2xx 报错并携带服务端信息。 */
    private JsonNode postChatCompletions(Map<String, Object> body) {
        try {
            String payload = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint()))
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("LLM 调用失败 status={} body={}", response.statusCode(), response.body());
                throw new IllegalStateException("LLM 调用失败: HTTP " + response.statusCode()
                        + "（model=" + body.get("model") + "），详情见日志");
            }
            return objectMapper.readTree(response.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM 调用异常", e);
            throw new IllegalStateException("LLM 调用异常: " + e.getMessage(), e);
        }
    }

    /** base-url 已含 /chat/completions 时直接使用，否则自动追加（兼容 /v1、/v4 等写法）。 */
    private String endpoint() {
        String base = props.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    /** 记录 token 用量（响应含 usage 时），供成本统计。 */
    private void logUsage(String model, JsonNode resp) {
        JsonNode usage = resp.path("usage");
        if (!usage.isMissingNode() && !usage.isNull()) {
            log.info("LLM 调用完成: model={} prompt_tokens={} completion_tokens={}",
                    model, usage.path("prompt_tokens").asLong(-1), usage.path("completion_tokens").asLong(-1));
        }
    }

    /** 剥离 ```json ... ``` 代码围栏（部分模型即使有指令也会输出围栏）。 */
    private String stripCodeFence(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }

    private void requireConfigured() {
        if (!props.isConfigured()) {
            throw new IllegalStateException("LLM 未配置：请设置 UMS_LLM_BASE_URL / UMS_LLM_API_KEY / UMS_LLM_MODEL"
                    + "（或 application.yml 的 ums.llm 段）");
        }
    }
}
