package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 设备配方（ch05）：化成曲线等；capacity_spec 与 item.capacity_spec 相等才允许下发。 */
@Data
@TableName("device_recipe")
public class DeviceRecipe {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** FORN-280-CHG-V3 */
    private String code;
    /** 280Ah / 314Ah */
    private String capacitySpec;
    /** JSONB：["F1-96G"] */
    private String deviceModels;
    /** JSONB：{"cc_current":"0.05C",...} */
    private String params;
    private String status;
}
