package com.cellmes.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 化成完成事件（ch10）：化成柜报文到达即发布，
 * CapacityPredictionService 监听后在 1 分钟内完成容量预测与三档拦截。
 */
@Data
@AllArgsConstructor
public class FormationCompleteEvent {

    /** 租户 ID（事件跨层传递，不依赖 ThreadLocal） */
    private Long tenantId;
    /** 化成完成批次（FORMED_CELL lot） */
    private Long lotId;
    /** 化成柜编码 */
    private String deviceCode;
    /** 预测特征：coatThicknessDevPct / fillQtyDevPct / cutoffCurrentMa / ambientTempDevC / selfDischargeMv */
    private Map<String, Object> features;
}
