package com.example.ums.feed.service.impl;

import com.example.ums.feed.mapper.ReadStatusMapper;
import com.example.ums.feed.service.ReadStatusService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 已读状态服务实现。
 */
@Service
@RequiredArgsConstructor
public class ReadStatusServiceImpl implements ReadStatusService {

    private final ReadStatusMapper readStatusMapper;

    @Override
    public void batchMarkRead(String userId, List<String> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return;
        }
        readStatusMapper.batchMarkRead(userId, articleIds);
    }

    @Override
    public void reset(String userId) {
        readStatusMapper.resetAll(userId);
    }
}
