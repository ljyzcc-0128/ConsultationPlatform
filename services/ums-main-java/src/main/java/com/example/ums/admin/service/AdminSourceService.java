package com.example.ums.admin.service;

import com.example.ums.admin.dto.SourceCreateRequest;
import com.example.ums.admin.dto.SourceUpdateRequest;
import com.example.ums.admin.model.CpSource;

import java.util.List;

/** 信源管理（ADM-001：增改停恢复、手工触发采集）。 */
public interface AdminSourceService {

    /** 全量信源列表（按优先级 + ID 排序）。 */
    List<CpSource> list();

    /** 新增信源。 */
    CpSource create(SourceCreateRequest request);

    /** 更新信源可编辑字段（启停/频率/优先级/名称/备注）。 */
    CpSource update(String sourceId, SourceUpdateRequest request);

    /** 手工触发一次采集（调用 ums-crawler 的 trigger API）。 */
    String trigger(String sourceId, int limit);
}
