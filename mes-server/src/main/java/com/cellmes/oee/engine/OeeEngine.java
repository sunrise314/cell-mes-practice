package com.cellmes.oee.engine;

import java.util.List;

/**
 * OEE 引擎（ch07，TR88.03 口径）—— 纯函数，不依赖 Spring/DB，方便单测。
 *
 * 时间口径：
 * - plannedDown（PM/换型）从分母排除；
 * - 连续 idle 段 < 微停阈值（默认 5 分钟）重分类为运行时间内的「微停」——
 *   微停不该算停机（availability 惩罚），但也不是满速（performance 惩罚自然吸收它）；
 * - plannedRunTime = run(含微停) + unplannedDown + 剩余 idle；
 * - availability = run / plannedRunTime；
 * - performance = Σ实际产出 / Σ(run 分钟 × standardRate)；
 * - quality = (out - reject) / out。
 */
public final class OeeEngine {

    private OeeEngine() {
    }

    /** 单分钟桶（已完成状态映射分类）。 */
    public record Minute(String timeCategory, int actualOutput, int rejectQty, double standardRate) {
    }

    public record OeeResult(
            long runMinutes, long idleMinutes, long unplannedDownMinutes, long plannedDownMinutes,
            long microStopMinutes, long plannedRunTime,
            long totalOutput, long totalReject,
            double availability, double performance, double quality, double oee) {
    }

    public static OeeResult compute(List<Minute> minutes, int microStopThresholdMin) {
        long run = 0, idle = 0, unplanned = 0, planned = 0, micro = 0;
        long output = 0, reject = 0;
        double ideal = 0;

        // 连续 idle 段检测（微停重分类）：先以哨兵暂存，段结束后按段长重分类
        int idleRun = 0;
        List<Minute> merged = new java.util.ArrayList<>(minutes.size());
        for (Minute m : minutes) {
            if ("idle_time".equals(m.timeCategory())) {
                idleRun++;
                merged.add(new Minute("__idle_run__", m.actualOutput(), m.rejectQty(), m.standardRate()));
            } else {
                micro += classifyIdleTail(merged, idleRun, microStopThresholdMin);
                idleRun = 0;
                merged.add(new Minute(m.timeCategory(), m.actualOutput(), m.rejectQty(), m.standardRate()));
            }
        }
        micro += classifyIdleTail(merged, idleRun, microStopThresholdMin);

        for (Minute m : merged) {
            output += m.actualOutput();
            reject += m.rejectQty();
            switch (m.timeCategory()) {
                case "run_time" -> {
                    run++;
                    ideal += m.standardRate();
                }
                case "idle_time" -> idle++;
                case "unplanned_down" -> unplanned++;
                case "planned_down" -> planned++;
                default -> throw new IllegalArgumentException("未知时间类别: " + m.timeCategory());
            }
        }

        long plannedRunTime = run + unplanned + idle;
        double availability = plannedRunTime == 0 ? 0 : (double) run / plannedRunTime;
        double performance = ideal == 0 ? 0 : output / ideal;
        double quality = output == 0 ? 0 : (double) (output - reject) / output;
        double oee = availability * performance * quality;

        return new OeeResult(run, idle, unplanned, planned, micro, plannedRunTime,
                output, reject, round4(availability), round4(performance), round4(quality), round4(oee));
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    /**
     * 收尾最近一段连续 idle 哨兵：段长 &lt; 阈值 → 重分类为 run_time（微停）；
     * 否则恢复为 idle_time。返回重分类为微停的分钟数。
     */
    private static long classifyIdleTail(List<Minute> merged, int idleRun, int thresholdMin) {
        if (idleRun <= 0) {
            return 0;
        }
        boolean microStop = idleRun < thresholdMin;
        for (int i = merged.size() - idleRun; i < merged.size(); i++) {
            Minute im = merged.get(i);
            merged.set(i, new Minute(microStop ? "run_time" : "idle_time",
                    im.actualOutput(), im.rejectQty(), im.standardRate()));
        }
        return microStop ? idleRun : 0;
    }
}
