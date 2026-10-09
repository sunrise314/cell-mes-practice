package com.cellmes.formation.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 规则版容量预测器（ch10）。 */
class RuleCapacityPredictorTest {

    private final RuleCapacityPredictor predictor = new RuleCapacityPredictor();

    @Test
    @DisplayName("零偏差特征 → 基线 280Ah")
    void baselineCapacity() {
        Map<String, Object> features = new HashMap<>();
        features.put("coatThicknessDevPct", 0.0);
        features.put("fillQtyDevPct", 0.0);
        features.put("cutoffCurrentMa", 50.0);   // 标准终止电流
        features.put("ambientTempDevC", 0.0);
        features.put("selfDischargeMv", 0.0);
        assertThat(predictor.predict(features)).isCloseTo(280.0, within(0.001));
    }

    @Test
    @DisplayName("负向特征叠加 → 预测值线性下降")
    void negativeFeaturesReduceCapacity() {
        Map<String, Object> features = new HashMap<>();
        features.put("coatThicknessDevPct", 2.0);   // -4.0
        features.put("fillQtyDevPct", 1.0);         // -1.5
        features.put("cutoffCurrentMa", 60.0);      // -0.02 × 10 = -0.2
        features.put("ambientTempDevC", 1.0);       // -0.3
        features.put("selfDischargeMv", 0.5);       // -0.25
        // 280 - 4 - 1.5 - 0.2 - 0.3 - 0.25 = 273.75 → 低于 275 应触发 HOLD
        double predicted = predictor.predict(features);
        assertThat(predicted).isCloseTo(273.75, within(0.001));
        assertThat(predicted).isLessThan(275.0);
    }

    @Test
    @DisplayName("缺失特征按 0 处理，不抛异常")
    void missingFeaturesTolerated() {
        Map<String, Object> features = new HashMap<>();
        features.put("fillQtyDevPct", -1.0); // 注液超量 +1.5；cutoff 缺失按 0 → -0.02×(0-50) = +1.0
        assertThat(predictor.predict(features)).isCloseTo(282.5, within(0.001));
    }
}
