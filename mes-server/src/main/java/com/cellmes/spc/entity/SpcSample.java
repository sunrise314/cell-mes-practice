package com.cellmes.spc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SPC 样本（ch08）：lot_id 关联批次，规则违反时自动 hold。 */
@Data
@TableName("spc_sample")
public class SpcSample {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chartId;
    private LocalDateTime sampleTime;
    private Integer subgroupNo;
    private BigDecimal sampleValue;
    private Long lotId;
}
