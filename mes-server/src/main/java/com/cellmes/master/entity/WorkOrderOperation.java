package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 派工单（ch01：按工序一行；ch01 的 dispatch_order ≡ 本表）。 */
@Data
@TableName("work_order_operation")
public class WorkOrderOperation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String woNo;
    private Integer seq;
    private String operationName;
    private Long resourceGroupId;
    /** 派工只到 WorkCell 级（ch01 关键约束） */
    private String workCellCode;
    private LocalDate shiftDate;
    /** A/B/C 三班 */
    private String shiftCode;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private BigDecimal standardDurationMin;
    /** JSONB：{"formation_curve":"FORN-280-CHG-V3"} */
    private String processParams;
    /** 防止改派时参数错配（ch01/ch03） */
    private Integer paramSetVersion;
    /** WAITING / ISSUED（已发料）/ RUNNING / DONE */
    private String status;
}
