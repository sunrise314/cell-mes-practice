package com.cellmes.master.controller;

import com.cellmes.common.api.R;
import com.cellmes.master.entity.WorkOrder;
import com.cellmes.master.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 工单（ch01 派工单展开）。 */
@RestController
@RequestMapping("/api/workorders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @PostMapping
    public R<WorkOrder> create(@RequestBody WorkOrderService.CreateRequest req) {
        return R.ok(workOrderService.create(req));
    }

    @GetMapping("/{woNo}")
    public R<WorkOrderService.Detail> detail(@PathVariable String woNo) {
        return R.ok(workOrderService.detail(woNo));
    }
}
