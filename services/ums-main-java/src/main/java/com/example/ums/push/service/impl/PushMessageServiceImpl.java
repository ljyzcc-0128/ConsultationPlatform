package com.example.ums.push.service.impl;

import com.example.ums.push.dto.PushRecordDto;
import com.example.ums.push.mapper.PushMessageMapper;
import com.example.ums.push.model.PushMessage;
import com.example.ums.push.service.PushMessageService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 推送消息查询实现。 */
@Service
public class PushMessageServiceImpl implements PushMessageService {

    private final PushMessageMapper pushMessageMapper;

    public PushMessageServiceImpl(PushMessageMapper pushMessageMapper) {
        this.pushMessageMapper = pushMessageMapper;
    }

    @Override
    public List<PushRecordDto> listByUser(String userId) {
        List<PushMessage> messages = pushMessageMapper.findByUserId(userId);
        return messages.stream().map(msg -> new PushRecordDto(
                String.valueOf(msg.getId()),
                msg.getTitle(),
                msg.getChannel(),
                msg.getSentAt() != null ? msg.getSentAt().toString() : null,
                msg.getStatus()
        )).toList();
    }
}
