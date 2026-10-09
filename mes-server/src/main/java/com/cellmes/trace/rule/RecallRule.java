package com.cellmes.trace.rule;

import com.cellmes.trace.entity.Lot;

import java.util.List;

/**
 * 召回规则接口（ch06）：新增规则 = 实现接口 + recall_rule_config 加一行配置，代码零改动。
 * 实现类 Bean 名必须与 recall_rule_config.rule_bean 一致。
 */
public interface RecallRule {

    /** 规则名（samePowderLot 等，对应配置行 rule_name）。 */
    String ruleName();

    /**
     * 计算受影响批次。
     *
     * @param tenantId     租户
     * @param sourceLotId  事故批次（任意阶段 lot）
     * @return 受影响的 FINAL_CELL 批次列表
     */
    List<Lot> findAffected(Long tenantId, Long sourceLotId);
}
