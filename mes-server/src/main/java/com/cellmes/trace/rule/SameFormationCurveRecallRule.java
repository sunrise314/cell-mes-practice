package com.cellmes.trace.rule;

import com.cellmes.trace.dao.TraceDao;
import com.cellmes.trace.dao.TraceEdge;
import com.cellmes.trace.entity.Lot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则 3：同一化成柜 + 同一化成曲线召回（ch06）。
 * 曲线不在 movement 上，存于 work_order_operation.process_params（JSONB），
 * 由 TraceDao.findFinalLotsSameFormationCurve 用 SQL join 解析。
 */
@Component("sameFormationCurveRecallRule")
@RequiredArgsConstructor
public class SameFormationCurveRecallRule implements RecallRule {

    private final TraceDao traceDao;

    @Override
    public String ruleName() {
        return "sameFormationCurve";
    }

    @Override
    public List<Lot> findAffected(Long tenantId, Long sourceLotId) {
        // 找到事故批次对应的化成工序移动（operation_seq = 6）
        List<TraceEdge> backward = traceDao.traceBackward(tenantId, sourceLotId, 20);
        List<Long> seq6MovementIds = backward.stream()
                .filter(e -> e.getOperationSeq() != null && e.getOperationSeq() == 6)
                .map(TraceEdge::getMovementId)
                .distinct()
                .toList();
        if (seq6MovementIds.isEmpty()) {
            return List.of();
        }
        return traceDao.findFinalLotsSameFormationCurve(tenantId, seq6MovementIds.getFirst());
    }
}
