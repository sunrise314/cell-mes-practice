package com.cellmes.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 批次（ch06）：lot_type 表达批次所处的工序阶段；HOLD 为质量拦截状态。 */
@Data
@TableName("lot")
public class Lot {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户（插入时由拦截器自动填充，实体保持 null） */
    private Long tenantId;
    /** SN 贯穿注液后各阶段（FILLED/FORMED/FINAL 同 lot_no 不同 lot_type） */
    private String lotNo;
    /** POWDER/COIL/SUBCOIL/BARE_CELL/FILLED_CELL/FORMED_CELL/FINAL_CELL/MATERIAL */
    private String lotType;
    private Long itemId;
    private BigDecimal quantity;
    private String unit;
    /** IN_STOCK / CONSUMED / SHIPPED / HOLD */
    private String status;
    private LocalDateTime createdAt;
}
