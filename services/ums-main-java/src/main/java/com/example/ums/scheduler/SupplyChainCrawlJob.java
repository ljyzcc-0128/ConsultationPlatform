package com.example.ums.scheduler;

import com.example.ums.scheduler.CrawlerProperties;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 供应链采集定时调度（单一调度系统 XXL-JOB，与资讯链路同一执行器）。
 *
 * 任务名 supplyChainGfexJob / supplyChainSmmJob / supplyChainSci99Job，
 * 建议 cron（由 xxl-job-admin 配置）：
 * - supplyChainGfexJob：0 0 7,18 * * ?（交易日 07:00/18:00，非交易日 Python 侧自动回溯）
 * - supplyChainSmmJob：0 0 8 * * ?（SMM 价格每日发布；周/月度指标由 Python 按区间聚合）
 * - supplyChainSci99Job：0 30 8 * * ?（卓创早间提示每日早间更新，08:30 抓当日）
 * 只负责 HTTP 触发 Python /internal/trigger/supplychain/{gfex|smm|sci99}（采集→发 MQ），
 * 数据加工在 Java 消费者（GfexWbillConsumer/SmmQuoteConsumer/Sci99QuoteConsumer），本类不落库。
 */
@Component
public class SupplyChainCrawlJob {

    private static final Logger log = LoggerFactory.getLogger(SupplyChainCrawlJob.class);

    private final CrawlerProperties props;
    private final RestClient restClient;

    public SupplyChainCrawlJob(CrawlerProperties props) {
        this.props = props;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutSeconds() * 1000);
        factory.setReadTimeout(props.getResponseTimeoutSeconds() * 1000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** XXL-JOB 任务入口：触发广期所仓单采集。任务参数（可选）：交易日 yyyy-MM-dd。 */
    @XxlJob("supplyChainGfexJob")
    public void supplyChainGfexJob() {
        triggerCrawler("/internal/trigger/supplychain/gfex", "供应链仓单采集");
    }

    /** XXL-JOB 任务入口：触发 SMM 报价采集。任务参数（可选）：数据日期 yyyy-MM-dd。 */
    @XxlJob("supplyChainSmmJob")
    public void supplyChainSmmJob() {
        triggerCrawler("/internal/trigger/supplychain/smm", "供应链SMM报价采集");
    }

    /** XXL-JOB 任务入口：触发卓创氮气早间提示采集。任务参数（可选）：数据日期 yyyy-MM-dd。 */
    @XxlJob("supplyChainSci99Job")
    public void supplyChainSci99Job() {
        triggerCrawler("/internal/trigger/supplychain/sci99", "供应链卓创报价采集");
    }

    /** 共用 HTTP 触发：POST 爬虫内部接口（采集→发 MQ），响应完整记录到任务日志 */
    private void triggerCrawler(String path, String taskLabel) {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank()) {
            XxlJobHelper.handleFail("未配置爬虫地址 ums.crawler.base-url（环境变量 UMS_CRAWLER_BASEURL）");
            return;
        }
        String tradeDate = XxlJobHelper.getJobParam();
        Map<String, Object> body = tradeDate != null && !tradeDate.isBlank()
                ? Map.of("trigger_type", "SCHEDULED", "trade_date", tradeDate.trim())
                : Map.of("trigger_type", "SCHEDULED");
        try {
            // 爬虫响应头不规范（Content-Type: application/octet-stream），
            // 沿用 CrawlTriggerClient 方式：exchange 读原始流，完整记录响应
            String response = restClient.post()
                    .uri(base + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((req, resp) -> {
                        String bodyText = new String(resp.getBody().readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8);
                        if (resp.getStatusCode().isError()) {
                            throw new RestClientException(
                                    "爬虫返回错误 status=" + resp.getStatusCode() + " body=" + bodyText);
                        }
                        return bodyText;
                    });
            log.info("{}触发成功 response={}", taskLabel, response);
            XxlJobHelper.log(taskLabel + "触发成功: " + response);
        } catch (RestClientException e) {
            log.warn("{}触发失败: {}", taskLabel, e.getMessage(), e);
            XxlJobHelper.handleFail("触发失败: " + e.getMessage());
        }
    }
}
