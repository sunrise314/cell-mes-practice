package com.cellmes.spc.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.common.domain.BizException;
import com.cellmes.common.event.ProcessParamChangeEvent;
import com.cellmes.common.event.RuleViolationEvent;
import com.cellmes.common.tenant.TenantContext;
import com.cellmes.spc.entity.BaselineHistory;
import com.cellmes.spc.entity.SpcChart;
import com.cellmes.spc.entity.SpcSample;
import com.cellmes.spc.engine.WesternElectricRuleEngine;
import com.cellmes.spc.engine.WesternElectricRuleEngine.Sample;
import com.cellmes.spc.mapper.BaselineHistoryMapper;
import com.cellmes.spc.mapper.SpcChartMapper;
import com.cellmes.spc.mapper.SpcSampleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * SPC 服务（ch08）：样本录入 → 规则判定 → 事件发布；基线计算（Cp/Cpk/Ppk）与归档。
 * RECALCULATING 期间不跑软报警（防参数切换期误报）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpcService {

    private final SpcChartMapper chartMapper;
    private final SpcSampleMapper sampleMapper;
    private final BaselineHistoryMapper baselineHistoryMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public SpcChart getChart(String deviceCode, String characteristic) {
        SpcChart chart = chartMapper.selectOne(Wrappers.<SpcChart>lambdaQuery()
                .eq(SpcChart::getDeviceCode, deviceCode)
                .eq(characteristic != null, SpcChart::getCharacteristic, characteristic)
                .last("limit 1"));
        if (chart == null) {
            throw new BizException(404, "控制图不存在: " + deviceCode);
        }
        return chart;
    }

    public ChartDetail detail(Long chartId) {
        SpcChart chart = chartMapper.selectById(chartId);
        if (chart == null) {
            throw new BizException(404, "控制图不存在: id=" + chartId);
        }
        ChartDetail detail = new ChartDetail();
        detail.setChart(chart);
        detail.setSamples(sampleMapper.selectList(Wrappers.<SpcSample>lambdaQuery()
                .eq(SpcSample::getChartId, chartId).orderByAsc(SpcSample::getSubgroupNo)));
        detail.setViolations(checkViolations(chart, detail.getSamples()));
        return detail;
    }

    @Data
    public static class ChartDetail {
        private SpcChart chart;
        private List<SpcSample> samples;
        private List<WesternElectricRuleEngine.RuleViolation> violations;
    }

    /** 样本录入：插入后立即跑规则判定。 */
    @Transactional
    public SpcSample addSample(Long chartId, double value, Long lotId) {
        SpcChart chart = chartMapper.selectById(chartId);
        if (chart == null) {
            throw new BizException(404, "控制图不存在: id=" + chartId);
        }
        Integer maxSubgroup = sampleMapper.selectList(Wrappers.<SpcSample>lambdaQuery()
                        .eq(SpcSample::getChartId, chartId).orderByDesc(SpcSample::getSubgroupNo).last("limit 1"))
                .stream().findFirst().map(SpcSample::getSubgroupNo).orElse(0);
        SpcSample sample = new SpcSample();
        sample.setChartId(chartId);
        sample.setSampleTime(LocalDateTime.now());
        sample.setSubgroupNo(maxSubgroup + 1);
        sample.setSampleValue(BigDecimal.valueOf(value));
        sample.setLotId(lotId);
        sampleMapper.insert(sample);
        checkViolations(chart, List.of());
        return sample;
    }

    /** 规则判定：仅 ACTIVE 基线执行（BASELINE_INIT/RECALCULATING 静默）。 */
    public List<WesternElectricRuleEngine.RuleViolation> checkViolations(SpcChart chart, List<SpcSample> samples) {
        if (!"ACTIVE".equals(chart.getBaselineStatus())) {
            return List.of();
        }
        if (samples == null || samples.isEmpty()) {
            samples = sampleMapper.selectList(Wrappers.<SpcSample>lambdaQuery()
                    .eq(SpcSample::getChartId, chart.getId()).orderByAsc(SpcSample::getSubgroupNo));
        }
        List<Sample> engineSamples = samples.stream()
                .map(s -> new Sample(s.getSubgroupNo(), s.getSampleValue().doubleValue(), s.getLotId()))
                .toList();
        Set<String> enabled = parseRules(chart.getEnabledRules());
        List<WesternElectricRuleEngine.RuleViolation> violations = WesternElectricRuleEngine.check(
                engineSamples,
                chart.getMeanValue().doubleValue(),
                chart.getSigmaValue().doubleValue(),
                chart.getControlUpper().doubleValue(),
                chart.getControlLower().doubleValue(),
                enabled);

        for (WesternElectricRuleEngine.RuleViolation violation : violations) {
            Long lotId = samples.stream()
                    .filter(s -> s.getSubgroupNo() == violation.subgroupNo())
                    .findFirst()
                    .map(SpcSample::getLotId)
                    .orElse(null);
            eventPublisher.publishEvent(new RuleViolationEvent(
                    TenantContext.get(), chart.getId(), chart.getDeviceCode(), chart.getProcessId(),
                    chart.getCharacteristic(), violation.ruleId(), violation.subgroupNo(),
                    violation.value(), lotId, violation.description()));
        }
        return violations;
    }

    /** 基线重算：≥30 样本 → mean/σ/UCL/LCL/Cp/Cpk/Ppk；旧基线归档（可回放）。 */
    @Transactional
    public SpcChart recalculateBaseline(Long chartId) {
        SpcChart chart = chartMapper.selectById(chartId);
        if (chart == null) {
            throw new BizException(404, "控制图不存在: id=" + chartId);
        }
        List<SpcSample> samples = sampleMapper.selectList(Wrappers.<SpcSample>lambdaQuery()
                .eq(SpcSample::getChartId, chartId).orderByAsc(SpcSample::getSubgroupNo));
        if (samples.size() < 30) {
            throw new BizException(409, "样本不足 30 个（当前 " + samples.size() + "），禁止重算基线");
        }
        double[] v = samples.stream().mapToDouble(s -> s.getSampleValue().doubleValue()).toArray();
        double mean = java.util.Arrays.stream(v).average().orElse(0);
        double variance = java.util.Arrays.stream(v).map(x -> (x - mean) * (x - mean)).sum() / (v.length - 1);
        double sigma = Math.sqrt(variance);
        if (sigma <= 0) {
            throw new BizException(409, "σ=0，无法计算控制限");
        }
        double su = chart.getSpecUpper().doubleValue();
        double sl = chart.getSpecLower().doubleValue();
        double ucl = mean + 3 * sigma;
        double lcl = mean - 3 * sigma;
        double cp = (su - sl) / (6 * sigma);
        double cpk = Math.min(su - mean, mean - sl) / (3 * sigma);

        // 旧基线归档
        archive(chart, "RECALC");

        chart.setMeanValue(round4(mean));
        chart.setSigmaValue(round4(sigma));
        chart.setControlUpper(round4(ucl));
        chart.setControlLower(round4(lcl));
        chart.setCp(round4(cp));
        chart.setCpk(round4(cpk));
        chart.setPpk(round4(cpk)); // 教学简化：组内 σ ≈ 总体 σ
        chart.setBaselineStatus("ACTIVE");
        chart.setBaselineSince(LocalDateTime.now());
        chartMapper.updateById(chart);
        log.info("[SPC 基线重算] chart={} mean={} σ={} UCL={} LCL={} Cpk={}",
                chartId, mean, sigma, ucl, lcl, cpk);
        return chart;
    }

    /** 参数变更事件（ch01/ch08）：归档旧基线 + 进入 RECALCULATING（期间不软报警）。 */
    @org.springframework.context.event.EventListener
    @Transactional
    public void onParamChange(ProcessParamChangeEvent event) {
        List<SpcChart> charts = chartMapper.selectList(Wrappers.<SpcChart>lambdaQuery()
                .eq(SpcChart::getDeviceCode, event.getDeviceCode())
                .eq(event.getProcessId() != null, SpcChart::getProcessId, event.getProcessId()));
        for (SpcChart chart : charts) {
            archive(chart, "PARAM_CHANGE");
            chart.setBaselineStatus("RECALCULATING");
            chartMapper.updateById(chart);
            log.warn("[SPC 基线失效] chart={} 原因=参数集 {} 升级到 v{}，进入 RECALCULATING",
                    chart.getId(), event.getParamSet(), event.getVersion());
        }
    }

    private void archive(SpcChart chart, String reason) {
        BaselineHistory history = new BaselineHistory();
        history.setChartId(chart.getId());
        history.setMeanValue(chart.getMeanValue());
        history.setSigmaValue(chart.getSigmaValue());
        history.setControlLower(chart.getControlLower());
        history.setControlUpper(chart.getControlUpper());
        history.setArchiveReason(reason);
        baselineHistoryMapper.insert(history);
    }

    private Set<String> parseRules(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            return Set.copyOf(objectMapper.readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (Exception e) {
            throw new BizException(500, "enabled_rules JSON 解析失败: " + json);
        }
    }

    private BigDecimal round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
