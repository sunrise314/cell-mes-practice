package com.cellmes.spc.engine;

import com.cellmes.spc.engine.WesternElectricRuleEngine.Sample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 西电 8 规则正反例（ch08）。基线：mean=280, σ=1, UCL=283, LCL=277。 */
class WesternElectricRuleEngineTest {

    private static final double MEAN = 280.0;
    private static final double SIGMA = 1.0;
    private static final double UCL = 283.0;
    private static final double LCL = 277.0;
    private static final Set<String> ALL = Set.of("rule1", "rule2", "rule3", "rule4", "rule5", "rule6", "rule7", "rule8");

    private static Sample s(int i, double v) {
        return new Sample(i, v, null);
    }

    private static List<String> ruleIds(List<WesternElectricRuleEngine.RuleViolation> violations) {
        return violations.stream().map(WesternElectricRuleEngine.RuleViolation::ruleId).toList();
    }

    @Test
    @DisplayName("rule1：单点超出 ±3σ 报警，界内点不报")
    void rule1_beyondControlLimits() {
        var violations = WesternElectricRuleEngine.check(
                List.of(s(1, 283.5), s(2, 276.5), s(3, 280.0)), MEAN, SIGMA, UCL, LCL, ALL);
        assertThat(ruleIds(violations)).containsExactly("rule1", "rule1");
    }

    @Test
    @DisplayName("rule2：连续 9 点同侧报警，8 点不报")
    void rule2_nineInARowSameSide() {
        var nine = java.util.stream.IntStream.rangeClosed(1, 9).mapToObj(i -> s(i, 280.5)).toList();
        assertThat(ruleIds(WesternElectricRuleEngine.check(nine, MEAN, SIGMA, UCL, LCL, ALL)))
                .containsExactly("rule2");

        var eight = java.util.stream.IntStream.rangeClosed(1, 8).mapToObj(i -> s(i, 280.5)).toList();
        assertThat(WesternElectricRuleEngine.check(eight, MEAN, SIGMA, UCL, LCL, ALL)).isEmpty();
    }

    @Test
    @DisplayName("rule3：连续 6 点单调下降报警（一次连续段只报一次）")
    void rule3_sixPointTrend() {
        // 7 点单调下降：第 6 点触发一次，第 7 点不重复报
        var seven = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(i -> s(i, 280.5 - i * 0.2)).toList();
        var violations = WesternElectricRuleEngine.check(seven, MEAN, SIGMA, UCL, LCL, ALL);
        assertThat(ruleIds(violations)).containsExactly("rule3");

        var five = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(i -> s(i, 280.5 - i * 0.2)).toList();
        assertThat(WesternElectricRuleEngine.check(five, MEAN, SIGMA, UCL, LCL, ALL)).isEmpty();
    }

    @Test
    @DisplayName("rule4：连续 14 点交替上下报警")
    void rule4_alternating() {
        var values = new java.util.ArrayList<Sample>();
        for (int i = 1; i <= 14; i++) {
            values.add(s(i, i % 2 == 1 ? 280.8 : 279.2));
        }
        assertThat(ruleIds(WesternElectricRuleEngine.check(values, MEAN, SIGMA, UCL, LCL, ALL)))
                .contains("rule4");
    }

    @Test
    @DisplayName("rule5：3 点中 2 点超出同侧 2σ")
    void rule5_twoOfThreeBeyond2Sigma() {
        var violations = WesternElectricRuleEngine.check(
                List.of(s(1, 282.5), s(2, 280.0), s(3, 282.5)), MEAN, SIGMA, UCL, LCL, ALL);
        assertThat(ruleIds(violations)).contains("rule5");
    }

    @Test
    @DisplayName("rule6：5 点中 4 点超出同侧 1σ")
    void rule6_fourOfFiveBeyond1Sigma() {
        var violations = WesternElectricRuleEngine.check(
                List.of(s(1, 281.5), s(2, 281.5), s(3, 279.5), s(4, 281.5), s(5, 281.5)),
                MEAN, SIGMA, UCL, LCL, ALL);
        assertThat(ruleIds(violations)).contains("rule6");
    }

    @Test
    @DisplayName("rule7：连续 15 点在 ±1σ 之内（控制限过宽）")
    void rule7_fifteenWithin1Sigma() {
        var values = new java.util.ArrayList<Sample>();
        for (int i = 1; i <= 15; i++) {
            values.add(s(i, 280.0 + (i % 2 == 0 ? 0.5 : -0.5)));
        }
        assertThat(ruleIds(WesternElectricRuleEngine.check(values, MEAN, SIGMA, UCL, LCL, ALL)))
                .contains("rule7");
    }

    @Test
    @DisplayName("rule8：连续 8 点在 ±1σ 之外（两侧均可）")
    void rule8_eightBeyond1Sigma() {
        var values = new java.util.ArrayList<Sample>();
        for (int i = 1; i <= 8; i++) {
            values.add(s(i, i % 2 == 0 ? 281.5 : 278.5));
        }
        assertThat(ruleIds(WesternElectricRuleEngine.check(values, MEAN, SIGMA, UCL, LCL, ALL)))
                .contains("rule8");
    }

    @Test
    @DisplayName("未启用的规则不产生报警；σ=0 直接跳过")
    void disabledRulesAndZeroSigma() {
        var nine = java.util.stream.IntStream.rangeClosed(1, 9).mapToObj(i -> s(i, 280.5)).toList();
        assertThat(WesternElectricRuleEngine.check(nine, MEAN, SIGMA, UCL, LCL, Set.of("rule1")))
                .isEmpty();
        assertThat(WesternElectricRuleEngine.check(nine, MEAN, 0, UCL, LCL, ALL)).isEmpty();
    }
}
