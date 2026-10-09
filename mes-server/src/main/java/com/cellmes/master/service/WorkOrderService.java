package com.cellmes.master.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.master.entity.*;
import com.cellmes.master.mapper.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 工单服务（ch01）：按三件套模板展开 7 张派工单 + 工单 BOM 行。 */
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private static final DateTimeFormatter WO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderOperationMapper workOrderOperationMapper;
    private final WorkOrderBomLineMapper workOrderBomLineMapper;
    private final ItemMapper itemMapper;
    private final RoutingMapper routingMapper;
    private final RoutingOperationMapper routingOperationMapper;
    private final BomHeaderMapper bomHeaderMapper;
    private final BomLineMapper bomLineMapper;
    private final ScheduleService scheduleService;

    @Data
    public static class CreateRequest {
        private String productItemCode;
        private Integer quantity;
        /** 可选计划开始时间（ISO），缺省=当前时间 */
        private String planStart;
    }

    @Data
    public static class Detail {
        private WorkOrder workOrder;
        private List<WorkOrderOperation> operations;
        private List<WorkOrderBomLine> bomLines;
    }

    @Transactional
    public WorkOrder create(CreateRequest req) {
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            throw new BizException("工单数量必须大于 0");
        }
        Item product = itemMapper.selectOne(Wrappers.<Item>lambdaQuery()
                .eq(Item::getCode, req.getProductItemCode()).last("limit 1"));
        if (product == null) {
            throw new BizException(404, "产品物料不存在: " + req.getProductItemCode());
        }

        Routing routing = routingMapper.selectOne(Wrappers.<Routing>lambdaQuery()
                .eq(Routing::getProductItemId, product.getId())
                .eq(Routing::getStatus, "ACTIVE")
                .orderByDesc(Routing::getId).last("limit 1"));
        if (routing == null) {
            throw new BizException(404, "产品无 ACTIVE 工艺路线: " + product.getCode());
        }
        BomHeader bom = bomHeaderMapper.selectOne(Wrappers.<BomHeader>lambdaQuery()
                .eq(BomHeader::getProductItemId, product.getId())
                .eq(BomHeader::getStatus, "ACTIVE")
                .orderByDesc(BomHeader::getId).last("limit 1"));
        if (bom == null) {
            throw new BizException(404, "产品无 ACTIVE BOM: " + product.getCode());
        }

        WorkOrder wo = new WorkOrder();
        wo.setWoNo(nextWoNo());
        wo.setProductItemId(product.getId());
        wo.setQuantity(req.getQuantity());
        wo.setRoutingId(routing.getId());
        wo.setBomHeaderId(bom.getId());
        wo.setStatus("CREATED");
        workOrderMapper.insert(wo);

        // 展开派工单：串行排程，duration = std × qty / 1000
        LocalDateTime start = req.getPlanStart() != null ? LocalDateTime.parse(req.getPlanStart()) : LocalDateTime.now();
        List<RoutingOperation> templates = routingOperationMapper.selectList(Wrappers.<RoutingOperation>lambdaQuery()
                .eq(RoutingOperation::getRoutingId, routing.getId())
                .orderByAsc(RoutingOperation::getSeq));
        for (WorkOrderOperation op : scheduleService.expand(wo.getWoNo(), templates, req.getQuantity(), start, null)) {
            workOrderOperationMapper.insert(op);
        }

        // 展开工单 BOM 行：模板行 × 工单数量
        List<BomLine> lines = bomLineMapper.selectList(Wrappers.<BomLine>lambdaQuery()
                .eq(BomLine::getBomHeaderId, bom.getId())
                .orderByAsc(BomLine::getLineNo));
        for (BomLine line : lines) {
            WorkOrderBomLine woLine = new WorkOrderBomLine();
            woLine.setWoNo(wo.getWoNo());
            woLine.setLineNo(line.getLineNo());
            woLine.setChildItemId(line.getChildItemId());
            woLine.setQuantity(line.getQuantity().multiply(BigDecimal.valueOf(req.getQuantity())));
            woLine.setUnit(line.getUnit());
            woLine.setOperationSeq(line.getOperationSeq());
            woLine.setSubstitutes(line.getSubstitutes());
            woLine.setStatus("PENDING");
            workOrderBomLineMapper.insert(woLine);
        }
        return wo;
    }

    public Detail detail(String woNo) {
        WorkOrder wo = workOrderMapper.selectOne(Wrappers.<WorkOrder>lambdaQuery()
                .eq(WorkOrder::getWoNo, woNo).last("limit 1"));
        if (wo == null) {
            throw new BizException(404, "工单不存在: " + woNo);
        }
        Detail detail = new Detail();
        detail.setWorkOrder(wo);
        detail.setOperations(workOrderOperationMapper.selectList(Wrappers.<WorkOrderOperation>lambdaQuery()
                .eq(WorkOrderOperation::getWoNo, woNo).orderByAsc(WorkOrderOperation::getSeq)));
        detail.setBomLines(workOrderBomLineMapper.selectList(Wrappers.<WorkOrderBomLine>lambdaQuery()
                .eq(WorkOrderBomLine::getWoNo, woNo).orderByAsc(WorkOrderBomLine::getLineNo)));
        return detail;
    }

    /** 工单号：WO-yyyyMMdd-NNN（当日流水）。 */
    private String nextWoNo() {
        String prefix = "WO-" + LocalDate.now().format(WO_DATE) + "-";
        Long count = workOrderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()
                .likeRight(WorkOrder::getWoNo, prefix));
        return prefix + String.format("%03d", count + 1);
    }
}
