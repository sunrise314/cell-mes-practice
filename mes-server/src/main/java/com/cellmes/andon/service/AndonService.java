package com.cellmes.andon.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.andon.AndonLevel;
import com.cellmes.andon.entity.AndonCall;
import com.cellmes.andon.mapper.AndonCallMapper;
import com.cellmes.common.domain.BizException;
import com.cellmes.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Andon 呼叫服务（ch09）：建案 → 响应 → 解决；超时自动升级（SLA 减半，上限 3 次）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AndonService {

    private static final String[] ESCALATION_CHAIN = {"L1 操作工", "L2 班组长", "L3 工程师", "L4 厂长"};

    private final AndonCallMapper andonCallMapper;
    private final NotificationService notificationService;

    @Value("${mes.andon.max-escalations:3}")
    private int maxEscalations;

    @Transactional
    public AndonCall createCall(String source, AndonLevel level, String deviceCode, Integer processId,
                                Long lotId, String title, String description) {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new BizException(401, "租户上下文缺失，无法创建 Andon 呼叫");
        }
        LocalDateTime now = LocalDateTime.now();
        AndonCall call = new AndonCall();
        call.setCallTime(now);
        call.setCallSource(source);
        call.setCallLevel(level.name());
        call.setDeviceCode(deviceCode);
        call.setProcessId(processId);
        call.setLotId(lotId);
        call.setTitle(title);
        call.setDescription(description);
        call.setStatus("OPEN");
        call.setSlaResponseMin(level.getResponseMin());
        call.setSlaOnsiteMin(level.getOnsiteMin());
        call.setSlaResponseDeadline(now.plusMinutes(level.getResponseMin()));
        call.setSlaOnsiteDeadline(now.plusMinutes(level.getOnsiteMin()));
        call.setEscalateCount(0);
        andonCallMapper.insert(call);
        notificationService.notifyLevel(level.name(), level.name() + " 责任人", title, description);
        return call;
    }

    @Transactional
    public AndonCall respond(Long callId, Long userId) {
        AndonCall call = get(callId);
        if (!"OPEN".equals(call.getStatus())) {
            throw new BizException(409, "呼叫状态不可响应: " + call.getStatus());
        }
        call.setStatus("RESPONDING");
        call.setRespondedAt(LocalDateTime.now());
        call.setAssigneeId(userId);
        andonCallMapper.updateById(call);
        return call;
    }

    @Transactional
    public AndonCall resolve(Long callId, Long userId) {
        AndonCall call = get(callId);
        if ("RESOLVED".equals(call.getStatus()) || "CLOSED".equals(call.getStatus())) {
            throw new BizException(409, "呼叫已解决");
        }
        call.setStatus("RESOLVED");
        call.setResolvedAt(LocalDateTime.now());
        if (call.getRespondedAt() == null) {
            call.setRespondedAt(call.getResolvedAt());
            call.setAssigneeId(userId);
        }
        andonCallMapper.updateById(call);
        return call;
    }

    public AndonCall get(Long callId) {
        AndonCall call = andonCallMapper.selectById(callId);
        if (call == null) {
            throw new BizException(404, "Andon 呼叫不存在: id=" + callId);
        }
        return call;
    }

    public List<AndonCall> list(String status) {
        return andonCallMapper.selectList(Wrappers.<AndonCall>lambdaQuery()
                .eq(status != null, AndonCall::getStatus, status)
                .orderByDesc(AndonCall::getCallTime));
    }

    /**
     * 定时升级（ch09 核心机制）：扫描 OPEN 且响应 SLA 已过期的呼叫 →
     * SLA 减半、escalate_count+1、逐级上报（操作工→班组长→工程师→厂长），上限 3 次。
     * 调度线程无租户上下文，扫描/更新走 @InterceptorIgnore 显式 SQL（跨租户巡检）。
     */
    @Scheduled(fixedDelay = 60_000)
    public void escalateOverdue() {
        List<AndonCall> overdue = andonCallMapper.selectOverdueOpen();
        for (AndonCall call : overdue) {
            escalateOne(call);
        }
    }

    /** 手动触发一次升级扫描（演示/测试用，不必等 60 秒）。 */
    public int escalateOverdueNow() {
        List<AndonCall> overdue = andonCallMapper.selectOverdueOpen();
        overdue.forEach(this::escalateOne);
        return overdue.size();
    }

    private void escalateOne(AndonCall call) {
        int count = call.getEscalateCount() == null ? 0 : call.getEscalateCount();
        if (count >= maxEscalations) {
            log.error("[Andon 升级到顶] call={} 已升级 {} 次仍未响应，升级链上限（厂长）兜底处理",
                    call.getId(), count);
            return;
        }
        int newResp = Math.max(1, call.getSlaResponseMin() / 2);
        int newOnsite = Math.max(1, call.getSlaOnsiteMin() / 2);
        int newCount = count + 1;
        LocalDateTime now = LocalDateTime.now();
        andonCallMapper.escalate(call.getId(), newResp, newOnsite,
                now.plusMinutes(newResp), now.plusMinutes(newOnsite), newCount, 9000L + newCount);
        notificationService.notifyEscalation(call.getId(), newCount, ESCALATION_CHAIN[Math.min(newCount, 3)]);
    }
}
