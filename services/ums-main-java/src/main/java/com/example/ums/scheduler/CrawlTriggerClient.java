package com.example.ums.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ums.admin.mapper.AdminSourceMapper;
import com.example.ums.admin.model.CpSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * 爬虫触发客户端（调度器与人工触发共用）。
 * 连接参数全部来自 CrawlerProperties（ums.crawler.*，可环境变量注入）：
 * base-url 必填；连接/响应超时可配，避免爬虫不可达时请求长时间挂起。
 * 成功（HTTP 2xx）：回写 last_crawl_time（UTC）+ crawl_status=Success；
 * 失败：只回写 crawl_status=Failed（数据文档口径：失败不覆盖 last_crawl_time），
 * RestClientException 上抛由调用方决定处理（人工→502，调度→WARN 跳过）。
 */
@Component
public class CrawlTriggerClient {

    private static final Logger log = LoggerFactory.getLogger(CrawlTriggerClient.class);

    private final AdminSourceMapper sourceMapper;
    private final CrawlerProperties props;
    private final RestClient restClient;

    public CrawlTriggerClient(AdminSourceMapper sourceMapper, CrawlerProperties props) {
        this.sourceMapper = sourceMapper;
        this.props = props;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutSeconds() * 1000);
        factory.setReadTimeout(props.getResponseTimeoutSeconds() * 1000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 触发指定信源抓取并回写调度状态，返回爬虫响应体。 */
    public String triggerAndRecord(CpSource source, int limit) {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("未配置爬虫地址 ums.crawler.base-url（环境变量 UMS_CRAWLER_BASEURL）");
        }
        String response;
        try {
            // 爬虫响应头不规范（Content-Type: application/octet-stream 且曾出现流读取异常），
            // 用 exchange 绕过 message converter 直接读原始流，完整记录响应便于定位
            response = restClient.post()
                    .uri(base + "/internal/trigger/{code}", source.getSourceId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("limit", Math.max(1, limit)))
                    .exchange((req, resp) -> {
                        String bodyText = new String(resp.getBody().readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8);
                        log.info("爬虫原始响应 source={} status={} contentType={} body={}",
                                source.getSourceId(), resp.getStatusCode(),
                                resp.getHeaders().getContentType(), bodyText);
                        if (resp.getStatusCode().isError()) {
                            throw new RestClientException(
                                    "爬虫返回错误 status=" + resp.getStatusCode() + " body=" + bodyText);
                        }
                        return bodyText;
                    });
        } catch (RestClientException e) {
            updateStatus(source.getSourceId(), "Failed", null);
            log.warn("触发抓取失败 source={} : {}", source.getSourceId(), e.getMessage(), e);
            throw e;
        }
        updateStatus(source.getSourceId(), "Success", LocalDateTime.now(ZoneOffset.UTC));
        log.info("触发抓取成功 source={} response={}", source.getSourceId(), response);
        return response;
    }

    private void updateStatus(String sourceId, String status, LocalDateTime lastCrawlTime) {
        LambdaUpdateWrapper<CpSource> wrapper = new LambdaUpdateWrapper<CpSource>()
                .eq(CpSource::getSourceId, sourceId)
                .set(CpSource::getCrawlStatus, status);
        if (lastCrawlTime != null) {
            wrapper.set(CpSource::getLastCrawlTime, lastCrawlTime);
        }
        sourceMapper.update(null, wrapper);
    }
}
