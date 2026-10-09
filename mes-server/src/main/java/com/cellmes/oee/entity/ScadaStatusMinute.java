package com.cellmes.oee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** SCADA 分钟桶（ch07）：秒级状态按 1 分钟取众数聚合；OEE 只读分钟表，禁用秒级直算。 */
@Data
@TableName("scada_status_minute")
public class ScadaStatusMinute {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private Integer processId;
    private LocalDate shiftDate;
    private String shiftCode;
    private LocalDateTime minuteTs;
    private String scadaStatus;
    private Integer actualOutput;
    private Integer rejectQty;
    /** 标准产出：件/分钟 */
    private BigDecimal standardRate;
}
