package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 设备组（ch05）：routing_operation.resource_group_id 指向本表，device_codes 为可互替设备清单。 */
@Data
@TableName("resource_group")
public class ResourceGroup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    /** JSONB：["BC-001","BC-002"] */
    private String deviceCodes;
}
