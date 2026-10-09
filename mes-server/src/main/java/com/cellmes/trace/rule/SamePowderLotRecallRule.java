package com.cellmes.trace.rule;

import com.cellmes.trace.dao.TraceDao;
import com.cellmes.trace.dao.TraceEdge;
import com.cellmes.trace.entity.Lot;
import com.cellmes.trace.service.LotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 规则 1：同一正极粉料批次召回（客户合同标准定义，ch06）。 */
@Component("samePowderLotRecallRule")
@RequiredArgsConstructor
public class SamePowderLotRecallRule implements RecallRule {

    private final TraceDao traceDao;
    private final LotService lotService;

    @Override
    public String ruleName() {
        return "samePowderLot";
    }

    @Override
    public List<Lot> findAffected(Long tenantId, Long sourceLotId) {
        // 正向回溯找 POWDER 源头批次
        List<TraceEdge> backward = traceDao.traceBackward(tenantId, sourceLotId, 20);
        List<Long> powderLotIds = backward.stream()
                .filter(e -> "POWDER".equals(e.getFromLotType()))
                .map(TraceEdge::getFromLotId)
                .distinct()
                .toList();
        if (powderLotIds.isEmpty()) {
            return List.of();
        }
        // 再从粉料批次向下游扩散，收集全部 FINAL_CELL
        Set<Lot> affected = new LinkedHashSet<>();
        for (Long powderLotId : powderLotIds) {
            for (TraceEdge edge : traceDao.traceForward(tenantId, powderLotId, 20)) {
                if ("FINAL_CELL".equals(edge.getToLotType())) {
                    affected.add(lotService.getById(edge.getToLotId()));
                }
            }
        }
        return List.copyOf(affected);
    }
}
