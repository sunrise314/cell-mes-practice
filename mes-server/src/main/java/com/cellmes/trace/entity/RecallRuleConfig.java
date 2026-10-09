package com.cellmes.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 召回规则元数据（ch06）：运营可加规则不改代码，rule_bean 即 Spring Bean 名。 */
@Data
@TableName("recall_rule_config")
public class RecallRuleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** samePowderLot / sameElectrolyteLot / sameFormationCurve */
    private String ruleName;
    /** Spring Bean 名，如 samePowderLotRecallRule */
    private String ruleBean;
    private String description;
    private Boolean enabled;
}
