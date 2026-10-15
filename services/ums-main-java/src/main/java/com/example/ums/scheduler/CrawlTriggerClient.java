package com.example.ums.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ums.admin.mapper.AdminSourceMapper;
import com.example.ums.admin.model.CpSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * 爬虫触发客户端（调度器与人工触发共用）。
 * 成功（HTTP 2xx）：回写 last_crawl_time（UTC）+ crawl_status=Success；
 * 失败：只回写 crawl_status=Failed（数据文档口径：失败不覆盖 last_crawl_time），
 * RestClientException 上抛由调用方决定处理（人工→502，调度→WARN 跳过）。
 */
@Component
public class CrawlTriggerClient {

    private static final Logger log = LoggerFactory.getLogger(CrawlTriggerClient.class);

    private final AdminSourceMapper sourceMapper;
    private final String crawlerBaseUrl;
    private final RestClient restClient = RestClient.create();

    public CrawlTriggerClient(AdminSourceMapper sourceMapper,
                              @Value("${ums.crawler.base-url:http://localhost:8100}") String crawlerBaseUrl) {
        this.sourceMapper = sourceMapper;
        this.crawlerBaseUrl = crawlerBaseUrl;
    }

    /** 触发指定信源抓取并回写调度状态，返回爬虫响应体。 */
    public String triggerAndRecord(CpSource source, int limit) {
        String response;
        try {
            response = restClient.post()
                    .uri(crawlerBaseUrl + "/internal/trigger/{code}", source.getSourceId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("limit", Math.max(1, limit)))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            updateStatus(source.getSourceId(), "Failed", null);
            log.warn("触发抓取失败 source={} : {}", source.getSourceId(), e.getMessage());
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
