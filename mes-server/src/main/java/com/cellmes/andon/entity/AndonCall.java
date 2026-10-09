package com.cellmes.andon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Andon 呼叫（ch09）：4 级 SLA + 超时自动升级（SLA 减半，最多 3 次）。 */
@Data
@TableName("andon_call")
public class AndonCall {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime callTime;
    /** MANUAL/DEVICE/SPC/CUSTOMER/AI_PREDICTION */
    private String callSource;
    /** L1/L2/L3/L4 */
    private String callLevel;
    private String deviceCode;
    private Integer processId;
    private Long lotId;
    private Long callerId;
    private String title;
    private String description;
    /** OPEN / RESPONDING / RESOLVED / CLOSED */
    private String status;
    private Integer slaResponseMin;
    private Integer slaOnsiteMin;
    private LocalDateTime slaResponseDeadline;
    private LocalDateTime slaOnsiteDeadline;
    private LocalDateTime respondedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private Long assigneeId;
    /** 升级目标（上级人员 ID） */
    private Long escalateToId;
    /** 升级链计数（上限 3，ch09 面试点：最多升到厂长） */
    private Integer escalateCount;
    private Long eightDCaseId;
}
