package com.cellmes.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/** SPC 规则违反事件（ch08）：SpcService 判定后发布，SpcAlertHandler 监听执行自动 hold + Andon。 */
@Data
@AllArgsConstructor
public class RuleViolationEvent {

    private Long tenantId;
    private Long chartId;
    private String deviceCode;
    private Integer processId;
    private String characteristic;
    /** rule1-rule8 */
    private String ruleId;
    private Integer subgroupNo;
    private Double sampleValue;
    /** 违规点关联批次（可空） */
    private Long lotId;
    private String description;
}
