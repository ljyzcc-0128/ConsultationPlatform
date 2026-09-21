package com.example.ums.ai;

import com.example.ums.ai.dto.ClassificationResult;

/**
 * AI 加工适配层接口（ADR-04：业务代码不绑定供应商）。
 * 一期实现 summarize + classify；translate/extractEntities 等后续按需扩展。
 * 每次调用由调用方记录 provider/model/耗时/状态 到 ai_processing_log（AI-003）。
 */
public interface AIProcessingService {

    /**
     * 摘要：生成目标语言的三段式中文摘要
     * （发生了什么 → 对 BESS 意味着什么 → 对 Jinko ESS 意味着什么）。
     *
     * @param text           标题 + 正文的组合文本
     * @param targetLanguage 目标语言（BCP 47，如 zh-CN）
     * @return 摘要文本
     */
    String summarize(String text, String targetLanguage);

    /**
     * 分类：从统一主题字典中选出 1-3 个标签。
     */
    ClassificationResult classify(String text);

    /** API key 等是否已配置；未配置时调用方应走 SKIPPED 而非 FAILED */
    boolean isConfigured();

    String providerName();

    String modelName();
}
