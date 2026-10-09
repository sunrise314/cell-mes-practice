package com.cellmes.master.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 租户（ch02）：全局表，租户拦截器白名单。 */
@Data
@TableName("tenant")
public class Tenant {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String status;
    private LocalDateTime createdAt;
}
