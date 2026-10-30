package com.example.ums.ai.web;

import com.example.ums.ai.dto.QaAnswerDto;
import com.example.ums.ai.dto.QaAskRequest;
import com.example.ums.ai.service.QaService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/**
 * 智能问答 REST 接口（对登录用户）。
 *
 * 需 JWT 登录（SecurityConfig 默认 anyRequest().authenticated() 覆盖 /api/v1/qa/**）：
 * 问答调用 GLM 消耗 API 费用，不开放匿名访问。
 */
@RestController
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    /**
     * 智能问答：优先基于平台采集资讯回答（RAG，带引用来源）；
     * 无相关资讯时基于平台使用知识回答。
     * 示例：POST /api/v1/qa/ask {"question":"最近储能行业有什么政策动态？"}
     */
    @PostMapping("/api/v1/qa/ask")
    public QaAnswerDto ask(@RequestBody QaAskRequest request) {
        return qaService.ask(request.question());
    }
}
