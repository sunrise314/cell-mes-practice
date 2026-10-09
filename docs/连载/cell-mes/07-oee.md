---
title: ch07 OEE 三因子：TR88.03 停机编码 + 24h 班次口径 + 可用率修正
slug: cell-mes-07-oee
---

## 事故现场：OEE 88% 是假的，辊压线实际只跑了 77%，少交 12 万颗

事故 2 的复盘要把 OEE 报表造假的全过程还原。2025 年 12 月运营会上，厂长拿到月度 OEE 报表：

<table>
<thead><tr>
<th>工序</th>
<th>OEE</th>
</tr></thead>
<tbody>
<tr>
<td>涂布</td>
<td>88%</td>
</tr>
<tr>
<td>辊压</td>
<td>91%</td>
</tr>
<tr>
<td>分切</td>
<td>93%</td>
</tr>
<tr>
<td>卷绕</td>
<td>87%</td>
</tr>
<tr>
<td>化成</td>
<td>92%</td>
</tr>
</tbody>
</table>

厂长很满意——88% 是行业标杆水平（业内 OEE 标杆 85%，一线厂如宁德溧阳工厂公开数据是 82-85%）。但下工序辊压线当月却报"涂布极片缺料 437 小时等料"——437 小时相当于 18 天没生产。涂布 OEE 88% 又辊压等料 437 小时，**两个数字矛盾**：涂布都 88% 跑得飞起，怎么辊压还会等料？

厂长让 IE（工业工程师）小王深挖。小王把涂布机 BC-001 当月的 SCADA 日志拉出来，按 1 分钟一个数据点重算 OEE。算完发现：

- **真实可用率**：71.2%（报表 87%）
- **真实表现率**：89.5%（报表 92%）
- **真实质量率**：98.7%（报表 99%）
- **真实 OEE** = 0.712 × 0.895 × 0.987 = **62.9%**（报表 88%）

报表把 OEE 抬高了 25 个百分点，**根因是把"待料停机"算成了"计划停机"**。SCADA 的设备状态字段值有 9 种：`RUN` / `IDLE` / `PM` / `CHG` / `MAT_WAIT` / `ALARM` / `OFF` / `MECH` / `ELEC`，MES 报表口径把 `MAT_WAIT`（待料）和 `PM`（保养）、`CHG`（换卷）一起算"计划停机"——按 OEE 标准，**计划停机从可用率分母中扣除**，待料算非计划停机扣可用率。把待料塞进计划停机，**可用率分母变小，可用率变大**。

按真实 OEE 62.9% 反推产能：60 PPM × 1440 min/天 × 30 天 × 0.629 = **163 万片/月**，但报表显示产能 224 万片/月（按 88% 算），**差 61 万片 = 12 万颗电芯**——这就是事故 2 的"辊压等料 437 小时"的源头，涂布少产了 12 万片极片。

## 排查：OEE 三因子的标准口径

小王查了 OEE 的国际标准——**ISA-95 TR88.03**（也叫 OEE Standard）明确定义 OEE 三因子：

### 可用率 Availability = 实际运行时间 / 计划运行时间

- **计划运行时间** = 总时间 - 计划停机（计划停机：保养 PM、换型 CHG、计划停产 OFF_PLAN）
- **实际运行时间** = 计划运行时间 - 非计划停机（非计划停机：设备故障 MECH/ELEC、待料 MAT_WAIT、报警 ALARM）

事故根因：报表把 `MAT_WAIT`（待料）归到了"计划停机"，但 TR88.03 明确——**待料是非计划停机**，因为待料不在原排产计划里，是异常事件。换型（CHG）属于计划停机，因为换型是排产计划里就预期的。

### 表现率 Performance = 实际产出 / 标准产出

- **标准产出** = 实际运行时间 × 标准节拍
- **实际产出** = 实际下料数量
- 表现率 < 100% 的原因：设备降速、微停（< 5min 的短停机）、空转

报表把表现率从 89.5% 抬到 92%，根因是**微停没扣**——SCADA 日志里 1-3 分钟的短暂停机（如换料等待、操作员休息）被算成了"运行时间"，但实际没产出。

### 质量率 Quality = 合格产出 / 实际产出

- 合格产出 = 实际产出 - 不合格
- 质量率 < 100% 的原因：废品、返工

报表把质量率从 98.7% 抬到 99%，根因是**返工算成了合格**——涂布有少量极片返修（边部切掉重涂），返修后合格但报表直接算合格没扣返工。

### TR88.03 的停机编码体系

TR88.03 把停机按"原因大类→原因中类→原因小类"三层编码：

```
[1 计划停机]     1.1 保养 PM       1.1.1 日保养 / 1.1.2 周保养 / 1.1.3 月保养
                 1.2 换型 CHG       1.2.1 换卷 / 1.2.2 换料 / 1.2.3 换工艺
                 1.3 计划停产 OFF   1.3.1 节假日 / 1.3.2 计划检修

[2 非计划停机]   2.1 设备故障       2.1.1 机械 MECH / 2.1.2 电气 ELEC / 2.1.3 软件
                 2.2 待料 MAT_WAIT  2.2.1 上工序缺料 / 2.2.2 中间品缺 / 2.2.3 包材缺
                 2.3 报警 ALARM     2.3.1 工艺超限 / 2.3.2 安全 / 2.3.3 质量
                 2.4 人员 MAN       2.4.1 缺人 / 2.4.2 换班

[3 微停空转]     3.1 微停           3.1.1 <5min 短停（不算非计划停机，但扣表现率）
                 3.2 空转           3.2.1 设备运行但无产出
```

事故 2 的根因就是**停机编码错配**——MAT_WAIT 应该是 2.2，被算成了 1.x。

## 底层原理：OEE 计算的时间模型

### 时间桶模型

OEE 计算的输入是 SCADA 的设备状态时间序列。把 1 个月按 1 分钟一个桶切（43200 个桶），每个桶的状态字段决定它属于哪个时间类别：

```python
# 状态转时间类别的映射表
STATUS_TO_TIME = {
    "RUN":       "run_time",        # 实际运行
    "IDLE":      "idle_time",       # 空转（扣表现率）
    "PM":        "planned_down",    # 计划停机
    "CHG":       "planned_down",    # 换型计划停机
    "OFF":       "planned_down",    # 计划停产
    "MAT_WAIT":  "unplanned_down",  # 待料非计划停机
    "ALARM":     "unplanned_down",  # 报警非计划停机
    "MECH":      "unplanned_down",  # 机械故障非计划停机
    "ELEC":      "unplanned_down",  # 电气故障非计划停机
}

# 微停识别：< 5min 的 IDLE 算微停，扣表现率
def detect_micro_stop(idle_segments):
    micro_stops = [seg for seg in idle_segments if seg.duration_min < 5]
    return micro_stops
```

### 24h 班次口径的复杂性

电芯厂 A 是三班倒——A 班 08:00-16:00、B 班 16:00-24:00、C 班 00:00-08:00，每班 8 小时。OEE 的"总时间"按 24h × 30 天 = 720 小时/月算，但**班次交接 5 分钟不交接好就出 bug**：

- 24:00 是 B 班结束还是 C 班开始？报表按"开始时间归属班次"算，24:00 算 C 班开始
- 班次交接时设备状态切换可能跳变，需要做"班次开始 5 分钟内 IDLE 不算本班责任"
- 节假日停产（如国庆 7 天）从总时间中扣除，不算计划停机，**直接从分母中拿掉**

工程上的解法是引入 `shift_calendar` 表配置班次日历：

```sql
CREATE TABLE shift_calendar (
    id BIGINT PRIMARY KEY,
    shift_date DATE NOT NULL,
    shift_code VARCHAR(8) NOT NULL,    -- A/B/C/OFF
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    crew_id BIGINT,                     -- 班组
    is_holiday BOOLEAN DEFAULT FALSE,
    UNIQUE(shift_date, shift_code)
);
```

OEE 计算按 shift_calendar 的总时间累加，节假日直接排除，班次交接无歧义。

### OEE 计算引擎核心 SQL

```sql
-- 月度 OEE 计算（按设备+班次+工序聚合）
SELECT
    mm.device_id,
    mm.shift_code,
    -- 可用率
    SUM(CASE WHEN s.time_category = 'run_time' THEN mm.minutes ELSE 0 END) AS run_min,
    SUM(CASE WHEN s.time_category = 'planned_down' THEN mm.minutes ELSE 0 END) AS planned_down_min,
    SUM(CASE WHEN s.time_category = 'unplanned_down' THEN mm.minutes ELSE 0 END) AS unplanned_down_min,
    SUM(CASE WHEN s.time_category = 'micro_stop' THEN mm.minutes ELSE 0 END) AS micro_stop_min,
    -- 表现率
    SUM(mm.actual_output) AS actual_output,
    SUM(mm.actual_output) * s.standard_rate AS standard_output,
    -- 质量率
    SUM(mm.actual_output - mm.reject_qty) AS good_output
FROM scada_status_minute mm
JOIN status_mapping s ON s.scada_status = mm.scada_status
JOIN shift_calendar sc ON sc.shift_date = mm.shift_date AND sc.shift_code = mm.shift_code
WHERE mm.shift_date BETWEEN '2025-12-01' AND '2025-12-31'
  AND sc.is_holiday = FALSE
GROUP BY mm.device_id, mm.shift_code;
```

## 正确姿势：OEE 计算引擎的工程实现

### 1. 状态映射表 + 工序特化

不同工序的 SCADA 状态字段值不一样（涂布是 RUN/STOP，化成是 1/2/3），必须用 status_mapping 表配置每个工序的状态映射：

```sql
CREATE TABLE status_mapping (
    id BIGINT PRIMARY KEY,
    process_id BIGINT NOT NULL,        -- 工序 ID
    scada_status VARCHAR(32) NOT NULL, -- SCADA 原始状态值
    time_category VARCHAR(32) NOT NULL -- run_time/planned_down/unplanned_down/micro_stop/idle
);
-- 涂布工序映射
INSERT INTO status_mapping (process_id, scada_status, time_category) VALUES
(1, 'RUN', 'run_time'),
(1, 'IDLE', 'idle_time'),
(1, 'PM', 'planned_down'),
(1, 'CHG', 'planned_down'),
(1, 'MAT_WAIT', 'unplanned_down'),    -- 关键：待料是非计划
(1, 'ALARM', 'unplanned_down'),
(1, 'MECH', 'unplanned_down'),
(1, 'ELEC', 'unplanned_down');
```

### 2. 微停识别算法

微停（< 5min 的 IDLE）必须单独识别，因为它既不算非计划停机（太短不计入报表），也不能算运行时间（实际没产出）。识别算法在 OEE 引擎 Service 层实现：

```java
@Service
public class OeeEngine {
    @Autowired private ScadaStatusMinuteMapper minuteMapper;

    public OeeResult calculate(Long deviceId, LocalDate from, LocalDate to) {
        List<ScadaStatusMinute> minutes = minuteMapper.findByDeviceAndDate(deviceId, from, to);

        long runMin = 0, plannedDownMin = 0, unplannedDownMin = 0, microStopMin = 0, idleMin = 0;
        long actualOutput = 0, rejectQty = 0;
        BigDecimal standardRate = getStandardRate(deviceId);

        // 微停识别：连续 IDLE 段，duration < 5min 算微停
        List<IdleSegment> idleSegments = extractIdleSegments(minutes);
        Set<Integer> microStopMinuteIdx = new HashSet<>();
        for (IdleSegment seg : idleSegments) {
            if (seg.durationMin < 5) {
                for (int i = seg.startIdx; i <= seg.endIdx; i++) {
                    microStopMinuteIdx.add(i);
                }
            }
        }

        for (int i = 0; i < minutes.size(); i++) {
            ScadaStatusMinute m = minutes.get(i);
            String cat = statusMappingService.getCategory(m.getProcessId(), m.getScadaStatus());

            if (microStopMinuteIdx.contains(i)) {
                microStopMin++;
            } else {
                switch (cat) {
                    case "run_time": runMin++; break;
                    case "planned_down": plannedDownMin++; break;
                    case "unplanned_down": unplannedDownMin++; break;
                    case "idle_time": idleMin++; break;
                }
            }
            actualOutput += m.getActualOutput();
            rejectQty += m.getRejectQty();
        }

        // OEE 三因子计算
        long plannedRunTime = runMin + unplannedDownMin + idleMin + microStopMin;
        BigDecimal availability = bd(runMin).divide(bd(plannedRunTime), 4, RoundingMode.HALF_UP);
        BigDecimal standardOutput = bd(runMin).multiply(standardRate);
        BigDecimal performance = bd(actualOutput).divide(standardOutput, 4, RoundingMode.HALF_UP);
        BigDecimal quality = bd(actualOutput - rejectQty).divide(bd(actualOutput), 4, RoundingMode.HALF_UP);
        BigDecimal oee = availability.multiply(performance).multiply(quality);

        return new OeeResult(availability, performance, quality, oee);
    }
}
```

### 3. 班次日历与节假日

```java
@Service
public class ShiftCalendarService {
    @Autowired private ShiftCalendarMapper mapper;

    public List<ShiftCalendar> getShifts(LocalDate from, LocalDate to) {
        return mapper.findByDateRange(from, to);
    }

    public long calculateTotalMinutes(Long deviceId, LocalDate from, LocalDate to) {
        // 按班次日历累加总时间，节假日排除
        List<ShiftCalendar> shifts = getShifts(from, to);
        return shifts.stream()
            .filter(s -> !s.isHoliday())
            .filter(s -> s.getCrewId() != null)  // 没排班的不算
            .mapToLong(s -> Duration.between(s.getStartTime(), s.getEndTime()).toMinutes())
            .sum();
    }
}
```

### 4. 停机编码可追溯

每个非计划停机必须有"原因大类→中类→小类"三层编码，运营在 Andon 看板或停机登记界面填，OEE 报表按编码维度下钻：

```sql
CREATE TABLE downtime_event (
    id BIGINT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    duration_min INT NOT NULL,
    reason_level1 VARCHAR(32) NOT NULL,   -- 设备故障/待料/报警/人员
    reason_level2 VARCHAR(32),             -- 机械/电气/上工序缺料/...
    reason_level3 VARCHAR(64),             -- 具体原因描述
    operator_id BIGINT,                    -- 登记人
    shift_code VARCHAR(8)
);
```

## 数据说话：OEE 口径修正前后对比

口径修正上线后跑了一个月，对比事故前后的 OEE 数字：

<table>
<thead><tr>
<th>工序</th>
<th>报表 OEE</th>
<th>真实 OEE</th>
<th>差值</th>
<th>主要偏差来源</th>
</tr></thead>
<tbody>
<tr>
<td>涂布</td>
<td>88%</td>
<td>62.9%</td>
<td>-25.1%</td>
<td>待料误入计划停机</td>
</tr>
<tr>
<td>辊压</td>
<td>91%</td>
<td>78.4%</td>
<td>-12.6%</td>
<td>待料 + 微停</td>
</tr>
<tr>
<td>分切</td>
<td>93%</td>
<td>82.1%</td>
<td>-10.9%</td>
<td>微停 + 空转</td>
</tr>
<tr>
<td>卷绕</td>
<td>87%</td>
<td>74.5%</td>
<td>-12.5%</td>
<td>待料 + 报警</td>
</tr>
<tr>
<td>化成</td>
<td>92%</td>
<td>81.2%</td>
<td>-10.8%</td>
<td>待料 + 报警</td>
</tr>
<tr>
<td>分容</td>
<td>95%</td>
<td>88.5%</td>
<td>-6.5%</td>
<td>微停（最小偏差）</td>
</tr>
</tbody>
</table>

修正后的 OEE 数字与业内标杆对标：

<table>
<thead><tr>
<th>工序</th>
<th>修正后 OEE</th>
<th>业内标杆</th>
<th>差距</th>
</tr></thead>
<tbody>
<tr>
<td>涂布</td>
<td>62.9%</td>
<td>85%</td>
<td>-22.1%</td>
</tr>
<tr>
<td>辊压</td>
<td>78.4%</td>
<td>88%</td>
<td>-9.6%</td>
</tr>
<tr>
<td>分切</td>
<td>82.1%</td>
<td>90%</td>
<td>-7.9%</td>
</tr>
<tr>
<td>卷绕</td>
<td>74.5%</td>
<td>87%</td>
<td>-12.5%</td>
</tr>
<tr>
<td>化成</td>
<td>81.2%</td>
<td>90%</td>
<td>-8.8%</td>
</tr>
</tbody>
</table>

关键发现：

1. **涂布是最大瓶颈**——修正后 62.9%，距标杆 22 个百分点。根因是涂布机 BC-001/002 经常等上工序粉料（粉料供应不上），这表明问题不在涂布设备本身，在供应链。
2. **卷绕差距第二大**——卷绕 8 台设备中 WND-003 真实 OEE 仅 58%，是异常设备，需 IE 重点分析。
3. **微停的影响被低估**——分切修正前 93% 修正后 82.1%，11 个百分点里 7 个是微停没扣。微停看似每段只 1-3 分钟，但累计起来月度可达 1500 分钟（25 小时）。

## 面试怎么答：OEE 与停机编码

**问：OEE 三因子的标准定义是什么？为什么 88% OEE 可能是假的？**

答：OEE = 可用率 × 表现率 × 质量率，定义来自 ISA-95 TR88.03。可用率 = 实际运行时间 / 计划运行时间，其中计划运行时间 = 总时间 - 计划停机（保养、换型、计划停产），实际运行时间 = 计划运行时间 - 非计划停机（故障、待料、报警）。**88% OEE 是假的根因是待料停机（MAT_WAIT）被误归为计划停机**——待料不在排产计划里，是异常事件，应该归非计划停机扣可用率。误归后，可用率分母变小（计划运行时间变长），可用率变大，OEE 抬高。事故 2 涂布真实 OEE 62.9% 被报表抬到 88%，差 25 个百分点，**月产能少交 12 万颗电芯**。

**问：微停（< 5min）怎么处理？算运行时间还是停机？**

答：既不算运行时间也不算非计划停机，单独作为微停扣表现率。原因：① 微停太短不计入停机报表（< 5min 的设备停启多是 SCADA 状态抖动，不一定是真停机），但实际没产出，算运行时间会抬高表现率；② 微停也不计入非计划停机，否则停机事件数爆炸（一个班次可能 100 次微停，停机报表看不过来）。算法：连续 IDLE 段 duration < 5min 算微停，扣表现率分母（standard_output 用 run_min，不含 micro_stop_min）。微停的影响累计可观——分切工序 11 个百分点偏差里 7 个是微停没扣，月度累计 1500 分钟（25 小时）。

**问：班次交接的 OEE 怎么算？24:00 是 B 班还是 C 班？**

答：按"开始时间归属班次"原则——24:00 是 C 班的开始不算 B 班的结束。班次日历表 shift_calendar 配置每个班次的起止时间，OEE 计算按 shift_calendar 累加总时间，**避免交接时刻的歧义**。班次交接的 5 分钟 IDLE 有特殊规则——班次开始 5 分钟内的 IDLE 不算本班责任（前班交接延迟），但这部分仍计入总时间分母，只是不计入本班停机原因。节假日直接从总时间排除（is_holiday=true 的班次不算总时间），不算计划停机——节假日不是停机事件，是日历事件。

**问：OEE 计算的输入数据从哪来？怎么保证 SCADA 状态准确？**

答：SCADA 每秒推一次设备状态到 MES（ch04 采集层），MES 把秒级状态按 1 分钟聚合（取众数 mode）存入 scada_status_minute 表。OEE 计算从 scada_status_minute 读 1 个月 43200 行数据，按状态映射表转 time_category，累加各时间类别分钟数。**SCADA 状态准确性的关键**是设备端 PLC 的状态机设计——PLC 必须有明确的状态字段（RUN/IDLE/PM/ALARM/...），不能有"未定义"状态。SCADA 工程师部署设备时配置 OPC UA NodeID 或 Modbus 寄存器地址映射，MES 在 status_mapping 表配置每个工序的状态映射规则。**映射错配（如把 MAT_WAIT 算成 PM）就是事故 2 的根因**。

## 落地清单：OEE 引擎工程化动作

1. **status_mapping 表配置**：每个工序独立配置 SCADA 状态到 time_category 的映射，禁用全局统一映射（涂布和化成的状态字段值不一样）。
2. **待料 MAT_WAIT 必算非计划停机**：TR88.03 标准，待料不在排产计划里，是异常事件，扣可用率。
3. **微停算法 < 5min 单独识别**：连续 IDLE 段 duration < 5min 算微停，扣表现率，不算非计划停机。
4. **班次日历 shift_calendar**：A/B/C/OFF 班次日历配置，节假日 is_holiday=true 直接从总时间排除。
5. **停机编码三层**：reason_level1/2/3 三层编码，每个非计划停机必须填到 level3，OEE 报表按编码维度下钻。
6. **1 分钟桶聚合**：SCADA 秒级数据按 1 分钟聚合（取众数），OEE 计算从分钟表读，禁用秒级数据算 OEE（性能灾难）。
7. **OEE 看板按设备+班次+工序聚合**：支持多维下钻（设备级、班次级、工序级、月/周/日）。
8. **黄金回归**：① 待料 MAT_WAIT 必须算 unplanned_down 不算 planned_down；② 微停 < 5min 必须扣表现率不扣可用率；③ 节假日 is_holiday=true 必须从总时间排除不算计划停机；④ 班次交接 24:00 必须归 C 班开始不归 B 班结束。

下一章我们离开 OEE 进入质量管控层，看 SPC 西电规则怎么在化成容量异常时自动 hold 住一柜料——这是事故 1 化成容量不够的核心防线。
