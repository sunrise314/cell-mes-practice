package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** BOM 行（ch01）：BOM 行必须绑工序；substitutes 为替代料清单（ch05）。 */
@Data
@TableName("bom_line")
public class BomLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bomHeaderId;
    private Integer lineNo;
    private Long childItemId;
    /** 单颗用量 */
    private BigDecimal quantity;
    /** kg / g / pcs / m */
    private String unit;
    /** 投入工序 seq */
    private Integer operationSeq;
    /** JSONB：[{"item_id":123,"priority":1}] */
    private String substitutes;
}
