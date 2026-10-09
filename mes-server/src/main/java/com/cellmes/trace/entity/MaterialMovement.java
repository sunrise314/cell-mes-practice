package com.cellmes.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 物料移动（ch06 批次三链）：一表记录料链/工序链/设备链。 */
@Data
@TableName("material_movement")
public class MaterialMovement {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime eventTime;
    /** 料链：上游批次强制扫描（非空 = 追溯链物理上不可能断） */
    private Long fromLotId;
    private String fromLotType;
    /** 工序链 */
    private Long workOrderId;
    private Integer operationSeq;
    /** 设备链 */
    private String deviceCode;
    private Long toLotId;
    private String toLotType;
    private BigDecimal quantity;
    private String unit;
    /** 双向链表（递归追溯入口；1→N 分叉走 lot 关联补全） */
    private Long prevMovementId;
    private Long nextMovementId;
}
