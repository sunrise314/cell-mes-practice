package com.cellmes.spc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 基线归档（ch08 落地清单 7）：事故复盘可回放当时用的基线。 */
@Data
@TableName("baseline_history")
public class BaselineHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chartId;
    private BigDecimal meanValue;
    private BigDecimal sigmaValue;
    private BigDecimal controlLower;
    private BigDecimal controlUpper;
    private LocalDateTime archivedAt;
    /** RECALC / PARAM_CHANGE */
    private String archiveReason;
}
