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

/**
 * 规则 2：同一电解液批次召回（ch06）。
 * 电解液以 MATERIAL 类批次进入注液工序（并行支路），回溯时按 from_lot_type=MATERIAL 识别。
 */
@Component("sameElectrolyteLotRecallRule")
@RequiredArgsConstructor
public class SameElectrolyteLotRecallRule implements RecallRule {

    private final TraceDao traceDao;
    private final LotService lotService;

    @Override
    public String ruleName() {
        return "sameElectrolyteLot";
    }

    @Override
    public List<Lot> findAffected(Long tenantId, Long sourceLotId) {
        List<TraceEdge> backward = traceDao.traceBackward(tenantId, sourceLotId, 20);
        List<Long> materialLotIds = backward.stream()
                .filter(e -> "MATERIAL".equals(e.getFromLotType()))
                .map(TraceEdge::getFromLotId)
                .distinct()
                .toList();
        if (materialLotIds.isEmpty()) {
            return List.of();
        }
        Set<Lot> affected = new LinkedHashSet<>();
        for (Long materialLotId : materialLotIds) {
            for (TraceEdge edge : traceDao.traceForward(tenantId, materialLotId, 20)) {
                if ("FINAL_CELL".equals(edge.getToLotType())) {
                    affected.add(lotService.getById(edge.getToLotId()));
                }
            }
        }
        return List.copyOf(affected);
    }
}
