package com.cellmes.oee.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** OEE 引擎（ch07）：对齐种子数据 BC-001 A 班场景 + 微停阈值边界。 */
class OeeEngineTest {

    private static OeeEngine.Minute run(int output, int reject) {
        return new OeeEngine.Minute("run_time", output, reject, 1.0);
    }

    private static OeeEngine.Minute of(String category) {
        return new OeeEngine.Minute(category, 0, 0, 1.0);
    }

    @Test
    @DisplayName("BC-001 A 班 480 分钟：微停 7 分钟重分类，三因子与 OEE 精确匹配")
    void seedScenario_bc001ShiftA() {
        List<OeeEngine.Minute> minutes = new ArrayList<>();
        // 0-119 RUN
        for (int i = 0; i < 120; i++) {
            minutes.add(run(1, i < 6 ? 1 : 0));
        }
        // 120-122 IDLE（3 分钟 → 微停）
        for (int i = 0; i < 3; i++) {
            minutes.add(of("idle_time"));
        }
        // 123-199 RUN
        for (int i = 0; i < 77; i++) {
            minutes.add(run(1, 0));
        }
        // 200-201 IDLE（2 分钟 → 微停）
        minutes.add(of("idle_time"));
        minutes.add(of("idle_time"));
        // 202-259 RUN
        for (int i = 0; i < 58; i++) {
            minutes.add(run(1, 0));
        }
        // 260-319 MAT_WAIT（非计划停机 60）
        for (int i = 0; i < 60; i++) {
            minutes.add(of("unplanned_down"));
        }
        // 320-359 RUN
        for (int i = 0; i < 40; i++) {
            minutes.add(run(1, 0));
        }
        // 360-389 PM（计划停机 30，出分母）
        for (int i = 0; i < 30; i++) {
            minutes.add(of("planned_down"));
        }
        // 390-399 RUN
        for (int i = 0; i < 10; i++) {
            minutes.add(run(1, 0));
        }
        // 400-401 IDLE（2 分钟 → 微停）
        minutes.add(of("idle_time"));
        minutes.add(of("idle_time"));
        // 402-429 RUN
        for (int i = 0; i < 28; i++) {
            minutes.add(run(1, 0));
        }
        // 430-440 IDLE（11 分钟 → 不算微停）
        for (int i = 0; i < 11; i++) {
            minutes.add(of("idle_time"));
        }
        // 441-479 RUN
        for (int i = 0; i < 39; i++) {
            minutes.add(run(1, 0));
        }

        OeeEngine.OeeResult r = OeeEngine.compute(minutes, 5);

        // run = 372 + 微停 7 = 379；idle 剩 11；unplanned 60；planned 30（排除）
        assertThat(r.microStopMinutes()).isEqualTo(7);
        assertThat(r.runMinutes()).isEqualTo(379);
        assertThat(r.idleMinutes()).isEqualTo(11);
        assertThat(r.unplannedDownMinutes()).isEqualTo(60);
        assertThat(r.plannedDownMinutes()).isEqualTo(30);
        // plannedRunTime = 379 + 60 + 11 = 450 = 480 - plannedDown
        assertThat(r.plannedRunTime()).isEqualTo(450);
        assertThat(r.totalOutput()).isEqualTo(372);
        assertThat(r.totalReject()).isEqualTo(6);
        // availability = 379/450 = 0.8422
        assertThat(r.availability()).isCloseTo(0.8422, within(0.0001));
        // performance = 372/379 = 0.9815（微停的损失由 performance 吸收）
        assertThat(r.performance()).isCloseTo(0.9815, within(0.0001));
        // quality = (372-6)/372 = 0.9839
        assertThat(r.quality()).isCloseTo(0.9839, within(0.0001));
        // OEE = 366/450 = 0.8133
        assertThat(r.oee()).isCloseTo(0.8133, within(0.0001));
    }

    @Test
    @DisplayName("微停阈值边界：连续 idle 恰好 5 分钟不算微停，4 分钟算")
    void microStopThresholdBoundary() {
        List<OeeEngine.Minute> fiveIdle = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            fiveIdle.add(run(1, 0));
        }
        for (int i = 0; i < 5; i++) {
            fiveIdle.add(of("idle_time"));
        }
        for (int i = 0; i < 100; i++) {
            fiveIdle.add(run(1, 0));
        }
        assertThat(OeeEngine.compute(fiveIdle, 5).microStopMinutes()).isZero();

        List<OeeEngine.Minute> fourIdle = new ArrayList<>(fiveIdle);
        fourIdle.set(100, new OeeEngine.Minute("idle_time", 0, 0, 1.0));
        fourIdle.set(102, run(1, 0));
        assertThat(OeeEngine.compute(fourIdle, 5).microStopMinutes()).isEqualTo(4);
    }

    @Test
    @DisplayName("全 RUN 分钟：三因子全部为 1")
    void perfectRun() {
        List<OeeEngine.Minute> minutes = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            minutes.add(run(1, 0));
        }
        OeeEngine.OeeResult r = OeeEngine.compute(minutes, 5);
        assertThat(r.oee()).isCloseTo(1.0, within(0.0001));
    }
}
