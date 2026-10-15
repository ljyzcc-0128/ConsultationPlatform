package com.example.ums.ai.dto;

import java.util.List;

/**
 * 智能问答回答。
 *
 * @param answer     回答文本（中文）
 * @param mode       回答模式：NEWS=基于平台采集资讯；PLATFORM=基于平台使用知识
 * @param references 引用来源列表（PLATFORM 模式为空）
 * @param provider   AI 供应商标识（如 glm）
 * @param model      模型名（如 glm-4-flash）
 */
public record QaAnswerDto(String answer, String mode, List<QaReferenceDto> references,
                          String provider, String model) {
}
