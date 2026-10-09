package com.cellmes.spc.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.andon.service.AndonService;
import com.cellmes.common.event.RuleViolationEvent;
import com.cellmes.spc.entity.SpcSample;
import com.cellmes.spc.mapper.SpcSampleMapper;
import com.cellmes.trace.service.LotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SPC 报警处理器（ch08）：规则违反 → 自动 hold 最近 5 个 subgroup 关联批次 + Andon L3 呼叫。
 * 只处理落库 lot 的违规点；趋势类规则（rule3）会带上最近点位的批次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpcAlertHandler {

    private final SpcSampleMapper sampleMapper;
    private final LotService lotService;
    private final AndonService andonService;

    @EventListener
    public void onViolation(RuleViolationEvent event) {
        // 最近 5 个 subgroup 的关联批次全部 hold（止涨/止跌影响面按批次窗口控制）
        List<SpcSample> recent = sampleMapper.selectList(Wrappers.<SpcSample>lambdaQuery()
                .eq(SpcSample::getChartId, event.getChartId())
                .orderByDesc(SpcSample::getSubgroupNo)
                .last("limit 5"));
        Set<Long> lotIds = new LinkedHashSet<>();
        if (event.getLotId() != null) {
            lotIds.add(event.getLotId());
        }
        recent.stream().filter(s -> s.getLotId() != null).forEach(s -> lotIds.add(s.getLotId()));
        for (Long lotId : lotIds) {
            try {
                lotService.hold(lotId);
            } catch (Exception e) {
                log.error("[SPC 自动 hold 失败] lotId={}", lotId, e);
            }
        }

        andonService.createCall("SPC", com.cellmes.andon.AndonLevel.L3,
                event.getDeviceCode(), event.getProcessId(),
                lotIds.isEmpty() ? null : lotIds.iterator().next(),
                "SPC 规则违反 " + event.getRuleId() + "（" + event.getCharacteristic() + "）",
                event.getDescription() + "；已自动 hold 批次 " + lotIds.size() + " 个，请质量工程师现场确认");
        log.warn("[SPC 报警] chart={} rule={} subgroup={} hold={}",
                event.getChartId(), event.getRuleId(), event.getSubgroupNo(), lotIds);
    }
}
