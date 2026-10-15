package com.example.ums.ai.service.impl;

import com.example.ums.ai.AIProcessingService;
import com.example.ums.ai.dto.QaAnswerDto;
import com.example.ums.ai.dto.QaReferenceDto;
import com.example.ums.ai.mapper.AiLogMapper;
import com.example.ums.ai.model.AiProcessingLog;
import com.example.ums.ai.service.QaService;
import com.example.ums.common.util.UrlHashCalculator;
import com.example.ums.content.dto.NewsListItemDto;
import com.example.ums.content.service.ContentSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能问答编排实现。
 *
 * 流程：问题校验 → AI 配置检查 → 全文检索资讯：
 * - 命中 → NEWS 模式（资讯摘要组装进 prompt，回答带 [n] 引用）
 * - 未命中/检索异常 → PLATFORM 模式（静态平台使用知识）
 * 每次调用写 ai_processing_log（operation=qa，AI-003 可追溯）。
 */
@Service
public class QaServiceImpl implements QaService {

    private static final Logger log = LoggerFactory.getLogger(QaServiceImpl.class);

    private static final int MAX_QUESTION_CHARS = 500;
    private static final int TOP_K = 6;
    private static final String OPERATION_QA = "qa";

    private static final String NEWS_SYSTEM_PROMPT = """
            你是储能行业的资讯分析师。请基于提供的平台采集资讯回答用户问题。要求：
            - 用中文回答，简洁清晰；
            - 陈述事实时用 [n] 标注来源（n 为资讯编号）；
            - 忠实于给定资讯，不编造资讯中没有的内容；资讯不足以完整回答时如实说明；
            - 回答末尾用一句话注明：以上基于平台已采集资讯，信息可能不完整。""";

    /** 平台使用知识（一期静态内置；二期可迁入配置/知识库） */
    private static final String PLATFORM_KNOWLEDGE = """
            【平台定位】储能资讯平台（UMS）：面向储能行业的一站式资讯情报系统，
            自动采集多来源行业资讯（政策、市场、供应链、技术等），经 AI 加工与人工审核后发布，
            并提供供应链看板与管理后台。

            【三大能力】
            1. 资讯情报：AI 自动生成中文三段式摘要（发生了什么→对储能行业意味着什么→对系统集成商意味着什么）
               与主题分类（BESS、Policy、Supply Chain 等）；人工审核通过后对外展示。
            2. 供应链看板：材料价格、库存、贸易物流、供应商四类数据查询。
            3. 管理后台：信源管理、内容审核（通过/驳回/调整分类）、用户管理、采集任务调度、
               死信队列处理、推送管理、审计日志。

            【主要接口】
            - 登录：POST /api/auth/login，body 传 provider/username/credential；
              provider=LOCAL（本地账号）或 IAM（公司统一认证），成功返回 JWT token，
              后续请求头携带 Authorization: Bearer <token>。
            - 资讯（公开）：GET /api/news（时间线，游标分页，可按 sourceId/category/language/日期筛选）、
              GET /api/news/{articleId}（详情含正文）、GET /api/v1/search?q=（全文搜索）、
              GET /api/v1/contents/{id}/related（相关内容）、GET /api/v1/contents/{id}/timeline（变更时间线）。
            - 个人（需登录）：GET /api/me/feed（个人订阅 Feed）、
              POST /api/me/read-status/batch（批量已读）、POST /api/me/read-status/reset（重置已读）、
              /api/me/subscriptions（关键词/信源订阅管理，增删改查）。
            - 供应链（需登录）：GET /api/v1/supply-chain/materials|inventory|trade-logistics|suppliers。
            - 管理后台（需 ADMIN 角色）：/api/v1/admin/**（sources 信源、reviews 审核、users 用户、
              tasks 任务、dlq 死信、push 推送、audit-logs 审计、scheduler 调度状态）。

            【角色】USER（普通用户，可浏览/搜索/订阅/看供应链）；ADMIN（管理员，另可用管理后台）。""";

    private static final String PLATFORM_SYSTEM_PROMPT = """
            你是「储能资讯平台（UMS）」的智能助手，基于下述平台介绍回答用户关于平台使用的问题。要求：
            - 用中文回答；
            - 只回答与平台功能、使用方法相关的问题，不编造平台不存在的能力；
            - 若用户咨询的是储能行业资讯类问题而平台暂无相关采集内容，如实告知，
              并建议使用内容中心全文搜索（GET /api/v1/search?q=关键词）或浏览首页资讯时间线。

            平台介绍：
            %s""".formatted(PLATFORM_KNOWLEDGE);

    private final AIProcessingService aiService;
    private final ContentSearchService contentSearchService;
    private final AiLogMapper aiLogMapper;

    public QaServiceImpl(AIProcessingService aiService,
            ContentSearchService contentSearchService,
            AiLogMapper aiLogMapper) {
        this.aiService = aiService;
        this.contentSearchService = contentSearchService;
        this.aiLogMapper = aiLogMapper;
    }

    @Override
    public QaAnswerDto ask(String question) {
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能为空");
        }
        String q = question.trim();
        if (q.length() > MAX_QUESTION_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "问题长度不能超过 " + MAX_QUESTION_CHARS + " 字符");
        }
        if (!aiService.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 服务未配置（UMS_AI_API_KEY 为空或已禁用）");
        }

        // 1. 检索（检索故障不阻断问答：按无结果处理走 PLATFORM 模式）
        List<NewsListItemDto> hits;
        try {
            hits = contentSearchService.search(q, null, TOP_K).items();
        } catch (Exception e) {
            log.warn("问答检索失败，按无结果处理 question={}", snippet(q), e);
            hits = List.of();
        }

        // 2. 组装 prompt 与引用
        boolean newsMode = !hits.isEmpty();
        String systemPrompt = newsMode ? NEWS_SYSTEM_PROMPT : PLATFORM_SYSTEM_PROMPT;
        List<QaReferenceDto> references = new ArrayList<>();
        String userPrompt;
        if (newsMode) {
            StringBuilder sb = new StringBuilder("用户问题：").append(q)
                    .append("\n\n以下是与问题相关的平台采集资讯（编号即引用序号）：\n");
            for (int i = 0; i < hits.size(); i++) {
                NewsListItemDto n = hits.get(i);
                sb.append('[').append(i + 1).append("] ")
                        .append(nullSafe(n.title())).append("｜来源：").append(nullSafe(n.sourceName()))
                        .append("｜发布日期：").append(n.publishDate() != null ? n.publishDate() : "未知")
                        .append('\n').append("摘要：").append(nullSafe(n.summary())).append('\n');
                references.add(new QaReferenceDto(n.articleId(), n.title(), n.sourceName(),
                        n.originalUrl(), n.publishDate()));
            }
            userPrompt = sb.toString();
        } else {
            userPrompt = "用户问题：" + q;
        }

        // 3. 调模型（计时 + 记日志，operation=qa、newsId=NULL，V7 已放开非空限制）
        long t0 = System.currentTimeMillis();
        String inputDigest = UrlHashCalculator.sha256Hex(q);
        String answer;
        try {
            answer = aiService.complete(systemPrompt, userPrompt);
            record("SUCCESS", (int) (System.currentTimeMillis() - t0), inputDigest, null);
        } catch (Exception e) {
            record("FAILED", (int) (System.currentTimeMillis() - t0), inputDigest, e.getMessage());
            throw e;
        }
        log.info("问答完成 mode={} provider={} model={} refs={}",
                newsMode ? "NEWS" : "PLATFORM", aiService.providerName(), aiService.modelName(),
                references.size());
        return new QaAnswerDto(answer, newsMode ? "NEWS" : "PLATFORM", references,
                aiService.providerName(), aiService.modelName());
    }

    private void record(String status, Integer latencyMs, String inputDigest, String error) {
        try {
            AiProcessingLog entry = new AiProcessingLog();
            entry.setNewsId(null);
            entry.setOperation(OPERATION_QA);
            entry.setProvider(aiService.providerName());
            entry.setModel(aiService.modelName());
            entry.setStatus(status);
            entry.setLatencyMs(latencyMs);
            entry.setInputDigest(inputDigest);
            if (error != null && error.length() > 500) {
                error = error.substring(0, 500);
            }
            entry.setErrorSummary(error);
            aiLogMapper.insert(entry);
        } catch (Exception e) {
            // 日志失败不影响问答主流程
            log.warn("问答日志写入失败 status={}", status, e);
        }
    }

    private static String nullSafe(String s) {
        return s != null ? s : "（无）";
    }

    private static String snippet(String s) {
        return s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }
}
