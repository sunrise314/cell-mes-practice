package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 工艺路线头（ch01 三件套模板层）。 */
@Data
@TableName("routing")
public class Routing {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productItemId;
    /** V3 */
    private String revision;
    /** ACTIVE / DEPRECATED */
    private String status;
}
