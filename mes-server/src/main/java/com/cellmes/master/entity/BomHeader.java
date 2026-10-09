package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** BOM 头（ch01/ch05）。 */
@Data
@TableName("bom_header")
public class BomHeader {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productItemId;
    /** V5 */
    private String revision;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    /** ACTIVE / DEPRECATED */
    private String status;
}
