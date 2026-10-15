package com.example.ums.ai.service;

import com.example.ums.ai.dto.QaAnswerDto;

/**
 * 智能问答服务（对用户同步提问）。
 *
 * 模式判定（一期简单口径，不引入意图分类）：
 * - 问题能全文检索到资讯 → NEWS 模式（RAG，基于采集资讯回答并附引用）
 * - 检索无结果 → PLATFORM 模式（基于平台使用知识回答）
 */
public interface QaService {

    /**
     * 同步问答。
     *
     * @param question 用户问题（非空白，≤500 字符）
     * @return 回答 + 模式 + 引用来源
     * @throws org.springframework.web.server.ResponseStatusException
     *         问题非法 400；AI 未配置 503；模型调用失败 500
     */
    QaAnswerDto ask(String question);
}
