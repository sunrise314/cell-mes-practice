package com.cellmes.spc.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 西电规则引擎（ch08）—— 纯函数实现 Nelson/WE 8 规则，不依赖 Spring/DB。
 * 输入按 subgroup 顺序排列的样本 + 当前基线（mean/σ/UCL/LCL）+ 启用规则集。
 * 每条 run 类规则一次连续段只报一次（在触发长度恰好达到时标记）。
 */
public final class WesternElectricRuleEngine {

    private WesternElectricRuleEngine() {
    }

    public record Sample(int subgroupNo, double value, Long lotId) {
    }

    public record RuleViolation(String ruleId, int subgroupNo, double value, String description) {
    }

    public static List<RuleViolation> check(List<Sample> samples, double mean, double sigma,
                                            double ucl, double lcl, Set<String> enabled) {
        List<RuleViolation> violations = new ArrayList<>();
        if (samples.isEmpty() || sigma <= 0) {
            return violations;
        }
        int n = samples.size();
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            v[i] = samples.get(i).value();
        }

        // rule1：1 点出界（±3σ 之外）
        if (enabled.contains("rule1")) {
            for (Sample s : samples) {
                if (s.value() > ucl || s.value() < lcl) {
                    violations.add(new RuleViolation("rule1", s.subgroupNo(), s.value(),
                            "单点超出控制限 [" + lcl + ", " + ucl + "]"));
                }
            }
        }

        // rule2：连续 9 点在中心线同侧
        if (enabled.contains("rule2")) {
            int upRun = 0, downRun = 0;
            for (int i = 0; i < n; i++) {
                if (v[i] > mean) {
                    upRun++;
                    downRun = 0;
                } else if (v[i] < mean) {
                    downRun++;
                    upRun = 0;
                } else {
                    upRun = 0;
                    downRun = 0;
                }
                if (upRun == 9 || downRun == 9) {
                    violations.add(new RuleViolation("rule2", samples.get(i).subgroupNo(), v[i],
                            "连续 9 点位于均值 " + (upRun == 9 ? "上方" : "下方")));
                }
            }
        }

        // rule3：连续 6 点持续上升或下降
        if (enabled.contains("rule3")) {
            int incRun = 1, decRun = 1;
            for (int i = 1; i < n; i++) {
                incRun = v[i] > v[i - 1] ? incRun + 1 : 1;
                decRun = v[i] < v[i - 1] ? decRun + 1 : 1;
                if (incRun == 6 || decRun == 6) {
                    violations.add(new RuleViolation("rule3", samples.get(i).subgroupNo(), v[i],
                            "连续 6 点持续" + (incRun == 6 ? "上升" : "下降") + "趋势"));
                }
            }
        }

        // rule4：连续 14 点交替上下
        if (enabled.contains("rule4")) {
            int altRun = 1;
            int lastDir = 0;
            for (int i = 1; i < n; i++) {
                int dir = Double.compare(v[i], v[i - 1]);
                if (dir != 0 && dir == -lastDir) {
                    altRun++;
                } else if (dir != 0) {
                    altRun = 2;
                } else {
                    altRun = 1;
                }
                lastDir = dir != 0 ? dir : lastDir;
                if (altRun == 14) {
                    violations.add(new RuleViolation("rule4", samples.get(i).subgroupNo(), v[i],
                            "连续 14 点交替上下"));
                }
            }
        }

        // rule5：连续 3 点中 2 点落在同侧 2σ 之外
        if (enabled.contains("rule5")) {
            checkKofN(samples, v, mean, sigma, violations, 2, 3, "rule5", 2.0);
        }

        // rule6：连续 5 点中 4 点落在同侧 1σ 之外
        if (enabled.contains("rule6")) {
            checkKofN(samples, v, mean, sigma, violations, 4, 5, "rule6", 1.0);
        }

        // rule7：连续 15 点在 ±1σ 之内（控制限过宽，需重算基线）
        if (enabled.contains("rule7")) {
            int run = 0;
            for (int i = 0; i < n; i++) {
                run = Math.abs(v[i] - mean) < sigma ? run + 1 : 0;
                if (run == 15) {
                    violations.add(new RuleViolation("rule7", samples.get(i).subgroupNo(), v[i],
                            "连续 15 点位于 ±1σ 之内"));
                }
            }
        }

        // rule8：连续 8 点在 ±1σ 之外（两侧均可）
        if (enabled.contains("rule8")) {
            int run = 0;
            for (int i = 0; i < n; i++) {
                run = Math.abs(v[i] - mean) > sigma ? run + 1 : 0;
                if (run == 8) {
                    violations.add(new RuleViolation("rule8", samples.get(i).subgroupNo(), v[i],
                            "连续 8 点位于 ±1σ 之外"));
                }
            }
        }
        return violations;
    }

    /** rule5/rule6 通式：最近 N 点中 ≥K 点落在同侧 zσ 之外，在窗口末端标记一次。 */
    private static void checkKofN(List<Sample> samples, double[] v, double mean, double sigma,
                                  List<RuleViolation> violations, int k, int nWin, String ruleId, double z) {
        for (int end = nWin - 1; end < v.length; end++) {
            int up = 0, down = 0;
            for (int i = end - nWin + 1; i <= end; i++) {
                double zScore = (v[i] - mean) / sigma;
                if (zScore > z) {
                    up++;
                } else if (zScore < -z) {
                    down++;
                }
            }
            if (up >= k || down >= k) {
                violations.add(new RuleViolation(ruleId, samples.get(end).subgroupNo(), v[end],
                        "最近 " + nWin + " 点中 " + k + " 点落在同侧 " + z + "σ 之外"));
            }
        }
    }
}
