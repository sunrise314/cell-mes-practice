package com.cellmes.formation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 容量预测记录（ch10）：三档拦截决策留痕。 */
@Data
@TableName("capacity_prediction")
public class CapacityPrediction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long lotId;
    private String deviceCode;
    private BigDecimal predictedCapacity;
    /** JSONB：预测特征 */
    private String features;
    /** PASS / WARN / HOLD */
    private String action;
    private LocalDateTime predictedAt;
}
