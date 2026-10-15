package com.example.ums.scheduler.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.admin.mapper.AdminSourceMapper;
import com.example.ums.admin.model.CpSource;
import com.example.ums.scheduler.CrawlScheduler;
import com.example.ums.scheduler.FrequencyParser;
import com.example.ums.scheduler.SchedulerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 调度状态查询（ADM 系统配置的调度视角，只读）。 */
@RestController
@RequestMapping("/api/v1/admin/scheduler")
public class SchedulerStatusController {

    private final AdminSourceMapper sourceMapper;
    private final CrawlScheduler crawlScheduler;
    private final SchedulerProperties properties;

    public SchedulerStatusController(AdminSourceMapper sourceMapper,
                                     CrawlScheduler crawlScheduler,
                                     SchedulerProperties properties) {
        this.sourceMapper = sourceMapper;
        this.crawlScheduler = crawlScheduler;
        this.properties = properties;
    }

    /** 调度总开关 + 各信源到期状态。 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Map<String, Object>> sources = sourceMapper.selectList(
                        new LambdaQueryWrapper<CpSource>()
                                .orderByAsc(CpSource::getPriority)
                                .orderByAsc(CpSource::getSourceId))
                .stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("sourceId", s.getSourceId());
                    m.put("crawlEnabled", s.getCrawlEnabled());
                    m.put("crawlFrequency", s.getCrawlFrequency());
                    m.put("frequencyValid", FrequencyParser.isValid(s.getCrawlFrequency()));
                    m.put("intervalHours", FrequencyParser.parse(s.getCrawlFrequency()).toHours());
                    m.put("lastCrawlTime", s.getLastCrawlTime());
                    m.put("crawlStatus", s.getCrawlStatus());
                    m.put("due", Boolean.TRUE.equals(s.getCrawlEnabled())
                            && CrawlScheduler.isDue(s, now));
                    return m;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("triggerLimit", properties.getTriggerLimit());
        result.put("dueCount", crawlScheduler.dueSources(now).size());
        result.put("sources", sources);
        return result;
    }
}
