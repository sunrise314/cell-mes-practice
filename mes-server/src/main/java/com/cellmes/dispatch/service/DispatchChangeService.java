package com.cellmes.dispatch.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.common.event.ProcessParamChangeEvent;
import com.cellmes.common.tenant.TenantContext;
import com.cellmes.dispatch.dto.DispatchChangeRequest;
import com.cellmes.dispatch.dto.DispatchChangeResult;
import com.cellmes.master.entity.*;
import com.cellmes.master.mapper.*;
import com.cellmes.master.service.BomService;
import com.cellmes.master.service.RoutingService;
import com.cellmes.master.service.ScheduleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 改派四步法（ch05）：影响评估 → 前置校验（7 检查）→ 重展开 → 参数重绑定。
 * 核心纪律：任何一项校验不过，整个改派 fail-closed，绝不半套执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchChangeService {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderOperationMapper workOrderOperationMapper;
    private final WorkOrderBomLineMapper workOrderBomLineMapper;
    private final ItemMapper itemMapper;
    private final RoutingService routingService;
    private final BomService bomService;
    private final RoutingOperationMapper routingOperationMapper;
    private final BomLineMapper bomLineMapper;
    private final InventoryMapper inventoryMapper;
    private final DeviceRecipeMapper deviceRecipeMapper;
    private final ResourceGroupMapper resourceGroupMapper;
    private final WorkCellMapper workCellMapper;
    private final ScheduleService scheduleService;
    private final FormationCurveDispatcher formationCurveDispatcher;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public DispatchChangeResult change(DispatchChangeRequest req) {
        WorkOrder wo = workOrderMapper.selectOne(Wrappers.<WorkOrder>lambdaQuery()
                .eq(WorkOrder::getWoNo, req.getWoNo()).last("limit 1"));
        if (wo == null) {
            throw new BizException(404, "工单不存在: " + req.getWoNo());
        }
        List<WorkOrderOperation> oldOps = workOrderOperationMapper.selectList(
                Wrappers.<WorkOrderOperation>lambdaQuery().eq(WorkOrderOperation::getWoNo, req.getWoNo()));
        List<RoutingOperation> oldTemplates = wo.getRoutingId() == null ? List.of()
                : routingOperationMapper.selectList(Wrappers.<RoutingOperation>lambdaQuery()
                        .eq(RoutingOperation::getRoutingId, wo.getRoutingId()));

        List<DispatchChangeResult.Check> checks = new ArrayList<>();

        // ---------- 检查 1：工单状态 ----------
        boolean statusOk = "CREATED".equals(wo.getStatus()) || "RELEASED".equals(wo.getStatus());
        checks.add(new DispatchChangeResult.Check("状态检查", statusOk,
                "工单状态=" + wo.getStatus() + "，仅 CREATED/RELEASED 可改派"));

        // ---------- 检查 2：已发料工序 ----------
        List<WorkOrderOperation> issued = oldOps.stream()
                .filter(op -> "ISSUED".equals(op.getStatus()))
                .toList();
        checks.add(new DispatchChangeResult.Check("已发料检查", issued.isEmpty(),
                issued.isEmpty() ? "所有工序未发料" : "第 " + issued.getFirst().getSeq()
                        + " 工序已发料，改派将导致物料账实不符"));

        // ---------- 目标三件套解析 ----------
        Long newProductId = req.getNewProductItemId() != null ? req.getNewProductItemId() : wo.getProductItemId();
        Item newItem = itemMapper.selectById(newProductId);
        if (newItem == null) {
            throw new BizException(404, "目标产品物料不存在: " + newProductId);
        }
        Routing newRouting = req.getNewRoutingRevision() != null
                ? routingService.getByRevision(newProductId, req.getNewRoutingRevision())
                : routingService.getActiveRouting(newProductId);
        BomHeader newBom = req.getNewBomRevision() != null
                ? bomService.getByRevision(newProductId, req.getNewBomRevision())
                : bomService.getActiveBom(newProductId);
        List<RoutingOperation> newTemplates = routingService.listOperations(newRouting.getId());

        // ---------- 检查 3：新 BOM ACTIVE ----------
        boolean bomActive = newBom != null && "ACTIVE".equals(newBom.getStatus());
        checks.add(new DispatchChangeResult.Check("新 BOM ACTIVE", bomActive,
                newBom == null ? "BOM 不存在" : "BOM " + newBom.getRevision() + " 状态=" + newBom.getStatus()));

        // ---------- 检查 4：新 Routing ACTIVE ----------
        boolean routingActive = newRouting != null && "ACTIVE".equals(newRouting.getStatus());
        checks.add(new DispatchChangeResult.Check("新 Routing ACTIVE", routingActive,
                newRouting == null ? "Routing 不存在" : "Routing " + newRouting.getRevision() + " 状态=" + newRouting.getStatus()));

        // ---------- 检查 5：设备组支持 ----------
        String targetDevice = req.getNewTargetDeviceCode() != null ? req.getNewTargetDeviceCode() : wo.getTargetDeviceCode();
        DispatchChangeResult.Check deviceCheck = checkDeviceSupport(targetDevice, newTemplates);
        checks.add(deviceCheck);

        // ---------- 检查 6：库存充足性 ----------
        checks.add(checkInventory(newBom, wo));

        // ---------- 检查 7：化成曲线 capacity_spec 双向校验 ----------
        checks.add(checkFormationCurve(newTemplates, newItem));

        boolean allPass = checks.stream().allMatch(DispatchChangeResult.Check::isPass);
        if (req.isDryRun()) {
            return new DispatchChangeResult(req.getWoNo(), true, allPass, checks, null);
        }
        if (!allPass) {
            String failed = checks.stream().filter(c -> !c.isPass())
                    .map(c -> c.getName() + ": " + c.getDetail())
                    .collect(Collectors.joining("；"));
            throw new BizException(409, "改派被拦截（fail-closed）—— " + failed);
        }

        // ---------- 重展开：BOM ----------
        reExpandBom(wo, newBom);
        // ---------- 重展开：Routing（排程重算 + 参数集版本递增） ----------
        int newVersion = oldOps.stream()
                .mapToInt(op -> op.getParamSetVersion() == null ? 1 : op.getParamSetVersion())
                .max().orElse(1) + 1;
        List<WorkOrderOperation> newOps = reExpandRouting(wo, newTemplates, targetDevice, newVersion);

        // ---------- 工单头更新 ----------
        wo.setProductItemId(newItem.getId());
        wo.setRoutingId(newRouting.getId());
        wo.setBomHeaderId(newBom.getId());
        if (req.getNewTargetDeviceCode() != null) {
            wo.setTargetDeviceCode(req.getNewTargetDeviceCode());
        }
        workOrderMapper.updateById(wo);

        // ---------- 下发化成曲线 ----------
        formationCurveDispatcher.dispatch(newOps);

        // ---------- 参数变更事件（SPC 基线重置，ch08） ----------
        publishParamChangeEvents(oldTemplates, newTemplates, newOps, newVersion);

        log.info("[改派完成] wo={} → 产品 {} / Routing {} / BOM {} / 参数集 v{}",
                req.getWoNo(), newItem.getCode(), newRouting.getRevision(), newBom.getRevision(), newVersion);
        return new DispatchChangeResult(req.getWoNo(), false, true, checks,
                new DispatchChangeResult.Summary(newVersion, newOps.size(),
                        bomService.listLines(newBom.getId()).size(), targetDevice));
    }

    /** 检查 5：目标设备必须存在于某个新工艺资源组的 device_codes 内。 */
    private DispatchChangeResult.Check checkDeviceSupport(String targetDevice, List<RoutingOperation> newTemplates) {
        if (targetDevice == null) {
            return new DispatchChangeResult.Check("设备组支持", true, "未指定目标设备，跳过");
        }
        WorkCell cell = workCellMapper.selectOne(Wrappers.<WorkCell>lambdaQuery()
                .eq(WorkCell::getCode, targetDevice).last("limit 1"));
        if (cell == null) {
            return new DispatchChangeResult.Check("设备组支持", false, "设备不存在: " + targetDevice);
        }
        for (RoutingOperation tpl : newTemplates) {
            if (tpl.getResourceGroupId() == null) {
                continue;
            }
            ResourceGroup group = resourceGroupMapper.selectById(tpl.getResourceGroupId());
            if (group == null || group.getDeviceCodes() == null) {
                continue;
            }
            try {
                List<String> devices = objectMapper.readValue(group.getDeviceCodes(), new TypeReference<>() {
                });
                if (devices.contains(targetDevice)) {
                    return new DispatchChangeResult.Check("设备组支持", true,
                            "设备 " + targetDevice + " ∈ 设备组 " + group.getCode());
                }
            } catch (Exception e) {
                throw new BizException(500, "设备组 JSON 解析失败: " + group.getCode());
            }
        }
        return new DispatchChangeResult.Check("设备组支持", false,
                "设备 " + targetDevice + " 不在新工艺任何资源组内");
    }

    /** 检查 6：新 BOM 每行需求量（单颗用量 × 工单数量）库存充足。 */
    private DispatchChangeResult.Check checkInventory(BomHeader newBom, WorkOrder wo) {
        if (newBom == null) {
            return new DispatchChangeResult.Check("库存充足性", false, "新 BOM 不存在，无法校验");
        }
        for (BomLine line : bomService.listLines(newBom.getId())) {
            BigDecimal required = line.getQuantity().multiply(BigDecimal.valueOf(wo.getQuantity()));
            Inventory inv = inventoryMapper.selectOne(Wrappers.<Inventory>lambdaQuery()
                    .eq(Inventory::getItemId, line.getChildItemId()).last("limit 1"));
            if (inv == null || inv.getQuantity().compareTo(required) < 0) {
                return new DispatchChangeResult.Check("库存充足性", false,
                        "物料 " + line.getChildItemId() + " 需求 " + required + "，现有 "
                                + (inv == null ? "0" : inv.getQuantity()));
            }
        }
        return new DispatchChangeResult.Check("库存充足性", true, "新 BOM 全部子料库存充足");
    }

    /** 检查 7：化成曲线配方 capacity_spec == 产品 capacity_spec（ch05 双向校验）。 */
    private DispatchChangeResult.Check checkFormationCurve(List<RoutingOperation> newTemplates, Item newItem) {
        RoutingOperation formOp = newTemplates.stream()
                .filter(tpl -> tpl.getProcessParams() != null && tpl.getProcessParams().contains("formation_curve"))
                .findFirst().orElse(null);
        if (formOp == null) {
            return new DispatchChangeResult.Check("化成曲线一致性", true, "工艺无化成曲线，跳过");
        }
        try {
            Map<String, Object> params = objectMapper.readValue(formOp.getProcessParams(), new TypeReference<>() {
            });
            String curve = String.valueOf(params.get("formation_curve"));
            DeviceRecipe recipe = deviceRecipeMapper.selectOne(Wrappers.<DeviceRecipe>lambdaQuery()
                    .eq(DeviceRecipe::getCode, curve).last("limit 1"));
            if (recipe == null) {
                return new DispatchChangeResult.Check("化成曲线一致性", false, "曲线配方不存在: " + curve);
            }
            boolean match = recipe.getCapacitySpec() != null
                    && recipe.getCapacitySpec().equals(newItem.getCapacitySpec());
            return new DispatchChangeResult.Check("化成曲线一致性", match,
                    "曲线 " + curve + " capacity_spec=" + recipe.getCapacitySpec()
                            + " vs 物料 capacity_spec=" + newItem.getCapacitySpec()
                            + (match ? "，一致" : "，不一致（禁止下发）"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "process_params JSON 解析失败: " + formOp.getProcessParams());
        }
    }

    /** 删旧重插工单 BOM 行（改派前已确认无 ISSUED 行）。 */
    private void reExpandBom(WorkOrder wo, BomHeader newBom) {
        workOrderBomLineMapper.delete(Wrappers.<WorkOrderBomLine>lambdaQuery()
                .eq(WorkOrderBomLine::getWoNo, wo.getWoNo()));
        for (BomLine line : bomService.listLines(newBom.getId())) {
            WorkOrderBomLine woLine = new WorkOrderBomLine();
            woLine.setWoNo(wo.getWoNo());
            woLine.setLineNo(line.getLineNo());
            woLine.setChildItemId(line.getChildItemId());
            woLine.setQuantity(line.getQuantity().multiply(BigDecimal.valueOf(wo.getQuantity())));
            woLine.setUnit(line.getUnit());
            woLine.setOperationSeq(line.getOperationSeq());
            woLine.setSubstitutes(line.getSubstitutes());
            woLine.setStatus("PENDING");
            workOrderBomLineMapper.insert(woLine);
        }
    }

    /** 删旧重插派工单：排程从当前时间重算，param_set_version 递增。 */
    private List<WorkOrderOperation> reExpandRouting(WorkOrder wo, List<RoutingOperation> newTemplates,
                                                     String targetDevice, int newVersion) {
        workOrderOperationMapper.delete(Wrappers.<WorkOrderOperation>lambdaQuery()
                .eq(WorkOrderOperation::getWoNo, wo.getWoNo()));
        List<WorkOrderOperation> newOps = scheduleService.expand(
                wo.getWoNo(), newTemplates, wo.getQuantity(), LocalDateTime.now(), targetDevice);
        for (WorkOrderOperation op : newOps) {
            op.setParamSetVersion(newVersion);
            workOrderOperationMapper.insert(op);
        }
        return newOps;
    }

    /** 关键参数集发生变化的工序发布 ProcessParamChangeEvent（ch08 SPC 基线重置触发源）。 */
    private void publishParamChangeEvents(List<RoutingOperation> oldTemplates,
                                          List<RoutingOperation> newTemplates,
                                          List<WorkOrderOperation> newOps,
                                          int newVersion) {
        Map<Integer, String> oldParamSets = oldTemplates.stream()
                .filter(t -> t.getCriticalParamSet() != null)
                .collect(Collectors.toMap(RoutingOperation::getSeq, RoutingOperation::getCriticalParamSet));
        Map<Integer, String> workCells = newOps.stream()
                .collect(Collectors.toMap(WorkOrderOperation::getSeq,
                        op -> op.getWorkCellCode() == null ? "" : op.getWorkCellCode()));
        Long tenantId = TenantContext.get();
        for (RoutingOperation tpl : newTemplates) {
            String newParamSet = tpl.getCriticalParamSet();
            if (newParamSet == null || newParamSet.equals(oldParamSets.get(tpl.getSeq()))) {
                continue;
            }
            eventPublisher.publishEvent(new ProcessParamChangeEvent(
                    tenantId, workCells.get(tpl.getSeq()), tpl.getSeq(), newParamSet, newVersion));
        }
    }
}
