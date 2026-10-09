package com.cellmes.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 关键工艺参数变更事件（ch01/ch08）：
 * param_set_version 递增时发布，SPC 监听后归档旧基线并进入 RECALCULATING（期间不软报警）。
 */
@Data
@AllArgsConstructor
public class ProcessParamChangeEvent {

    private Long tenantId;
    private String deviceCode;
    private Integer processId;
    /** 关键参数集编码，如 FORM-CUTOFF-0.05C */
    private String paramSet;
    /** 变更后的参数集版本 */
    private Integer version;
}
