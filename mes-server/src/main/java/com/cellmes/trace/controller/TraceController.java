package com.cellmes.trace.controller;

import com.cellmes.common.api.R;
import com.cellmes.trace.dao.TraceDao;
import com.cellmes.trace.dao.TraceEdge;
import com.cellmes.trace.entity.MaterialMovement;
import com.cellmes.trace.service.LotService;
import com.cellmes.trace.service.MaterialReceiveService;
import com.cellmes.trace.service.RecallService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 批次追溯（ch06）：双向追溯 / 投料登记 / 召回执行。 */
@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceDao traceDao;
    private final LotService lotService;
    private final MaterialReceiveService materialReceiveService;
    private final RecallService recallService;

    /** 正向追溯（追根源）：成品批次 → 原料批次。 */
    @GetMapping("/{lotNo}/backward")
    public R<List<TraceEdge>> backward(@PathVariable String lotNo,
                                       @RequestParam(defaultValue = "20") int maxDepth) {
        Long lotId = lotService.getByLotNo(lotNo).getId();
        Long tenantId = currentTenant();
        return R.ok(traceDao.traceBackward(tenantId, lotId, maxDepth));
    }

    /** 反向追溯（追影响面）：原料批次 → 全部下游成品。 */
    @GetMapping("/{lotNo}/forward")
    public R<List<TraceEdge>> forward(@PathVariable String lotNo,
                                      @RequestParam(defaultValue = "20") int maxDepth) {
        Long lotId = lotService.getByLotNo(lotNo).getId();
        Long tenantId = currentTenant();
        return R.ok(traceDao.traceForward(tenantId, lotId, maxDepth));
    }

    /** 投料登记：from_lot 强制 + 余量校验 + 双向链表维护。 */
    @PostMapping("/movements")
    public R<MaterialMovement> record(@RequestBody MaterialReceiveService.RecordRequest req) {
        return R.ok(materialReceiveService.record(req));
    }

    /** 召回执行：按启用规则计算影响面并自动 hold（含已出货批次）。 */
    @PostMapping("/recall")
    public R<RecallService.RecallResult> recall(@RequestParam String lotNo,
                                                @RequestParam(required = false) String rule) {
        return R.ok(recallService.recall(lotNo, rule));
    }

    private Long currentTenant() {
        Long tenantId = com.cellmes.common.tenant.TenantContext.get();
        if (tenantId == null) {
            throw new com.cellmes.common.domain.BizException(401, "租户上下文缺失");
        }
        return tenantId;
    }
}
