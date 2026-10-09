package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 物料主数据（ch01/ch05）：capacity_spec 是化成曲线双向校验的关键字段。 */
@Data
@TableName("item")
public class Item {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    /** PRODUCT / MATERIAL */
    private String itemType;
    /** 280Ah / 314Ah（产品用） */
    private String capacitySpec;
    private LocalDateTime createdAt;
}
