package com.cellmes.trace.dao;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 追溯链边（ch06）：一条 material_movement 映射为一条有向边 from_lot → to_lot。 */
@Data
public class TraceEdge {

    private Long movementId;
    private Integer depth;
    private Integer operationSeq;
    private String deviceCode;
    private Long workOrderId;
    private BigDecimal quantity;
    private String unit;
    private LocalDateTime eventTime;
    private Long fromLotId;
    private String fromLotNo;
    private String fromLotType;
    private Long toLotId;
    private String toLotNo;
    private String toLotType;
}
