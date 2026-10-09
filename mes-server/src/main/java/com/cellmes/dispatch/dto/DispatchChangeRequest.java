package com.cellmes.dispatch.dto;

import lombok.Data;

/** 改派请求（ch05）：四步法 = 影响评估 → 前置校验 → 重展开 → 参数重绑定。 */
@Data
public class DispatchChangeRequest {

    /** 工单号 */
    private String woNo;
    /** 换产品（如 X-314 → X-280），可选 */
    private Long newProductItemId;
    /** 换工艺路线版本，可选；缺省=新产品的 ACTIVE 版本 */
    private String newRoutingRevision;
    /** 换 BOM 版本，可选；缺省=新产品的 ACTIVE 版本 */
    private String newBomRevision;
    /** 换目标设备（化成柜等），可选 */
    private String newTargetDeviceCode;
    /** 试算：只跑校验不落库，返回完整检查清单 */
    private boolean dryRun;
}
