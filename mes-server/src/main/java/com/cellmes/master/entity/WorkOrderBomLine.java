package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 工单 BOM 行（ch05）：模板行 × 工单数量，发料状态跟踪。 */
@Data
@TableName("work_order_bom_line")
public class WorkOrderBomLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String woNo;
    private Integer lineNo;
    private Long childItemId;
    /** 单颗用量 × 工单数量 */
    private BigDecimal quantity;
    private String unit;
    private Integer operationSeq;
    /** JSONB：[{"item_id":123,"priority":1}] */
    private String substitutes;
    /** PENDING / ISSUED */
    private String status;
}
