package com.cellmes.oee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 班次日历（ch07）：OEE 总时间按日历累加；节假日 is_holiday=true 直接从分母排除。 */
@Data
@TableName("shift_calendar")
public class ShiftCalendar {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate shiftDate;
    /** A/B/C/OFF */
    private String shiftCode;
    /** 开始时间归属原则：24:00 归 C 班开始 */
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 班组；未排班不算总时间 */
    private Long crewId;
    private Boolean isHoliday;
}
