package com.cellmes.trace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.trace.entity.Lot;
import com.cellmes.trace.entity.RecallRuleConfig;
import com.cellmes.trace.mapper.RecallRuleConfigMapper;
import com.cellmes.trace.rule.RecallRule;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 召回服务（ch06）：加载启用规则 → 规则引擎算影响面 → 自动 hold（含已出货批次）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecallService {

    private final RecallRuleConfigMapper recallRuleConfigMapper;
    private final ApplicationContext applicationContext;
    private final LotService lotService;

    @Data
    public static class RecallResult {
        private String sourceLotNo;
        /** ruleName → 受影响批次号列表 */
        private Map<String, List<String>> affectedByRule = new LinkedHashMap<>();
        private int totalHeld;
    }

    @Transactional
    public RecallResult recall(String lotNo, String ruleName) {
        Lot source = lotService.getByLotNo(lotNo);
        List<RecallRuleConfig> configs = recallRuleConfigMapper.selectList(
                Wrappers.<RecallRuleConfig>lambdaQuery().eq(RecallRuleConfig::getEnabled, true));
        if (ruleName != null && !ruleName.isBlank()) {
            configs = configs.stream()
                    .filter(c -> ruleName.equals(c.getRuleName()))
                    .toList();
            if (configs.isEmpty()) {
                throw new BizException(404, "召回规则不存在或未启用: " + ruleName);
            }
        }

        RecallResult result = new RecallResult();
        result.setSourceLotNo(source.getLotNo());
        Set<Long> heldIds = new LinkedHashSet<>();
        for (RecallRuleConfig config : configs) {
            RecallRule rule = applicationContext.getBean(config.getRuleBean(), RecallRule.class);
            List<Lot> affected = rule.findAffected(source.getTenantId() == null
                    ? currentTenantSafe() : source.getTenantId(), source.getId());
            List<String> lotNos = affected.stream().map(Lot::getLotNo).toList();
            result.getAffectedByRule().put(config.getRuleName(), lotNos);
            affected.forEach(l -> heldIds.add(l.getId()));
        }
        for (Long lotId : heldIds) {
            lotService.hold(lotId);
        }
        result.setTotalHeld(heldIds.size());
        log.warn("[召回执行] source={} rules={} hold={}",
                lotNo, result.getAffectedByRule().keySet(), result.getTotalHeld());
        return result;
    }

    private Long currentTenantSafe() {
        Long tenantId = com.cellmes.common.tenant.TenantContext.get();
        if (tenantId == null) {
            throw new BizException(401, "租户上下文缺失");
        }
        return tenantId;
    }
}
