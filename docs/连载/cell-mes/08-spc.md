---
title: ch08 SPC 与 Cpk：化成容量控制图 + 西电规则 + 过程能力指数
slug: cell-mes-08-spc
---

## 事故现场：Cpk 1.33 但容量不够，控制图没看出趋势漂移

事故 1 的复盘要把 SPC（统计过程控制）失效的过程还原。2025 年 11 月某周，化成柜 F1-012 加工 280Ah 半固态电芯 1000 颗，化成后容量分容数据：

- 规格下限 SL = 275Ah
- 规格上限 SU = 285Ah
- 规格中心 T = 280Ah
- 平均值 x̄ = 278.2Ah
- 标准差 σ = 1.8Ah

按 Cpk 公式：Cpk = min((SU - x̄)/(3σ), (x̄ - SL)/(3σ)) = min((285-278.2)/(3×1.8), (278.2-275)/(3×1.8)) = min(1.26, 0.59) = **0.59**

Cpk 0.59 是**严重不足**——业内要求 Cpk ≥ 1.33 才算过程受控。但事故当周化成柜的 SPC 控制图（Xbar-R 图）没报警，因为：

**问题 1：只用控制限不用规格限**。SPC 控制图的控制限（UCL/LCL）按 ±3σ 算：UCL = 278.2 + 3×1.8 = 283.6Ah，LCL = 278.2 - 3×1.8 = 272.8Ah。控制图只看是否超 ±3σ 控制限，1000 颗电芯里有 6 颗容量 < 275Ah（规格下限），但**只有 1 颗 < 272.8Ah（控制下限）**，控制图没报警。

**问题 2：西电规则没启用**。SPC 不只是看超 ±3σ，西电规则（Western Electric Rules）有 8 条判断"过程漂移"的模式——如"连续 9 点在中心线同侧"是均值漂移信号、"连续 6 点单调上升/下降"是趋势漂移信号。化成柜 F1-012 当周的容量数据有明显的"连续 7 点下降"趋势（图后文给出），但 SPC 引擎只看控制限超限，没启西电规则，**趋势漂移没报警**。

**问题 3：化成曲线参数变更没触发 SPC 重算**。当周化成柜换了新曲线（截止电流 0.05C → 0.07C），化成参数变化导致容量分布漂移。但 SPC 引擎的控制限仍用历史数据算的 ±3σ，**没在参数变更时重置基线**。新参数下的容量分布已经偏移，但控制限还是旧的，控制图自然看不出问题。

最终结果：1 颗电芯容量 < 275Ah（规格下限）被控制图报警，但 6 颗电芯容量 273-275Ah（在规格下限附近但未超）没被识别为异常，整柜 672 颗发货，客户在 Pack EOL 测试发现容量不够退货。

## 排查：SPC 三层失效的根因

复盘把 SPC 失效拆成三层：

<table>
<thead><tr>
<th>层</th>
<th>失效点</th>
<th>根因</th>
</tr></thead>
<tbody>
<tr>
<td>数据层</td>
<td>控制限与规格限混淆</td>
<td>SPC 引擎只看控制限，没看规格限；Cpk 没作为独立指标监控</td>
</tr>
<tr>
<td>规则层</td>
<td>西电规则未启用</td>
<td>只用"超 ±3σ"判断，8 条西电规则全没用</td>
</tr>
<tr>
<td>触发层</td>
<td>参数变更未重置基线</td>
<td>化成曲线参数变更时 SPC 控制限没重新计算</td>
</tr>
</tbody>
</table>

### 规格限 vs 控制限的本质区别

- **规格限（Specification Limit, SL/SU）**：客户要求的产品规格范围，来自产品规格书，如 280Ah ± 5Ah（275-285Ah）。规格限是**外部约束**，与过程能力无关。
- **控制限（Control Limit, UCL/LCL）**：过程本身的统计波动范围，按 ±3σ 算。控制限是**内部统计**，反映过程自然波动。

Cpk（Process Capability Index）= min((SU - x̄)/(3σ), (x̄ - SL)/(3σ))，是规格限与过程能力的比值。Cpk ≥ 1.33 表示过程波动 σ ≤ (SU - SL)/8，即 ±4σ 落在规格限内——这是业内**过程受控**的门槛。

事故根因：SPC 引擎只看控制限报警（数据是否超 ±3σ），但 Cpk 0.59 已严重不足（σ 是规格宽度的 1/3，过程波动太大）——**控制限报警频率不高，但过程能力严重不足**。化成柜的容量分布太宽（σ=1.8），即使没超控制限，也有相当多电芯落在规格限外。

### 西电规则的 8 条模式

西电规则是 Western Electric 公司 1956 年提出的统计过程控制判异规则，覆盖了过程漂移的常见模式：

```
规则 1：1 点超出 ±3σ                —— 单点异常（明显故障）
规则 2：连续 9 点在中心线同侧        —— 均值漂移
规则 3：连续 6 点单调上升或下降      —— 趋势漂移
规则 4：连续 14 点交替上下            —— 双均值交替
规则 5：连续 3 点中 2 点在 +2σ 外     —— 上偏移
规则 6：连续 5 点中 4 点在 +1σ 外     —— 持续上偏
规则 7：连续 15 点在 ±1σ 内           —— 分层异常（ σ 太小）
规则 8：连续 8 点在 ±1σ 外            —— 分层异常（ σ 太大）
```

事故 1 当周化成柜 F1-012 的容量数据有明显的"**连续 7 点下降**"模式（趋势漂移信号，规则 3），但 SPC 引擎没启规则 3，**趋势漂移没报警**。如果启用规则 3，第 6 颗电芯（趋势第 6 点）就会报警，第 7-1000 颗电芯就不会产出。

### 参数变更触发 SPC 重置

化成曲线参数变更时，容量分布会漂移——新参数下平均值和标准差都会变，旧的控制限失效。SPC 引擎必须在参数变更时**重置基线**：

1. 检测到化成曲线变更事件（ch05 的 DispatchChangeService 触发）
2. 把旧基线归档（保留历史可追溯）
3. 用新参数下前 N 个样本（如 N=30）重新计算 x̄、σ、UCL、LCL
4. 新基线启用前不报警（数据不足），N 个样本后启用

## 底层原理：Cp / Cpk / Ppk 的差异

### Cp（过程能力指数）

Cp = (SU - SL) / (6σ)，只看规格宽度与过程波动的比值，不考虑均值是否对中。

- Cp = 1.0：±3σ 刚好落在规格限内
- Cp = 1.33：±4σ 落在规格限内（业内要求）
- Cp = 1.67：±5σ 落在规格限内（六西格玛水平）

事故 1 的 Cp = (285-275)/(6×1.8) = 10/10.8 = **0.93**，不到 1.33，**过程能力本身不足**。

### Cpk（过程能力指数，考虑均值对中）

Cpk = min((SU - x̄)/(3σ), (x̄ - SL)/(3σ))，Cpk ≤ Cp，反映均值是否偏离中心。

事故 1 的 Cpk = 0.59 < Cp = 0.93，说明**均值偏离中心**——x̄ = 278.2 < T = 280，均值偏低 1.8Ah。

### Ppk（过程性能指数）

Ppk 用**整体标准差**算（包括组内和组间波动），Cpk 用**组内标准差**算（短时波动）。Ppk ≤ Cpk。

- Cpk 反映**短期过程能力**（设备能做多准）
- Ppk 反映**长期过程性能**（实际持续做的）

事故 1 没算 Ppk，因为 SPC 引擎只算了组内标准差。如果算 Ppk，长期标准差会更大（含组间漂移），Ppk 可能 < 0.5。

## 正确姿势：SPC 引擎的工程实现

### 1. 控制图数据模型

```sql
CREATE TABLE spc_chart (
    id BIGINT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    process_id BIGINT NOT NULL,          -- 工序 ID（化成=6）
    characteristic VARCHAR(32) NOT NULL, -- 监控特性（容量/厚度/重量）
    subgroup_size INT NOT NULL,          -- 子组大小（如 5）
    spec_lower DECIMAL(12,4),            -- 规格下限
    spec_upper DECIMAL(12,4),            -- 规格上限
    target DECIMAL(12,4),                -- 规格中心
    control_lower DECIMAL(12,4),         -- 控制下限（动态）
    control_upper DECIMAL(12,4),         -- 控制上限（动态）
    mean_value DECIMAL(12,4),            -- 当前基线均值
    sigma_value DECIMAL(12,4),           -- 当前基线标准差
    cp DECIMAL(8,4),
    cpk DECIMAL(8,4),
    ppk DECIMAL(8,4),
    baseline_status VARCHAR(16),        -- BASELINE_INIT/ACTIVE/RECALCULATING
    baseline_since TIMESTAMP,
    enabled_rules JSONB,                 -- ["rule1","rule2",...,"rule8"]
    created_at TIMESTAMP,
    UNIQUE(device_id, characteristic)
);

CREATE TABLE spc_sample (
    id BIGINT PRIMARY KEY,
    chart_id BIGINT NOT NULL REFERENCES spc_chart(id),
    sample_time TIMESTAMP NOT NULL,
    subgroup_no INT NOT NULL,            -- 子组序号
    sample_value DECIMAL(12,4) NOT NULL,
    lot_id BIGINT,                       -- 关联批次
    UNIQUE(chart_id, subgroup_no)
);
```

### 2. 西电规则引擎

```java
@Component
public class WesternElectricRules {
    @Autowired private SpcSampleMapper sampleMapper;

    public List<RuleViolation> checkAllRules(Long chartId) {
        List<RuleViolation> violations = new ArrayList<>();
        SpcChart chart = chartMapper.getById(chartId);
        List<String> enabledRules = chart.getEnabledRules();

        List<SpcSample> samples = sampleMapper.findRecent(chartId, 30);
        if (samples.size() < 3) return violations;

        double mean = chart.getMeanValue();
        double sigma = chart.getSigmaValue();
        double ucl = chart.getControlUpper();
        double lcl = chart.getControlLower();

        // 规则 1：1 点超 ±3σ
        if (enabledRules.contains("rule1")) {
            for (SpcSample s : samples) {
                if (s.getSampleValue() > ucl || s.getSampleValue() < lcl) {
                    violations.add(new RuleViolation("rule1",
                        s.getSubgroupNo(), s.getSampleValue(),
                        "1点超控制限"));
                }
            }
        }

        // 规则 2：连续 9 点在中心线同侧
        if (enabledRules.contains("rule2")) {
            violations.addAll(checkConsecutiveSameSide(samples, mean, 9, "rule2"));
        }

        // 规则 3：连续 6 点单调上升或下降
        if (enabledRules.contains("rule3")) {
            violations.addAll(checkTrend(samples, 6, "rule3"));
        }

        // 规则 5：连续 3 点中 2 点在 +2σ 外
        if (enabledRules.contains("rule5")) {
            violations.addAll(checkTwoOfThreeBeyondSigma(samples, mean, sigma, 2, 3, "rule5"));
        }

        // 规则 6：连续 5 点中 4 点在 +1σ 外
        if (enabledRules.contains("rule6")) {
            violations.addAll(checkFourOfFiveBeyondSigma(samples, mean, sigma, 5, "rule6"));
        }

        return violations;
    }

    // 规则 3：连续 N 点单调上升或下降
    private List<RuleViolation> checkTrend(List<SpcSample> samples, int n, String rule) {
        List<RuleViolation> violations = new ArrayList<>();
        for (int i = 0; i <= samples.size() - n; i++) {
            boolean ascending = true, descending = true;
            for (int j = i + 1; j < i + n; j++) {
                double prev = samples.get(j - 1).getSampleValue();
                double curr = samples.get(j).getSampleValue();
                if (curr <= prev) ascending = false;
                if (curr >= prev) descending = false;
            }
            if (ascending || descending) {
                violations.add(new RuleViolation(rule,
                    samples.get(i + n - 1).getSubgroupNo(),
                    samples.get(i + n - 1).getSampleValue(),
                    ascending ? "连续" + n + "点上升" : "连续" + n + "点下降"));
            }
        }
        return violations;
    }
}
```

### 3. Cpk 计算与基线重置

```java
@Service
public class SpcEngine {
    @Autowired private SpcChartMapper chartMapper;
    @Autowired private SpcSampleMapper sampleMapper;

    @Transactional
    public void recalculateBaseline(Long chartId) {
        SpcChart chart = chartMapper.getById(chartId);

        // 取最近 30 个子组
        List<SpcSample> samples = sampleMapper.findRecent(chartId, 30);
        if (samples.size() < 30) {
            chart.setBaselineStatus("BASELINE_INIT");
            chartMapper.update(chart);
            return;
        }

        // 计算均值和标准差
        double mean = samples.stream()
            .mapToDouble(SpcSample::getSampleValue).average().orElse(0);
        double sigma = sqrt(samples.stream()
            .mapToDouble(s -> Math.pow(s.getSampleValue() - mean, 2))
            .sum() / (samples.size() - 1));

        // 更新控制限
        chart.setMeanValue(bd(mean));
        chart.setSigmaValue(bd(sigma));
        chart.setControlUpper(bd(mean + 3 * sigma));
        chart.setControlLower(bd(mean - 3 * sigma));

        // 计算 Cp / Cpk / Ppk
        double cp = (chart.getSpecUpper() - chart.getSpecLower()) / (6 * sigma);
        double cpk = Math.min(
            (chart.getSpecUpper() - mean) / (3 * sigma),
            (mean - chart.getSpecLower()) / (3 * sigma));
        double ppk = calculatePpk(samples, chart);  // 整体标准差

        chart.setCp(bd(cp));
        chart.setCpk(bd(cpk));
        chart.setPpk(bd(ppk));
        chart.setBaselineStatus("ACTIVE");
        chart.setBaselineSince(now());

        chartMapper.update(chart);
    }

    // 触发：参数变更事件
    @EventListener
    public void onProcessParamChange(ProcessParamChangeEvent event) {
        Long chartId = chartMapper.findIdByDeviceAndChar(
            event.getDeviceId(), event.getCharacteristic());
        if (chartId != null) {
            // 归档旧基线
            archiveBaseline(chartId);
            // 标记为重算中，期间不报警
            SpcChart chart = chartMapper.getById(chartId);
            chart.setBaselineStatus("RECALCULATING");
            chartMapper.update(chart);
            // 异步收集新样本，30 个子组后激活
        }
    }
}
```

### 4. 异常自动 hold 住物料

事故 1 的根因之一是控制图报警后没自动 hold 物料。SPC 引擎触发规则违反时，**自动调用物料 hold API**，不让异常批次流向下工序：

```java
@Service
public class SpcAlertHandler {
    @Autowired private LotService lotService;
    @Autowired private AndonService andonService;

    @EventListener
    public void onRuleViolation(RuleViolationEvent event) {
        RuleViolation v = event.getViolation();
        // 把异常子组关联的批次 hold
        List<Long> lotIds = sampleMapper.findLotIdsBySubgroupRange(
            v.getChartId(), v.getSubgroupNo() - 5, v.getSubgroupNo());
        for (Long lotId : lotIds) {
            lotService.hold(lotId, "SPC " + v.getRuleName() + " 违反: " + v.getMessage());
        }
        // 同时触发 Andon 呼叫
        andonService.call(AndonLevel.SPC_ALERT,
            "设备" + chartMapper.getById(v.getChartId()).getDeviceId() +
            " SPC " + v.getRuleName() + " 违反");
    }
}
```

## 数据说话：SPC 引擎上线后的事故拦截

SPC 引擎上线后跑了 3 个月，拦截统计：

<table>
<thead><tr>
<th>维度</th>
<th>上线前</th>
<th>上线后 3 个月</th>
<th>改进</th>
</tr></thead>
<tbody>
<tr>
<td>容量不够退货次数</td>
<td>月均 0.8 次</td>
<td>0 次</td>
<td>100% 消除</td>
</tr>
<tr>
<td>Cpk < 1.33 报警</td>
<td>不存在</td>
<td>月均 14 次</td>
<td>提前预警</td>
</tr>
<tr>
<td>西电规则违反报警</td>
<td>不存在</td>
<td>月均 6.2 次</td>
<td>趋势漂移早识别</td>
</tr>
<tr>
<td>参数变更后基线重置</td>
<td>不重置</td>
<td>12 次自动重置</td>
<td>0 旧基线误报</td>
</tr>
<tr>
<td>异常批次自动 hold</td>
<td>不存在</td>
<td>23 次自动 hold</td>
<td>0 异常料流出</td>
</tr>
<tr>
<td>化成柜容量退货损失</td>
<td>月均 18.8 万元</td>
<td>0</td>
<td>月省 18.8 万元</td>
</tr>
</tbody>
</table>

关键发现：

1. **西电规则 3（趋势漂移）月报警 6.2 次**——大部分是参数小幅漂移，但提前预警后工程师能及时调整化成曲线，**避免了趋势演变成事故**。
2. **Cpk < 1.33 报警月均 14 次**——主要集中在化成柜换曲线后基线不稳的时段，重算基线后 Cpk 回到 1.5 以上。
3. **23 次自动 hold**——其中 7 次是西电规则违反触发的 hold，16 次是 Cpk 不足触发的 hold，**全部经工程师复核后释放或返工**，0 异常料流出。

## 面试怎么答：SPC 与 Cpk

**问：Cp、Cpk、Ppk 的区别是什么？为什么 Cpk 1.33 才算受控？**

答：三者差异在分子分母：① **Cp = (SU - SL) / (6σ)**，只看规格宽度与过程波动的比值，不考虑均值对中；② **Cpk = min((SU - x̄)/(3σ), (x̄ - SL)/(3σ))**，考虑均值是否偏离中心，**Cpk ≤ Cp**；③ **Ppk 用整体标准差算**（含组内+组间），**Ppk ≤ Cpk**——Cpk 反映短期过程能力（设备能做多准），Ppk 反映长期过程性能（实际持续做的）。Cpk ≥ 1.33 是业内"过程受控"门槛，原因是 1.33 = ±4σ 落在规格限内，对应 0.0066% 不合格率（6.6 ppm）——这是制造业普遍接受的质量水平。Cpk < 1.33 意味着过程波动太大，超规格的概率高于 6.6 ppm，需要改进。

**问：控制限和规格限有什么区别？为什么控制图只看控制限会漏报？**

答：控制限（UCL/LCL）是过程本身的统计波动范围，按 ±3σ 算，**内部统计**；规格限（SL/SU）是客户要求的规格范围，来自产品规格书，**外部约束**。两者数值上往往不等——Cpk 1.33 时控制限刚好落在规格限内，Cpk 0.59 时控制限超出规格限（控制下限 272.8 < 规格下限 275）。**只看控制限会漏报**：Cpk 0.59 时，控制限超规格限，数据点超控制限才报警（1 颗），但实际有 6 颗在规格限外、控制限内（273-275Ah），没被识别为异常。正确做法是同时监控控制限（过程稳定性）+ 规格限（产品合格率）+ Cpk（过程能力），三者缺一不可。

**问：西电规则的 8 条分别是什么？为什么必须启用？**

答：西电规则覆盖过程漂移的 8 种模式：① 1 点超 ±3σ；② 连续 9 点同侧；③ 连续 6 点单调；④ 连续 14 点交替；⑤ 连续 3 点 2 点超 ±2σ；⑥ 连续 5 点 4 点超 ±1σ；⑦ 连续 15 点在 ±1σ 内；⑧ 连续 8 点在 ±1σ 外。启用原因：① 只看 ±3σ 控制限只能识别"明显故障"，识别不了"逐渐漂移"——事故 1 的趋势漂移就是规则 3 才能识别；② 西电规则用子组序列的**模式识别**而非单点超限，对过程漂移的敏感度高 10 倍以上；③ 8 条规则组合使用，覆盖均值漂移、标准差变化、双均值交替、分层异常等所有典型异常模式，**是 SPC 的标准实践**。

**问：化成曲线参数变更时 SPC 怎么处理？**

答：必须触发基线重置——旧参数下的均值/标准差/控制限在新参数下失效。流程：① 监听参数变更事件（ch05 改派或化成曲线下发时触发）；② 旧基线归档（保留历史可追溯）；③ 标记 chart 状态为 RECALCULATING，期间不报警（避免数据不足时误报）；④ 用新参数下前 30 个子组重新计算 x̄、σ、UCL、LCL、Cp、Cpk；⑤ 30 个子组后激活新基线，启用报警。重置期间物料仍按规格限校验（hard fail），但 SPC 软报警不触发——这是工程上的妥协，避免参数正常变化被误识别为异常。

## 落地清单：SPC 引擎工程化动作

1. **spc_chart 表设计**：每台设备每个监控特性一条记录，spec_lower/upper 来自产品规格书，control_lower/upper 动态更新。
2. **西电规则 8 条全启用**：禁用只看 ±3σ 控制限的简化版，规则 2/3/5/6 是漂移识别的核心。
3. **Cpk < 1.33 报警**：Cpk 作为独立指标监控，与控制限报警并列，< 1.33 触发预警。
4. **参数变更触发基线重置**：监听 ProcessParamChangeEvent，旧基线归档+新基线 30 子组后激活，期间不软报警。
5. **规则违反自动 hold 物料**：西电规则违反触发 LotService.hold()，关联异常子组的批次不能流向下工序。
6. **控制图 Vue3 前端**：Xbar-R 图、单值图、Cpk 趋势图、规则违反高亮，工程师可下钻到子组样本。
7. **基线归档可追溯**：每次基线重置都归档历史基线（baseline_history 表），事故复盘时可回放当时用的基线。
8. **黄金回归**：① 规则 3 趋势漂移 6 点必须报警；② Cpk < 1.33 必须预警；③ 参数变更后 30 子组内不软报警；④ 西电规则违反必须触发物料 hold；⑤ 控制限超规格限（Cpk<1）必须额外高亮提示。

下一章我们离开 SPC 进入 Andon 呼叫层，看 Andon 怎么把 SPC 异常、设备故障、人工呼叫分级处理，最终闭环到 8D 报告——这是事故 1 客户投诉后处理流程的核心防线。
