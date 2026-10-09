package com.cellmes.master.service;

import com.cellmes.common.domain.BizException;
import com.cellmes.master.entity.ResourceGroup;
import com.cellmes.master.entity.RoutingOperation;
import com.cellmes.master.entity.WorkOrderOperation;
import com.cellmes.master.mapper.ResourceGroupMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 派工排程（ch01）：
 * - 按工序 seq 顺序串行排程：plannedStart = max(前序 plannedEnd)；
 * - duration = standard_duration_min × qty / 1000（标准节拍按 1000 颗定义）；
 * - 派工只到 WorkCell 级：取设备组 device_codes 首个设备（ch05 组内互替）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ResourceGroupMapper resourceGroupMapper;
    private final ObjectMapper objectMapper;

    /**
     * 把工艺路线模板展开成派工单实例。
     *
     * @param targetDeviceCode 强制指定的目标设备（如化成柜），只对带 formation_curve 参数的工序生效
     */
    public List<WorkOrderOperation> expand(String woNo, List<RoutingOperation> templates, int quantity,
                                           LocalDateTime start, String targetDeviceCode) {
        List<WorkOrderOperation> result = new ArrayList<>();
        LocalDateTime cursor = start;
        for (RoutingOperation tpl : templates) {
            WorkOrderOperation op = new WorkOrderOperation();
            op.setWoNo(woNo);
            op.setSeq(tpl.getSeq());
            op.setOperationName(tpl.getOperationName());
            op.setResourceGroupId(tpl.getResourceGroupId());
            op.setWorkCellCode(pickWorkCell(tpl, targetDeviceCode));
            op.setProcessParams(tpl.getProcessParams());
            op.setParamSetVersion(1);
            op.setStatus("WAITING");

            // duration = std × qty / 1000，向上取整到分钟
            BigDecimal duration = tpl.getStandardDurationMin()
                    .multiply(BigDecimal.valueOf(quantity))
                    .divide(BigDecimal.valueOf(1000), 0, RoundingMode.CEILING);
            op.setStandardDurationMin(duration);
            long minutes = duration.setScale(0, RoundingMode.CEILING).longValue();

            op.setPlannedStart(cursor);
            cursor = cursor.plusMinutes(minutes);
            op.setPlannedEnd(cursor);
            result.add(op);
        }
        return result;
    }

    /** 组内取首个可互替设备；强制目标设备仅对化成类工序生效。 */
    private String pickWorkCell(RoutingOperation tpl, String targetDeviceCode) {
        boolean hasCurve = tpl.getProcessParams() != null && tpl.getProcessParams().contains("formation_curve");
        if (targetDeviceCode != null && hasCurve) {
            return targetDeviceCode;
        }
        if (tpl.getResourceGroupId() == null) {
            return null;
        }
        ResourceGroup group = resourceGroupMapper.selectById(tpl.getResourceGroupId());
        if (group == null || group.getDeviceCodes() == null) {
            throw new BizException(500, "设备组不存在或为空: id=" + tpl.getResourceGroupId());
        }
        try {
            List<String> devices = objectMapper.readValue(group.getDeviceCodes(), new TypeReference<>() {
            });
            if (devices.isEmpty()) {
                throw new BizException(500, "设备组无可用设备: " + group.getCode());
            }
            return devices.getFirst();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "设备组 JSON 解析失败: " + group.getCode());
        }
    }
}
