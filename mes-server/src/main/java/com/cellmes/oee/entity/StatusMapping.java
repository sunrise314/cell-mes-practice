package com.cellmes.oee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 状态映射（ch07）：每工序独立配置，SCADA 原始状态 → 时间类别；未映射 fail-closed。 */
@Data
@TableName("status_mapping")
public class StatusMapping {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 1涂布 2辊压 3分切 4卷绕 5注液 6化成 7分容 */
    private Integer processId;
    /** RUN/IDLE/PM/CHG/MAT_WAIT/ALARM/MECH/ELEC/OFF */
    private String scadaStatus;
    /** run_time / idle_time / planned_down / unplanned_down */
    private String timeCategory;
}
