package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工单（ch01 派工单实例层）。 */
@Data
@TableName("work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** WO-20251215-018 */
    private String woNo;
    private Long productItemId;
    private Integer quantity;
    private Long routingId;
    private Long bomHeaderId;
    /** 目标设备（化成柜等，可空=由排程选择） */
    private String targetDeviceCode;
    /** CREATED/RELEASED/STARTED/PAUSED/COMPLETED/CLOSED */
    private String status;
    private LocalDateTime createdAt;
}
