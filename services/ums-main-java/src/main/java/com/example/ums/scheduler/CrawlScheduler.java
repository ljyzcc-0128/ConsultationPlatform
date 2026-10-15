package com.example.ums.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.admin.mapper.AdminSourceMapper;
import com.example.ums.admin.model.CpSource;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 采集定时调度（实现指南 ADR：单一调度系统 XXL-JOB）。
 *
 * 任务名 crawlScheduledJob，由 xxl-job-admin 调度中心按 cron 触发（建议 0 0/10 * * * ?）。
 * 每次执行：扫描 crawl_enabled=1 的信源，「当前时间 - last_crawl_time ≥ 频率间隔」即触发
 * （last_crawl_time 为 null 视为到期，新信源上线即首抓）。
 * 触发结果由 CrawlTriggerClient 回写 cp_source（成功更新 last_crawl_time/crawl_status）。
 * 单实例执行器（一期单机）；多实例时在调度中心配置路由策略=第一个或故障转移。
 */
@Component
public class CrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);

    private final AdminSourceMapper sourceMapper;
    private final CrawlTriggerClient triggerClient;
    private final SchedulerProperties properties;

    public CrawlScheduler(AdminSourceMapper sourceMapper,
                          CrawlTriggerClient triggerClient,
                          SchedulerProperties properties) {
        this.sourceMapper = sourceMapper;
        this.triggerClient = triggerClient;
        this.properties = properties;
    }

    /** XXL-JOB 任务入口：触发所有到期信源。任务参数（可选）：覆盖单次抓取条数 limit。 */
    @XxlJob("crawlScheduledJob")
    public void crawlScheduledJob() {
        int limit = properties.getTriggerLimit();
        String param = XxlJobHelper.getJobParam();
        if (param != null && param.matches("\\d+")) {
            limit = Integer.parseInt(param);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<CpSource> due = dueSources(now);
        XxlJobHelper.log("调度扫描：{} 个信源到期（limit={}）", due.size(), limit);
        log.info("[crawlScheduledJob] {} 个信源到期，limit={}", due.size(), limit);

        int success = 0;
        int failed = 0;
        for (CpSource source : due) {
            try {
                triggerClient.triggerAndRecord(source, limit);
                success++;
            } catch (RestClientException e) {
                failed++; // 单信源失败不中断；Failed 已回写，下轮仍会重试
            } catch (Exception e) {
                failed++;
                log.error("调度触发未知异常 source={}", source.getSourceId(), e);
            }
        }
        XxlJobHelper.log("执行完成：成功 {} / 失败 {}", success, failed);
        if (failed > 0 && success == 0) {
            XxlJobHelper.handleFail("全部触发失败（爬虫不可达？）");
        } else {
            XxlJobHelper.handleSuccess();
        }
    }

    /** 查询到期信源（enabled=1 且超过频率间隔未抓取）；状态接口复用。 */
    public List<CpSource> dueSources(LocalDateTime nowUtc) {
        return sourceMapper.selectList(new LambdaQueryWrapper<CpSource>()
                        .eq(CpSource::getCrawlEnabled, true))
                .stream()
                .filter(s -> isDue(s, nowUtc))
                .toList();
    }

    /** 到期判断（静态口径，便于单测）：last_crawl_time 为 null 或距今 ≥ 间隔。 */
    public static boolean isDue(CpSource source, LocalDateTime nowUtc) {
        LocalDateTime last = source.getLastCrawlTime();
        if (last == null) {
            return true;
        }
        Duration interval = FrequencyParser.parse(source.getCrawlFrequency());
        Instant deadline = last.toInstant(ZoneOffset.UTC).plus(interval);
        return !nowUtc.toInstant(ZoneOffset.UTC).isBefore(deadline);
    }
}
