package com.cellmes.formation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模型版本（ch10）：EXPERIMENT/STAGING/PRODUCTION/RETIRED 四态流转，线上只认 PRODUCTION。 */
@Data
@TableName("model_version")
public class ModelVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String version;
    /** 规则版为配置标识（rules:baseline-v1）；XGBoost 版为模型文件路径 */
    private String modelPath;
    private BigDecimal mae;
    private BigDecimal r2;
    private BigDecimal accuracy;
    private BigDecimal falseAlarmRate;
    private BigDecimal missRate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime promotedAt;
}
