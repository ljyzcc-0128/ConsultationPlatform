package com.example.ums.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.admin.annotation.Audited;
import com.example.ums.admin.dto.SourceCreateRequest;
import com.example.ums.admin.dto.SourceUpdateRequest;
import com.example.ums.admin.mapper.AdminSourceMapper;
import com.example.ums.admin.model.CpSource;
import com.example.ums.admin.service.AdminSourceService;
import com.example.ums.scheduler.CrawlTriggerClient;
import org.springframework.stereotype.Service;

import java.util.List;

/** 信源管理实现。 */
@Service
public class AdminSourceServiceImpl implements AdminSourceService {

    private final AdminSourceMapper sourceMapper;
    private final CrawlTriggerClient crawlTriggerClient;

    public AdminSourceServiceImpl(AdminSourceMapper sourceMapper,
                                   CrawlTriggerClient crawlTriggerClient) {
        this.sourceMapper = sourceMapper;
        this.crawlTriggerClient = crawlTriggerClient;
    }

    @Override
    public List<CpSource> list() {
        return sourceMapper.selectList(new LambdaQueryWrapper<CpSource>()
                .orderByAsc(CpSource::getPriority)
                .orderByAsc(CpSource::getSourceId));
    }

    @Override
    @Audited(action = "SOURCE_CREATE", objectType = "cp_source")
    public CpSource create(SourceCreateRequest request) {
        // 必填字段校验
        if (request.sourceId() == null || request.sourceId().isBlank()) {
            throw new IllegalArgumentException("信源ID不能为空");
        }
        if (request.sourceName() == null || request.sourceName().isBlank()) {
            throw new IllegalArgumentException("信源名称不能为空");
        }
        if (request.entryUrl() == null || request.entryUrl().isBlank()) {
            throw new IllegalArgumentException("入口URL不能为空");
        }
        if (request.language() == null || request.language().isBlank()) {
            throw new IllegalArgumentException("默认语言不能为空");
        }
        // ID 唯一性校验
        if (sourceMapper.selectById(request.sourceId()) != null) {
            throw new IllegalArgumentException("信源ID已存在: " + request.sourceId());
        }
        CpSource source = new CpSource();
        source.setSourceId(request.sourceId());
        source.setSourceName(request.sourceName());
        source.setRegion(request.region() != null && !request.region().isBlank() ? request.region() : "未知");
        source.setSourceType(request.sourceType() != null && !request.sourceType().isBlank() ? request.sourceType() : "行业媒体");
        source.setEntryUrl(request.entryUrl());
        source.setLanguage(request.language());
        source.setCrawlEnabled(request.crawlEnabled() != null ? request.crawlEnabled() : true);
        source.setCrawlFrequency(request.crawlFrequency() != null && !request.crawlFrequency().isBlank() ? request.crawlFrequency() : "1/day");
        source.setPriority(request.priority() != null && !request.priority().isBlank() ? request.priority() : "P1");
        source.setPagination(request.pagination() != null ? request.pagination() : true);
        source.setFetchDetail(request.fetchDetail() != null ? request.fetchDetail() : true);
        source.setDateFilter(request.dateFilter() != null ? request.dateFilter() : true);
        source.setCategory(request.category());
        source.setCrawlStatus("NotRun");
        source.setRemark(request.remark());
        sourceMapper.insert(source);
        return sourceMapper.selectById(request.sourceId());
    }

    @Override
    @Audited(action = "SOURCE_UPDATE", objectType = "cp_source")
    public CpSource update(String sourceId, SourceUpdateRequest request) {
        CpSource source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("信源不存在: " + sourceId);
        }
        if (request.sourceName() != null) {
            source.setSourceName(request.sourceName());
        }
        if (request.crawlEnabled() != null) {
            source.setCrawlEnabled(request.crawlEnabled());
        }
        if (request.crawlFrequency() != null) {
            source.setCrawlFrequency(request.crawlFrequency());
        }
        if (request.priority() != null) {
            source.setPriority(request.priority());
        }
        if (request.remark() != null) {
            source.setRemark(request.remark());
        }
        sourceMapper.updateById(source);
        return sourceMapper.selectById(sourceId);
    }

    @Override
    @Audited(action = "SOURCE_TRIGGER", objectType = "cp_source")
    public String trigger(String sourceId, int limit) {
        CpSource source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("信源不存在: " + sourceId);
        }
        if (!Boolean.TRUE.equals(source.getCrawlEnabled())) {
            throw new IllegalStateException("信源已停用，请先启用再触发: " + sourceId);
        }
        // RestClientException 直接上抛，Controller 转 502（爬虫不可达）；成功/失败回写调度状态
        return crawlTriggerClient.triggerAndRecord(source, limit);
    }
}
