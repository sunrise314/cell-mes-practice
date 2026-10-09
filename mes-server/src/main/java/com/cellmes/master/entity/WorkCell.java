package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** ISA-95 工作单元（ch01）：具体设备，派工只能到这一级。 */
@Data
@TableName("work_cell")
public class WorkCell {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private Long workCenterId;
    /** 设备型号（化成曲线下发校验用，如 F1-96G） */
    private String model;
    private String status;
}
