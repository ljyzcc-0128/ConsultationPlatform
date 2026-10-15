package com.example.ums.admin.aspect;

/**
 * 当前操作人上下文（ThreadLocal）。
 * AdminTokenFilter 鉴权时写入（取 X-Admin-User 头，默认 local-admin），
 * AuditLogAspect 写日志时读取。
 */
public final class OperatorContext {

    public static final String DEFAULT_OPERATOR = "local-admin";

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private OperatorContext() {
    }

    public static void set(String operator) {
        HOLDER.set(operator == null || operator.isBlank() ? DEFAULT_OPERATOR : operator);
    }

    public static String get() {
        String v = HOLDER.get();
        return v == null ? DEFAULT_OPERATOR : v;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
