package com.cellmes.formation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cellmes.andon.AndonLevel;
import com.cellmes.andon.service.AndonService;
import com.cellmes.common.domain.BizException;
import com.cellmes.common.event.FormationCompleteEvent;
import com.cellmes.formation.engine.RuleCapacityPredictor;
import com.cellmes.formation.entity.CapacityPrediction;
import com.cellmes.formation.entity.ModelVersion;
import com.cellmes.formation.mapper.CapacityPredictionMapper;
import com.cellmes.formation.mapper.ModelVersionMapper;
import com.cellmes.trace.service.LotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 容量预测服务（ch10）：化成完成事件 → 预测 → 三档拦截。
 * < 275 HOLD（Andon L3，批次自动锁定）；275-280 WARN（Andon L4，人工复核）；≥ 280 PASS。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapacityPredictionService {

    private final ModelVersionMapper modelVersionMapper;
    private final CapacityPredictionMapper predictionMapper;
    private final RuleCapacityPredictor predictor;
    private final LotService lotService;
    private final AndonService andonService;
    private final ObjectMapper objectMapper;

    @Value("${mes.formation.spec-lower:275.0}")
    private double specLower;

    @Value("${mes.formation.pass-upper:280.0}")
    private double passUpper;

    /** 化成完成事件监听（发布线程内同步执行，保证事务一致）。 */
    @EventListener
    @Transactional
    public CapacityPrediction onFormationComplete(FormationCompleteEvent event) {
        ModelVersion model = modelVersionMapper.selectOne(Wrappers.<ModelVersion>lambdaQuery()
                .eq(ModelVersion::getStatus, "PRODUCTION")
                .orderByDesc(ModelVersion::getId)
                .last("limit 1"));
        if (model == null) {
            throw new BizException(500, "无 PRODUCTION 状态的容量预测模型（fail-closed）");
        }

        double predicted = predictor.predict(event.getFeatures());
        String action;
        if (predicted < specLower) {
            action = "HOLD";
            lotService.hold(event.getLotId());
            andonService.createCall("AI_PREDICTION", AndonLevel.L3, event.getDeviceCode(), 6,
                    event.getLotId(),
                    "容量预测低于下限 " + predicted + "Ah < " + specLower + "Ah",
                    "模型 " + model.getVersion() + " 预测批次 " + event.getLotId()
                            + " 容量不足，已自动 hold，禁止流入分容工序");
        } else if (predicted < passUpper) {
            action = "WARN";
            andonService.createCall("AI_PREDICTION", AndonLevel.L4, event.getDeviceCode(), 6,
                    event.getLotId(),
                    "容量预测进入预警带 " + predicted + "Ah",
                    "模型 " + model.getVersion() + " 预测批次 " + event.getLotId()
                            + " 处于 [" + specLower + "," + passUpper + ") 预警带，请人工复核");
        } else {
            action = "PASS";
        }

        CapacityPrediction record = new CapacityPrediction();
        record.setLotId(event.getLotId());
        record.setDeviceCode(event.getDeviceCode());
        record.setPredictedCapacity(BigDecimal.valueOf(predicted));
        try {
            record.setFeatures(objectMapper.writeValueAsString(event.getFeatures()));
        } catch (Exception e) {
            record.setFeatures("{}");
        }
        record.setAction(action);
        record.setPredictedAt(LocalDateTime.now());
        predictionMapper.insert(record);
        log.info("[容量预测] lot={} predicted={} action={} model={}",
                event.getLotId(), predicted, action, model.getVersion());
        return record;
    }
}
