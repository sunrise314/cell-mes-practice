package com.cellmes.dispatch.service;

import com.cellmes.common.domain.BizException;
import com.cellmes.dispatch.adapter.DeviceCollectAdapter;
import com.cellmes.master.entity.DeviceRecipe;
import com.cellmes.master.entity.WorkOrderOperation;
import com.cellmes.master.mapper.DeviceRecipeMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 化成曲线下发器（ch05）：改派/排产后把工单工序上的化成曲线推送到目标设备。
 * capacity_spec 双向校验已在改派前置检查完成，这里只负责下发动作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormationCurveDispatcher {

    private final DeviceCollectAdapter collectAdapter;
    private final DeviceRecipeMapper deviceRecipeMapper;
    private final ObjectMapper objectMapper;

    /** 扫描派工单中的化成工序并下发曲线。 */
    public void dispatch(List<WorkOrderOperation> operations) {
        for (WorkOrderOperation op : operations) {
            String curve = extractCurve(op);
            if (curve == null) {
                continue;
            }
            DeviceRecipe recipe = deviceRecipeMapper.selectOne(Wrappers.<DeviceRecipe>lambdaQuery()
                    .eq(DeviceRecipe::getCode, curve).last("limit 1"));
            if (recipe == null) {
                throw new BizException(500, "化成曲线配方不存在: " + curve);
            }
            collectAdapter.dispatchCurve(op.getWorkCellCode(), curve, recipe.getParams());
        }
    }

    public String extractCurve(WorkOrderOperation op) {
        if (op.getProcessParams() == null || !op.getProcessParams().contains("formation_curve")) {
            return null;
        }
        try {
            Map<String, Object> params = objectMapper.readValue(op.getProcessParams(), new TypeRef());
            Object curve = params.get("formation_curve");
            return curve == null ? null : curve.toString();
        } catch (Exception e) {
            throw new BizException(500, "process_params JSON 解析失败: " + op.getProcessParams());
        }
    }

    private static class TypeRef extends com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> {
    }
}
