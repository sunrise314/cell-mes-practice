package com.cellmes.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.cellmes.common.domain.BizException;
import com.cellmes.common.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置（ch02/ch03）：
 * 1. TenantLineInnerInterceptor —— 行级租户隔离，SQL 自动拼 tenant_id；
 *    上下文缺失直接抛异常（fail-closed），绝不降级为全表查询。
 * 2. PaginationInnerInterceptor —— PostgreSQL 方言分页。
 * 特殊场景（跨租户巡检、手写 CTE）在 Mapper 方法上用 @InterceptorIgnore 显式豁免。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.get();
                if (tenantId == null) {
                    throw new BizException(401, "租户上下文缺失，拒绝执行 SQL（fail-closed）");
                }
                return new LongValue(tenantId);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // tenant 是全局表；flyway_schema_history 是迁移框架表
                return "tenant".equalsIgnoreCase(tableName)
                        || "flyway_schema_history".equalsIgnoreCase(tableName);
            }
        });
        interceptor.addInnerInterceptor(tenantInterceptor);
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
