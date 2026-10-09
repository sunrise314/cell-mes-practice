package com.cellmes.formation.engine;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 规则版容量预测器（ch10 思路 1：快速基线 + 特征权重评分）。
 * 这是 CapacityPredictor 的接口位实现：上 XGBoost（思路 2）时替换本类，
 * 预测/拦截/记录链路完全复用。
 *
 * 特征（化成柜报文 + 上游工序 SPC 数据）：
 * - coatThicknessDevPct  涂布面密度偏差 %（正=偏厚）
 * - fillQtyDevPct        注液量偏差 %
 * - cutoffCurrentMa      化成终止电流 mA（标准 50，偏大=未化透）
 * - ambientTempDevC      环境温度偏差 ℃
 * - selfDischargeMv      自放电 K 值 mV/h
 */
@Component
public class RuleCapacityPredictor {

    public double predict(Map<String, Object> features) {
        double coatDev = num(features, "coatThicknessDevPct");
        double fillDev = num(features, "fillQtyDevPct");
        double cutoffMa = num(features, "cutoffCurrentMa");
        double tempDev = num(features, "ambientTempDevC");
        double selfDischarge = num(features, "selfDischargeMv");

        // 规则评分（基线 280Ah，权重来自历史数据回归拟合的教学示例值）
        double predicted = 280.0
                - 2.0 * coatDev                       // 面密度偏厚 → 活性物过剩但利用率低
                - 1.5 * fillDev                       // 注液不足 → 容量直接损失
                - 0.02 * (cutoffMa - 50.0)            // 终止电流偏大 → 未化透
                - 0.3 * Math.abs(tempDev)             // 温偏 → 化成副反应
                - 0.5 * selfDischarge;                // K 值大 → 微短路风险
        return Math.round(predicted * 100) / 100.0;
    }

    private double num(Map<String, Object> features, String key) {
        Object v = features == null ? null : features.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
