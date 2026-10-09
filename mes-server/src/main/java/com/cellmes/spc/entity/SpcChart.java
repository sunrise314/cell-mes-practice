package com.cellmes.spc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SPC 控制图（ch08）：规格限是外部约束，控制限是内部统计（±3σ，动态）。 */
@Data
@TableName("spc_chart")
public class SpcChart {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private Integer processId;
    /** 容量 / 厚度 / 张力 ... */
    private String characteristic;
    private Integer subgroupSize;
    private BigDecimal specLower;
    private BigDecimal specUpper;
    private BigDecimal target;
    private BigDecimal controlLower;
    private BigDecimal controlUpper;
    private BigDecimal meanValue;
    private BigDecimal sigmaValue;
    private BigDecimal cp;
    private BigDecimal cpk;
    private BigDecimal ppk;
    /** BASELINE_INIT / ACTIVE / RECALCULATING（期间不软报警） */
    private String baselineStatus;
    private LocalDateTime baselineSince;
    /** JSONB：["rule1",...,"rule8"] */
    private String enabledRules;
    private LocalDateTime createdAt;
}
