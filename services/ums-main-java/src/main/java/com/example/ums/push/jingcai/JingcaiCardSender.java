package com.example.ums.push.jingcai;

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

/**
 * 晶彩卡片发送器（底层：钉钉企业内部应用工作通知）。
 *
 * <p>链路：appKey/appSecret 换 accessToken（缓存至过期前 60s）→
 * 工作通知接口发送 action_card 卡片（markdown 正文 + "打开看板"按钮）。
 *
 * <p>前置（见《IAM-晶彩内单点认证集成文档》）：
 * <ul>
 *   <li>应用已在晶彩注册，appKey/appSecret/agentId 由 IAM 负责人提供</li>
 *   <li>本应用服务器出口 IP 已配置到钉钉接口白名单</li>
 *   <li>接收人为钉钉 userid（工号），配置于 UMS_JINGCAI_RECEIVERS</li>
 * </ul>
 *
 * <p>发送逻辑收敛在本类；如后续拿到晶彩平台专属卡片 API，
 * 只需替换本类实现，摘要构建与调度触发不用动。
 */
@Component
@EnableConfigurationProperties(JingcaiProperties.class)
public class JingcaiCardSender {

    private static final Logger log = LoggerFactory.getLogger(JingcaiCardSender.class);

    /** 钉钉新版 accessToken 端点 */
    private static final String TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
    /** 钉钉企业内部应用工作通知端点（asyncsend_v2） */
    private static final String SEND_URL = "https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2";

    private final JingcaiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** token 缓存：过期前 60s 刷新 */
    private String cachedToken;
    private long tokenExpireAtMillis;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public JingcaiCardSender(JingcaiProperties props) {
        this.props = props;
    }

    /**
     * 发送供应链日报卡片到配置的接收人。
     *
     * @return 钉钉返回的 task_id（errcode=0 时）
     */
    public long send(String title, String markdown) {
        if (!props.isEnabled()) {
            throw new BadCredentialsException("晶彩推送未启用（ums.jingcai.enabled=false）");
        }
        requireConfigured(props.getAppKey(), "ums.jingcai.app-key / UMS_JINGCAI_APP_KEY");
        requireConfigured(props.getAppSecret(), "ums.jingcai.app-secret / UMS_JINGCAI_APP_SECRET");
        requireConfigured(props.getAgentId(), "ums.jingcai.agent-id / UMS_JINGCAI_AGENT_ID");
        requireConfigured(props.getCardLink(), "ums.jingcai.card-link / UMS_JINGCAI_CARD_LINK");

        List<String> receivers = props.receiverList();
        if (receivers.isEmpty()) {
            throw new BadCredentialsException("接收人未配置（UMS_JINGCAI_RECEIVERS）");
        }

        // action_card 消息体：markdown 正文 + 单按钮（打开看板）
        String msg = """
                {"msgtype":"action_card","action_card":{
                  "title":"%s","markdown":%s,
                  "single_title":"打开供应链看板","single_url":"%s"}}
                """.formatted(escape(title), jsonString(markdown), escape(props.getCardLink()));

        String body = "{\"agent_id\":\"" + escape(props.getAgentId())
                + "\",\"userid_list\":\"" + escape(String.join(",", receivers))
                + "\",\"to_all_user\":false,\"msg\":" + msg + "}";

        String token = accessToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SEND_URL + "?access_token=" + token))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        JsonNode resp = executeForJson(request, "工作通知");
        int errcode = resp.path("errcode").asInt(-1);
        if (errcode != 0) {
            String errmsg = resp.path("errmsg").asText("unknown");
            log.error("晶彩卡片发送失败 errcode={} errmsg={}", errcode, errmsg);
            throw new BadCredentialsException("晶彩卡片发送失败：" + errmsg + "（errcode=" + errcode + "）");
        }
        long taskId = resp.path("task_id").asLong();
        log.info("晶彩卡片发送成功: title={} receivers={} task_id={}", title, receivers.size(), taskId);
        return taskId;
    }

    /** 获取 accessToken（带缓存；失败时报错并清缓存） */
    private synchronized String accessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireAtMillis) {
            return cachedToken;
        }
        String body = "{\"appKey\":\"" + escape(props.getAppKey())
                + "\",\"appSecret\":\"" + escape(props.getAppSecret()) + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        JsonNode resp = executeForJson(request, "accessToken");
        String token = resp.path("accessToken").asText(null);
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("钉钉 accessToken 获取失败：请核对 appKey/appSecret");
        }
        long expireIn = resp.path("expireIn").asLong(7200);
        cachedToken = token;
        tokenExpireAtMillis = now + (expireIn - 60) * 1000;
        return token;
    }

    private JsonNode executeForJson(HttpRequest request, String endpointName) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("钉钉 {} 调用失败 status={} body={}",
                        endpointName, response.statusCode(), response.body());
                throw new BadCredentialsException("钉钉 " + endpointName + " 调用失败: HTTP "
                        + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (BadCredentialsException e) {
            // 401 时 token 可能已失效，清缓存让下次重取
            cachedToken = null;
            throw e;
        } catch (Exception e) {
            cachedToken = null;
            log.error("钉钉 {} 调用异常", endpointName, e);
            throw new BadCredentialsException("钉钉 " + endpointName + " 调用异常: " + e.getMessage());
        }
    }

    private void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BadCredentialsException("晶彩推送配置缺失：" + name);
        }
    }

    /** JSON 字符串值转义（title/url 直接拼接场景） */
    private String escape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /** markdown 正文整体序列化为 JSON 字符串（内容含引号/换行也能安全传输） */
    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BadCredentialsException("卡片正文序列化失败");
        }
    }
}
