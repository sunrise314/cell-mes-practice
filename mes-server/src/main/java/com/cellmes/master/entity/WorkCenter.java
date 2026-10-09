package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** ISA-95 工作中心（ch01）：工序级设备组，如「涂布 1 线」。 */
@Data
@TableName("work_center")
public class WorkCenter {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    /** 极片车间 / 装配车间 / 化成车间 */
    private String area;
}
