package com.example.ums.ai.provider;

import com.example.ums.ai.AIProcessingService;
import com.example.ums.ai.dto.ClassificationResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 智谱 GLM Provider（OpenAI 兼容 /chat/completions 协议）。
 * key/base_url/model 全部来自环境变量（AiProperties），更换供应商只需新增 Provider 实现类。
 */
@Component
public class GlmProvider implements AIProcessingService {

    private static final Logger log = LoggerFactory.getLogger(GlmProvider.class);

    /** 统一主题字典（与 cp_source.category 同源；一期内置，后续迁入配置） */
    private static final String CATEGORY_DICTIONARY =
            "BESS, Policy, Power Market, Electricity Price, Renewable Energy, Supply Chain, "
            + "Technology, Market Analysis, Company News, Regulation, Safety, Finance";

    private static final String SUMMARIZE_SYSTEM_PROMPT = """
            你是储能行业的资讯分析师。基于用户提供的新闻，输出中文三段式摘要，每段一句话：
            1. 发生了什么（事实概述）；
            2. 对储能行业/BESS 意味着什么；
            3. 对储能系统集成商与设备商（如 Jinko ESS）意味着什么。
            要求：忠实原文不编造；直接输出三段文字，不加序号和小标题。""";

    private static final String CLASSIFY_SYSTEM_PROMPT = """
            你是储能行业的内容分类器。从主题字典中选出与新闻最相关的 1-3 个标签，
            以 JSON 对象输出，格式：{"categories": ["标签1", "标签2"]}。
            主题字典：%s
            只输出 JSON 对象，不要任何其他内容。""".formatted(CATEGORY_DICTIONARY);

    private static final int MAX_INPUT_CHARS = 8000;

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GlmProvider(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String summarize(String text, String targetLanguage) {
        String user = truncate(text);
        ChatResponse resp = chat(SUMMARIZE_SYSTEM_PROMPT, user, null);
        return resp.content().trim();
    }

    @Override
    public ClassificationResult classify(String text) {
        String user = truncate(text);
        ChatResponse resp = chat(CLASSIFY_SYSTEM_PROMPT, user, "json_object");
        List<String> categories = parseCategories(resp.content());
        return new ClassificationResult(categories);
    }

    @Override
    public boolean isConfigured() {
        return props.isEnabled() && props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    @Override
    public String providerName() {
        return "glm";
    }

    @Override
    public String modelName() {
        return props.getModel();
    }

    // ---------------- 内部实现 ----------------

    private String truncate(String text) {
        return text != null && text.length() > MAX_INPUT_CHARS
                ? text.substring(0, MAX_INPUT_CHARS) : text;
    }

    private ChatResponse chat(String systemPrompt, String userPrompt, String responseFormat) {
        try {
            // response_format 必须是对象 {"type":"json_object"}（字符串形式智谱报 400：
            // Cannot construct instance of ResponseFormat）——用 Map 构造，null 时不传该字段
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", props.getModel());
            payload.put("messages", List.of(
                    new Message("system", systemPrompt), new Message("user", userPrompt)));
            payload.put("temperature", 0.3);
            if (responseFormat != null) {
                payload.put("response_format", Map.of("type", responseFormat));
            }
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("GLM API HTTP " + response.statusCode()
                        + ": " + snippet(response.body()));
            }
            ChatResponse parsed = objectMapper.readValue(response.body(), ChatResponse.class);
            if (parsed.content() == null || parsed.content().isBlank()) {
                throw new IllegalStateException("GLM API 返回空内容: " + snippet(response.body()));
            }
            return parsed;
        } catch (IOException e) {
            throw new IllegalStateException("GLM API 调用失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GLM API 调用被中断", e);
        }
    }

    /** 解析 {"categories": [...]}，容错处理模型输出被 markdown 代码块包裹的情况 */
    private List<String> parseCategories(String content) {
        try {
            String json = content.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            JsonNode node = objectMapper.readTree(json);
            List<String> result = new ArrayList<>();
            node.path("categories").forEach(c -> {
                String v = c.asText().trim();
                if (!v.isEmpty()) {
                    result.add(v);
                }
            });
            if (result.isEmpty()) {
                log.warn("分类结果为空: {}", snippet(content));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("分类结果解析失败: " + snippet(content), e);
        }
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    // ---------------- OpenAI 兼容协议 DTO ----------------

    record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {
        String content() {
            return choices == null || choices.isEmpty() ? null : choices.get(0).message().content();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}
}
