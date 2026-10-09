package com.cellmes.andon.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 通知服务（ch09）：教学骨架用日志模拟 App 推送/短信/广播。 */
@Slf4j
@Service
public class NotificationService {

    public void notifyLevel(String level, String target, String title, String description) {
        log.warn("[Andon 通知] level={} target={} title={} desc={}", level, target, title, description);
    }

    public void notifyEscalation(Long callId, int escalateCount, String newLevel) {
        log.warn("[Andon 升级] call={} 第 {} 次升级，当前责任层级 {}", callId, escalateCount, newLevel);
    }
}
