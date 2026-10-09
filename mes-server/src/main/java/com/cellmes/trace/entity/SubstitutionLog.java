package com.cellmes.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 替代料选择日志（ch05）：合规审计要求，记录选择过程与理由。 */
@Data
@TableName("substitution_log")
public class SubstitutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String woNo;
    private Long bomLineId;
    private Long mainItemId;
    private Long chosenItemId;
    private Integer priority;
    private String reason;
    private LocalDateTime createdAt;
}
