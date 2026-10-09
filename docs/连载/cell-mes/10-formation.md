---
title: ch10 化成柜容量预测：Early Warning + 拦截策略 + 时序模型
slug: cell-mes-10-formation
---

## 事故现场：客户退货后才知道容量不够，化成时数据全在

事故 1 的复盘再深入一层——容量不够的根因是化成曲线参数变了（0.05C 截止变 0.07C），但**化成柜当时的数据全是有的**：电压曲线、电流曲线、温度曲线、化成截止时间，全都采上来了。问题是：**这些数据采上来后没人拿来做容量预测**。

复盘把化成柜 F1-012 的事故当周数据拉出来分析：

<table>
<thead><tr>
<th>化成阶段</th>
<th>电压规格</th>
<th>实际电压</th>
<th>偏差</th>
</tr></thead>
<tbody>
<tr>
<td>恒流阶段（0.05C）</td>
<td>3.65V @ 50% SoC</td>
<td>3.62V @ 50% SoC</td>
<td>-0.03V</td>
</tr>
<tr>
<td>恒压阶段（4.2V）</td>
<td>4.20V 维持</td>
<td>4.15V 维持</td>
<td>-0.05V</td>
</tr>
<tr>
<td>截止阶段（0.05C）</td>
<td>截止电流 0.05C = 14A</td>
<td>截止电流 0.07C = 19.6A</td>
<td>+5.6A</td>
</tr>
</tbody>
</table>

每个阶段的数据都有，**电压和电流都偏离规格**——恒压阶段实际 4.15V 比规格 4.20V 低 0.05V（说明化成不充分），截止电流实际 19.6A 比规格 14A 高 40%（说明提前截止了）。如果当时有预测模型，看到恒压阶段电压持续偏低 0.05V 就应该预测出"这柜电芯容量会偏低 5-10Ah"——**化成完成后 1 小时就能预测，不用等分容结果（化成完成到分容结果出来有 24 小时滞后）**。

更早的预测信号在化成开始后 30 分钟——化成柜恒流阶段电压曲线斜率：正常电芯斜率 0.012 V/min，事故电芯斜率 0.008 V/min（**低 33%**）。斜率低 33% 意味着电化学极化更严重，化成不充分，容量会偏低。**化成开始后 30 分钟就有预测信号，但当时没人看**。

事故根因：**化成柜数据采上来了，但只用于事后追溯（写报告）没用于事前预测（拦截）**。MES 把化成数据存到 IoTDB 时序库后，只在分容结果出来后回查化成曲线——这是**反应式**，不是**预测式**。主动防线应该是化成过程中实时预测容量，发现偏低立即拦截不让流向下工序。

## 排查：化成容量预测的三层缺口

<table>
<thead><tr>
<th>层</th>
<th>缺口</th>
<th>后果</th>
</tr></thead>
<tbody>
<tr>
<td>数据层</td>
<td>化成时序数据存了但没用</td>
<td>数据沉睡，无价值产出</td>
</tr>
<tr>
<td>模型层</td>
<td>没有容量预测模型</td>
<td>化成完成 24h 后才知容量不够</td>
</tr>
<tr>
<td>拦截层</td>
<td>预测偏低没有自动拦截</td>
<td>异常电芯流向下工序</td>
</tr>
</tbody>
</table>

### 容量预测的三种思路

业内化成容量预测有三种思路，按工程复杂度排序：

**思路 1：规则阈值法**。基于化成曲线的关键参数阈值判断，如"恒压阶段电压 < 4.18V 报警"、"截止电流 > 0.06C 报警"。简单但只能识别明显异常，对渐变漂移不敏感。

**思路 2：特征工程 + 机器学习**。从化成曲线提取特征（恒流斜率、恒压维持时间、截止电流、温度峰值等 20-30 个特征），用历史数据训练 XGBoost / 随机森林模型预测分容容量。中等复杂度，准确率 85-92%。

**思路 3：深度学习时序模型**。用 LSTM / Transformer 直接吃秒级化成电压电流曲线，端到端预测容量。最高复杂度，准确率 92-95%，但需要大量标注数据（> 10 万样本）和 GPU 推理资源。

事故 1 的电芯厂 A 当时有 2 年历史化成数据，约 50 万样本——思路 2 够用，思路 3 投资回报率不高。本期 MES 用思路 2，思路 1 作为快速基线，思路 3 留给后续迭代。

### 化成曲线的关键特征

事故 1 的预测信号分析发现，化成曲线的 5 个特征对容量预测有效：

<table>
<thead><tr>
<th>特征</th>
<th>物理意义</th>
<th>与容量相关性</th>
</tr></thead>
<tbody>
<tr>
<td>恒流阶段电压斜率</td>
<td>电化学极化程度</td>
<td>-0.78（强负相关）</td>
</tr>
<tr>
<td>恒压阶段维持时间</td>
<td>化成充分程度</td>
<td>+0.65（强正相关）</td>
</tr>
<tr>
<td>截止电流值</td>
<td>化成完成程度</td>
<td>-0.72（强负相关）</td>
</tr>
<tr>
<td>化成总能量</td>
<td>整体化学反应量</td>
<td>+0.85（极强正相关）</td>
</tr>
<tr>
<td>温度峰值</td>
<td>反应剧烈程度</td>
<td>-0.45（中等负相关）</td>
</tr>
</tbody>
</table>

恒流阶段电压斜率 -0.78 负相关：斜率越低，极化越严重，化成越不充分，容量越低。化成总能量 +0.85 极强正相关：总能量是电压×电流×时间的积分，能量越高化学反应越完全，容量越高。

## 底层原理：XGBoost 容量预测模型

### 特征工程

从化成曲线 IoTDB 时序数据提取 5 大类 23 个特征：

```python
import numpy as np
import pandas as pd
from tsfresh import extract_features

def extract_formation_features(curve_df):
    """
    curve_df: 化成曲线 DataFrame
        columns: time, voltage, current, temperature, stage
        stage: 'CC' (恒流) / 'CV' (恒压) / 'END' (截止)
    """
    features = {}

    # 1. 恒流阶段特征（CC stage）
    cc = curve_df[curve_df.stage == 'CC']
    features['cc_duration_min'] = (cc.time.max() - cc.time.min()).total_seconds() / 60
    features['cc_voltage_slope'] = np.polyfit(
        (cc.time - cc.time.min()).dt.total_seconds() / 60,
        cc.voltage, 1)[0]  # 斜率 V/min
    features['cc_voltage_mean'] = cc.voltage.mean()
    features['cc_voltage_std'] = cc.voltage.std()
    features['cc_current_mean'] = cc.current.mean()

    # 2. 恒压阶段特征（CV stage）
    cv = curve_df[curve_df.stage == 'CV']
    features['cv_duration_min'] = (cv.time.max() - cv.time.min()).total_seconds() / 60
    features['cv_voltage_mean'] = cv.voltage.mean()
    features['cv_voltage_deviation'] = 4.2 - cv.voltage.mean()  # 与目标 4.2V 偏差
    features['cv_current_slope'] = np.polyfit(
        (cv.time - cv.time.min()).dt.total_seconds() / 60,
        cv.current, 1)[0]  # 电流下降斜率

    # 3. 截止阶段特征（END stage）
    end = curve_df[curve_df.stage == 'END']
    features['end_current'] = end.current.iloc[-1] if len(end) > 0 else None
    features['end_current_deviation'] = features['end_current'] - 0.05 * 280  # 与规格偏差

    # 4. 全程特征
    features['total_duration_min'] = (curve_df.time.max() - curve_df.time.min()).total_seconds() / 60
    features['total_energy_wh'] = np.trapz(curve_df.voltage * curve_df.current,
                                            x=(curve_df.time - curve_df.time.min()).dt.total_seconds() / 3600)
    features['temp_peak'] = curve_df.temperature.max()
    features['temp_mean'] = curve_df.temperature.mean()
    features['temp_std'] = curve_df.temperature.std()

    # 5. 时序特征（用 tsfresh 自动提取）
    tsfresh_features = extract_features(curve_df[['time','voltage']],
                                         column_id='lot_id', column_sort='time',
                                         column_value='voltage')

    return {**features, **tsfresh_features}
```

### XGBoost 模型训练

```python
import xgboost as xgb
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, r2_score

def train_capacity_model(features_df, labels):
    """
    features_df: 特征 DataFrame (n_samples, n_features)
    labels: 实际分容容量 (n_samples,)
    """
    X_train, X_test, y_train, y_test = train_test_split(
        features_df, labels, test_size=0.2, random_state=42)

    model = xgb.XGBRegressor(
        n_estimators=300,
        max_depth=6,
        learning_rate=0.1,
        subsample=0.8,
        colsample_bytree=0.8,
        reg_alpha=0.1,
        reg_lambda=1.0,
        random_state=42
    )
    model.fit(X_train, y_train,
              eval_set=[(X_test, y_test)],
              early_stopping_rounds=20,
              verbose=False)

    y_pred = model.predict(X_test)
    mae = mean_absolute_error(y_test, y_pred)
    r2 = r2_score(y_test, y_pred)

    print(f"MAE: {mae:.2f} Ah | R²: {r2:.4f}")
    return model, mae, r2
```

### 模型性能与拦截策略

50 万历史样本训练后，模型性能：

<table>
<thead><tr>
<th>指标</th>
<th>数值</th>
</tr></thead>
<tbody>
<tr>
<td>MAE（平均绝对误差）</td>
<td>1.8 Ah</td>
</tr>
<tr>
<td>R²（决定系数）</td>
<td>0.91</td>
</tr>
<tr>
<td>准确率（误差 < 3Ah）</td>
<td>87%</td>
</tr>
<tr>
<td>误报率（实际 ≥ 275Ah 但预测 < 275Ah）</td>
<td>4.2%</td>
</tr>
<tr>
<td>漏报率（实际 < 275Ah 但预测 ≥ 275Ah）</td>
<td>1.8%</td>
</tr>
</tbody>
</table>

模型上线后，**漏报率 1.8%** 是关键——即 100 个真异常电芯里有 1.8 个被漏报。误报率 4.2% 可接受——误报会触发人工复核，多花工时但不漏料。

拦截策略分三档：

<table>
<thead><tr>
<th>预测容量</th>
<th>策略</th>
<th>动作</th>
</tr></thead>
<tbody>
<tr>
<td>≥ 280Ah</td>
<td>直接通过</td>
<td>流向下工序</td>
</tr>
<tr>
<td>275-280Ah</td>
<td>预警</td>
<td>Andon L4 + 人工复核</td>
</tr>
<tr>
<td>< 275Ah</td>
<td>自动 hold</td>
<td>不流向下工序 + Andon L3</td>
</tr>
</tbody>
</table>

## 正确姿势：化成容量预测系统

### 1. 实时预测架构

化成柜完成化成后 1 分钟内触发预测：

```java
@Service
public class CapacityPredictionService {
    @Autowired private IoTdbService ioTdbService;
    @Autowired private FeatureExtractor featureExtractor;
    @Autowired private XgBoostModel model;
    @Autowired private AndonService andonService;
    @Autowired private LotService lotService;

    @EventListener
    public void onFormationComplete(FormationCompleteEvent evt) {
        Long lotId = evt.getLotId();
        Long deviceId = evt.getDeviceId();

        // 1. 从 IoTDB 拉化成曲线
        TimeSeries curve = ioTdbService.queryFormationCurve(
            deviceId, lotId, evt.getStartTime(), evt.getEndTime());

        // 2. 提取特征
        Map<String, Double> features = featureExtractor.extract(curve);

        // 3. 预测容量
        double predictedCapacity = model.predict(features);
        double specLower = 275.0;
        double specUpper = 285.0;

        // 4. 拦截策略
        if (predictedCapacity < specLower) {
            // 自动 hold + Andon L3
            lotService.hold(lotId, "化成容量预测 " + predictedCapacity +
                " < 规格下限 " + specLower);
            andonService.call(AndonCall.builder()
                .source(AndonSource.AI_PREDICTION)
                .level(AndonLevel.L3)
                .lotId(lotId)
                .deviceId(deviceId)
                .title("化成容量预测异常: " + lotId +
                       " 预测 " + predictedCapacity + "Ah < 275Ah")
                .description("化成柜 " + deviceId + " | 预测容量 " +
                    predictedCapacity + " | 特征: " + features)
                .build());
        } else if (predictedCapacity < specLower + 5) {
            // 预警 + Andon L4
            andonService.call(AndonCall.builder()
                .source(AndonSource.AI_PREDICTION)
                .level(AndonLevel.L4)
                .lotId(lotId)
                .deviceId(deviceId)
                .title("化成容量预测预警: " + lotId +
                       " 预测 " + predictedCapacity + "Ah")
                .build());
        }

        // 5. 记录预测结果
        predictionMapper.insert(lotId, deviceId, predictedCapacity,
            features, evt.getStartTime());
    }
}
```

### 2. 模型版本管理与 A/B 测试

模型迭代时需要 A/B 测试，避免新模型直接上线后性能回退：

```sql
CREATE TABLE model_version (
    id BIGINT PRIMARY KEY,
    version VARCHAR(16) NOT NULL UNIQUE,    -- v1.0.0
    model_path VARCHAR(256) NOT NULL,      -- 模型文件路径
    mae DECIMAL(8,4),
    r2 DECIMAL(8,4),
    accuracy DECIMAL(8,4),
    false_alarm_rate DECIMAL(8,4),          -- 误报率
    miss_rate DECIMAL(8,4),                 -- 漏报率
    status VARCHAR(16) NOT NULL,           -- EXPERIMENT/STAGING/PRODUCTION/RETIRED
    created_at TIMESTAMP,
    promoted_at TIMESTAMP
);
```

```java
@Service
public class ModelVersionService {
    @Autowired private ModelVersionMapper mapper;

    public XgBoostModel getActiveModel() {
        ModelVersion v = mapper.findActive();
        return XgBoostModel.load(v.getModelPath());
    }

    @Transactional
    public void promote(String version) {
        // 把当前 PRODUCTION 设为 RETIRED
        mapper.retireProduction();
        // 把目标版本设为 PRODUCTION
        mapper.promote(version);
    }
}
```

### 3. 模型重训流水线

模型每周重训一次，用最近 90 天数据：

```python
def retrain_pipeline():
    # 1. 从 IoTDB 拉最近 90 天化成曲线
    curves = iotdb_client.query_recent(days=90)

    # 2. 从 PostgreSQL 拉对应分容结果（label）
    labels = psql_client.query_grading_results(days=90)

    # 3. 特征提取
    features_df = pd.DataFrame([extract_formation_features(c) for c in curves])

    # 4. 训练新模型
    model, mae, r2 = train_capacity_model(features_df, labels)

    # 5. 与当前模型对比
    current = model_repo.get_active()
    if mae < current.mae * 0.95 and r2 > current.r2 * 1.02:
        # 性能提升 5% 以上才上线
        model_repo.save(model, version=bump_version(current.version),
                        mae=mae, r2=r2, status='STAGING')
        # 等 1 周 A/B 测试后 promote
        schedule_ab_test()
    else:
        log("性能未提升，不重训")
```

## 数据说话：预测系统上线后拦截统计

预测系统上线 3 个月，拦截统计：

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
<td>化成完成到知容量时间</td>
<td>24 小时（分容结果）</td>
<td>1 分钟（预测）</td>
<td>-99.97%</td>
</tr>
<tr>
<td>容量不够电芯流出工序</td>
<td>月均 6.7 颗</td>
<td>0 颗</td>
<td>100% 拦截</td>
</tr>
<tr>
<td>自动 hold 异常电芯</td>
<td>不存在</td>
<td>22 次</td>
<td>0 异常流出</td>
</tr>
<tr>
<td>误报触发人工复核</td>
<td>不存在</td>
<td>4.2% 误报率</td>
<td>月均 28 次复核</td>
</tr>
<tr>
<td>漏报</td>
<td>不存在</td>
<td>1.8%</td>
<td>月均漏报 1.2 次</td>
</tr>
<tr>
<td>模型重训频率</td>
<td>不存在</td>
<td>周一次</td>
<td>持续优化</td>
</tr>
</tbody>
</table>

关键发现：

1. **化成完成 1 分钟内预测容量**——比传统等分容结果 24 小时快 1440 倍，**提前 24 小时拦截异常**。
2. **22 次自动 hold 中 18 次是真异常**，4 次是误报但被人工复核纠正——**有效拦截率 82%**。
3. **1.8% 漏报**——主要发生在化成曲线参数变更后第一柜（新参数下样本少，模型预测置信度低），通过 ch08 的 SPC 基线重置机制兜底。

## 面试怎么答：化成容量预测

**问：化成容量预测为什么不用深度学习 LSTM？**

答：3 个原因：① **样本量**——LSTM 端到端时序模型需要 10 万以上标注样本，电芯厂当时 50 万样本看似够，但化成曲线是 2 小时秒级数据 = 7200 点/样本，50 万 × 7200 = 36 亿数据点，训练成本高；② **可解释性**——XGBoost 特征重要性可解释（恒压维持时间贡献 35%、恒流斜率 28%、截止电流 22%），工程师能据此调工艺；LSTM 黑盒，工艺工程师不信任；③ **ROI**——XGBoost 准确率 91%，LSTM 准确率 92-95%，差 1-4 个百分点，但工程成本高 5-10 倍（GPU 推理 vs CPU 推理）。**本期选 XGBoost 是工程权衡**，LSTM 留给后续数据量到 200 万样本时迭代。

**问：化成曲线的 5 个核心特征是什么？为什么有效？**

答：5 个核心特征是：① 恒流阶段电压斜率 -0.78 相关——斜率低说明极化严重，化成不充分，容量低；② 恒压阶段维持时间 +0.65 相关——维持时间长说明化成充分；③ 截止电流值 -0.72 相关——电流大说明提前截止，化成不充分；④ 化成总能量 +0.85 相关——能量是 V×I×t 积分，能量高化学反应完全；⑤ 温度峰值 -0.45 相关——温度高反应剧烈但易副反应。5 个特征物理意义清晰，覆盖了化成的电化学全过程，**比单一参数（如截止电压）预测能力强 3-5 倍**。

**问：预测偏低自动 hold 会不会误伤正常电芯？**

答：会有误报，但控制在 4.2%——即 100 个正常电芯里有 4.2 个被误 hold。误报通过两层兜底：① hold 后 30 分钟内人工复核——质量工程师看化成曲线，判断是否真异常；② 累积 hold 数据回流模型——误报样本作为负样本进入下次重训，模型持续优化。事故 1 的损失 18.8 万 vs 误报成本（4.2% × 月产 12 万 × 30 元/颗人工复核 = 15 万元/月），**ROI 仍然正**——拦截 1 次事故省 18.8 万 vs 月度误报成本 15 万，省 3.8 万 + 客户信任度提升。

**问：模型重训为什么周一次？不能每天？**

答：3 个原因：① **数据量**——每天重训用最近 90 天数据 = 90 × 1500 = 13.5 万样本，与周一次用 90 × 1500 = 13.5 万样本是同一份数据，每天重训浪费算力；② **稳定性**——每天重训模型参数波动大，预测结果不稳定，运营难判断"这次预测偏低是模型问题还是真异常"；③ **A/B 测试**——新模型 STAGING 后需要 1 周 A/B 测试验证性能提升 5% 才 promote，每天重训跳过 A/B 测试风险高。周一次重训是**工程稳定性 vs 模型时效性的平衡**。

## 落地清单：化成容量预测工程化动作

1. **IoTDB 存化成秒级曲线**：每柜化成 7200 秒点，存 IoTDB 时序库，禁用 PostgreSQL 存秒级数据（性能灾难）。
2. **特征工程 23 个特征**：恒流斜率 + 恒压维持 + 截止电流 + 总能量 + 温度峰值 + tsfresh 自动特征，**禁用单一参数预测**。
3. **XGBoost 模型 + 周一次重训**：50 万样本训练，准确率 91%，A/B 测试性能提升 5% 才 promote。
4. **化成完成 1 分钟内触发预测**：EventListener 监听 FormationCompleteEvent，异步预测不阻塞化成柜控制。
5. **三档拦截策略**：< 275Ah 自动 hold + Andon L3；275-280Ah 预警 + Andon L4；≥ 280Ah 通过。
6. **模型版本管理**：EXPERIMENT/STAGING/PRODUCTION/RETIRED 四态，禁用直接覆盖生产模型。
7. **误报数据回流**：人工复核结果累积作为负样本，下次重训用，模型持续优化。
8. **黄金回归**：① 化成完成后 1 分钟内必须出预测结果；② 预测 < 275Ah 必须自动 hold；③ 模型 promote 前必须 A/B 测试 1 周；④ 误报样本必须回流下次重训；⑤ 模型重训必须用最近 90 天数据。

下一章我们离开预测层进入时序数据存储，看 IoTDB 怎么把秒级点位分级归档成分钟级/小时级 KPI——这是支撑化成预测模型、SPC 控制图、OEE 计算的底层数据基础设施。
