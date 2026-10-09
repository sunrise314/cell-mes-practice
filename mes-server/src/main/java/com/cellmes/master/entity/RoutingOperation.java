package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 工艺路线工序（ch01）：7 段工艺 COAT/ROLL/SLIT/WIND/FILL/FORM/TEST。 */
@Data
@TableName("routing_operation")
public class RoutingOperation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routingId;
    /** 工序号 1-7（DAG 前置关系按 seq 排序近似） */
    private Integer seq;
    private String operationCode;
    private String operationName;
    private String workCenterCode;
    /** ch05 可用设备组 */
    private Long resourceGroupId;
    /** 参考批量 1000 颗的整段节拍（分钟） */
    private BigDecimal standardDurationMin;
    /** JSONB：{"formation_curve":"FORN-280-CHG-V3"} */
    private String processParams;
    /** ch01 关键参数集（SPC 基线重置事件来源） */
    private String criticalParamSet;
}
