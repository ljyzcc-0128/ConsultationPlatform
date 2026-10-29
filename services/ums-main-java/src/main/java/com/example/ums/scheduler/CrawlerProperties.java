package com.example.ums.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 爬虫服务连接配置（ums.crawler.*）。
 * 环境变量注入（经 yml 占位符）：UMS_CRAWLER_URL（base-url）、
 * UMS_CRAWLER_CONNECTTIMEOUTSECONDS、UMS_CRAWLER_RESPONSETIMEOUTSECONDS
 */
@ConfigurationProperties(prefix = "ums.crawler")
public class CrawlerProperties {

    /** 爬虫服务根地址（如 http://localhost:8100），不写死默认值，必须由 yml/环境变量提供 */
    private String baseUrl;

    /** 建立 TCP 连接超时秒数（爬虫不在线时快速失败，避免调度线程长时间阻塞） */
    private int connectTimeoutSeconds = 5;

    /** 等待爬虫响应超时秒数（抓取是慢操作，单次触发允许较长时间） */
    private int responseTimeoutSeconds = 60;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }

    public int getResponseTimeoutSeconds() { return responseTimeoutSeconds; }
    public void setResponseTimeoutSeconds(int responseTimeoutSeconds) { this.responseTimeoutSeconds = responseTimeoutSeconds; }
}
