package com.example.ums.admin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理操作审计注解（实现指南 5.2）。
 * 加在 Service 方法上，AuditLogAspect 拦截后自动写入 cp_audit_log，
 * 业务代码不需要手写审计日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 操作类型，如 SOURCE_UPDATE / REVIEW_APPROVE */
    String action();

    /** 对象类型，如 cp_source / cp_news / dlq */
    String objectType();
}
