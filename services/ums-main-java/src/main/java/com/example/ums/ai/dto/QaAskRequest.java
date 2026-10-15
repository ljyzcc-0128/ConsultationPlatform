package com.example.ums.ai.dto;

/**
 * 智能问答请求（POST /api/v1/qa/ask）。
 */
public record QaAskRequest(String question) {
}
