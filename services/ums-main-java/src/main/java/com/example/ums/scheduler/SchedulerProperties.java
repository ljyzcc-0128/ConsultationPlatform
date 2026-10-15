package com.example.ums.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 采集调度配置（ums.scheduler.*）。
 * 注意：调度频率/启停/路由由 XXL-JOB 调度中心控制（任务 crawlScheduledJob），
 * 这里只保留执行参数。调度中心地址等执行器配置见 XxlJobConfig（ums.xxl-job.*）。
 */
@ConfigurationProperties(prefix = "ums.scheduler")
public class SchedulerProperties {

    /** 每次任务触发每个信源的抓取条数上限（传给爬虫 trigger 的 limit；任务参数可覆盖） */
    private int triggerLimit = 10;

    public int getTriggerLimit() { return triggerLimit; }
    public void setTriggerLimit(int triggerLimit) { this.triggerLimit = triggerLimit; }
}
