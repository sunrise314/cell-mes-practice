package com.cellmes.common.tenant;

/** 租户上下文（ch02）：请求线程内持有当前租户 ID，由 TenantFilter 写入、请求结束清理。 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        TENANT.set(tenantId);
    }

    public static Long get() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }
}
