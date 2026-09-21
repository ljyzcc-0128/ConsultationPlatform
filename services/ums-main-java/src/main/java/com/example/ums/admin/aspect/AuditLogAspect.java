package com.example.ums.admin.aspect;

import com.example.ums.admin.annotation.Audited;
import com.example.ums.admin.mapper.AuditLogMapper;
import com.example.ums.admin.model.CpAuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计切面（实现指南 5.2）：拦截 @Audited 方法，成功后写 cp_audit_log。
 * 一期简化：before/after 合并为「参数 + 结果摘要」存 detail JSON；
 * 审计写入失败只打日志，不影响业务主流程。
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);
    private static final int DETAIL_MAX_LENGTH = 2000;

    private final AuditLogMapper auditLogMapper;
    /** findAndRegisterModules 注册 JavaTimeModule，否则含 LocalDateTime 的结果序列化失败退化为 toString */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AuditLogAspect(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint jp, Audited audited) throws Throwable {
        Object result = jp.proceed();
        try {
            insertLog(jp, audited, result);
        } catch (Exception e) {
            log.warn("审计日志写入失败 action={} : {}", audited.action(), e.getMessage());
        }
        return result;
    }

    private void insertLog(ProceedingJoinPoint jp, Audited audited, Object result) throws Exception {
        CpAuditLog entry = new CpAuditLog();
        entry.setOperator(OperatorContext.get());
        entry.setAction(audited.action());
        entry.setObjectType(audited.objectType());
        entry.setObjectId(extractObjectId(jp.getArgs()));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("args", summarize(jp.getArgs()));
        detail.put("result", summarize(result));
        String json = objectMapper.writeValueAsString(detail);
        if (json.length() > DETAIL_MAX_LENGTH) {
            json = json.substring(0, DETAIL_MAX_LENGTH);
        }
        entry.setDetail(json);
        auditLogMapper.insert(entry);
    }

    /** 取第一个字符串参数作为对象 ID（约定：sourceId / articleId / queue 均为首个参数） */
    private String extractObjectId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private Object summarize(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > DETAIL_MAX_LENGTH
                    ? json.substring(0, DETAIL_MAX_LENGTH) + "..."
                    : objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
