package com.cellmes.oee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 停机事件（ch07）：原因三层编码，每个非计划停机必须填到 level3。 */
@Data
@TableName("downtime_event")
public class DowntimeEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMin;
    /** 设备故障/待料/报警/人员 */
    private String reasonLevel1;
    /** 机械/电气/上工序缺料 */
    private String reasonLevel2;
    /** 具体原因描述 */
    private String reasonLevel3;
    private Long operatorId;
    private String shiftCode;
}
