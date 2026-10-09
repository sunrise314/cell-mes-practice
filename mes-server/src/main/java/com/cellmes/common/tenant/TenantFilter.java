package com.cellmes.common.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 租户解析过滤器（ch02 fail-closed）：
 * 所有业务请求必须携带 X-Tenant-Id 头，缺失一律 400 拒绝——宁可报错，绝不串数据。
 * /actuator/** 为运维探针，豁免。
 */
@Component
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("X-Tenant-Id");
        if (!StringUtils.hasText(header)) {
            reject(response, "缺少 X-Tenant-Id 请求头（多租户 fail-closed）");
            return;
        }
        try {
            TenantContext.set(Long.parseLong(header.trim()));
            chain.doFilter(request, response);
        } catch (NumberFormatException e) {
            reject(response, "X-Tenant-Id 必须为数字");
        } finally {
            TenantContext.clear();
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                objectMapper.writeValueAsString(java.util.Map.of("code", 400, "message", message))
                        .getBytes(StandardCharsets.UTF_8));
    }
}
