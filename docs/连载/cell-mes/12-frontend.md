---
title: ch12 Vue3 看板与简易 APS：Andon 大屏 + OEE 实时图 + 化成瓶颈排产
slug: cell-mes-12-frontend
---

## 事故现场：厂长上班要看 8 个系统，APS 用 Excel 排产漏掉化成瓶颈

事故复盘要把 MES 前端的工程坑还原。ch00-ch11 全部功能落地后，MES 后端能力齐全（BOM/Routing/Dispatch/采集/SPC/OEE/Andon/化成预测/IoTDB），但**前端没整合**：

**问题 1：厂长上班看 8 个系统**。厂长早上 8 点上班，要打开 SCADA 看板（设备实时状态）、MES 派工单页面、SPC 控制图、OEE 报表（Excel 导出）、Andon 历史记录、化成预测结果（独立看板）、ERP 工单、SCADA 报警中心——**8 个 tab 切来切去**，厂长说"我看个产线状态要切 8 个 tab，这 MES 是给我增加工作量还是减少？"。

**问题 2：APS 排产用 Excel**。APS（Advanced Planning and Scheduling）是排产系统，电芯厂 A 当时的 APS 是计划员用 Excel 排——周一早上排本周的 7 段工艺派工单，按工单优先级 + 设备可用性人工排。**漏掉化成瓶颈**——化成柜 96 台但每柜 12 小时一柜电芯，是 7 段工艺的瓶颈（其他工序都是分钟级节拍，化成是小时级），计划员排产时没把化成柜能力作为约束，导致**前工序产出堆积在 WIP（在制品）区，化成柜排不上**——周一排的 1000 颗 X-280 工单，涂布周二就产完，化成柜周三才能上，**WIP 区堆了 1000 颗裸芯 24 小时**。

**问题 3：Andon 看板不在车间大屏显示**。ch09 的 Andon 系统做了，但只在 MES 后台页面里，**车间没大屏**——操作员看不到 Andon 呼叫，响应慢。事故 1 的客诉 Andon L3 呼叫发出后，化成车间没人看到，质量工程师 2 小时后才在后台看到。

## 排查：前端整合与 APS 瓶颈识别

复盘把前端问题拆成三层：

<table>
<thead><tr>
<th>层</th>
<th>缺口</th>
<th>后果</th>
</tr></thead>
<tbody>
<tr>
<td>数据层</td>
<td>8 个系统 8 套 API</td>
<td>厂长切 tab 切到崩溃</td>
</tr>
<tr>
<td>视图层</td>
<td>车间没 Andon 大屏</td>
<td>呼叫响应慢</td>
</tr>
<tr>
<td>排产层</td>
<td>APS 用 Excel + 无瓶颈约束</td>
<td>WIP 堆积、化成瓶颈漏识别</td>
</tr>
</tbody>
</table>

### MES 前端的整合策略

业内 MES 前端整合有三种思路：

**思路 1：单页面整合**——所有功能在 MES 单页面内 tab 切换，用户登录 MES 后看到所有信息。简单但单页面信息密度高，**厂长看不懂**。

**思路 2：仪表板整合**——按角色做仪表板（厂长仪表板、车间主任仪表板、操作员工位终端），每个仪表板聚合角色关心的所有数据。**业内主流**。

**思路 3：iframe 整合**——把 SCADA 看板、ERP 工单页面 iframe 嵌入 MES 仪表板。最快但用户体验差（iframe 切换慢、跨域 cookie 问题）。

本期选思路 2——按角色 3 个仪表板：

- **厂长仪表板**：8 个核心指标卡（OEE 全厂、客诉 Andon 数、SPC 异常数、化成预测异常、WIP 库存、当日产出、当周排产、瓶颈告警）
- **车间主任仪表板**：本车间设备实时状态 + Andon 呼叫 + 派工单进度 + SPC 控制图
- **操作员工位终端**：当前派工单 + 设备状态 + Andon 呼叫按钮 + SOP 文档

### APS 瓶颈识别：化成是 7 段工艺瓶颈

7 段工艺的节拍对比：

<table>
<thead><tr>
<th>工序</th>
<th>设备数</th>
<th>单设备节拍</th>
<th>单设备日产能</th>
<th>工序日产能</th>
</tr></thead>
<tbody>
<tr>
<td>涂布</td>
<td>2</td>
<td>0.5 片/min</td>
<td>720 片</td>
<td>1440 片</td>
</tr>
<tr>
<td>辊压</td>
<td>1</td>
<td>1 片/min</td>
<td>1440 片</td>
<td>1440 片</td>
</tr>
<tr>
<td>分切</td>
<td>1</td>
<td>2 片/min</td>
<td>2880 片</td>
<td>2880 片</td>
</tr>
<tr>
<td>卷绕</td>
<td>8</td>
<td>3 PPM</td>
<td>4320 颗</td>
<td>34560 颗</td>
</tr>
<tr>
<td>注液</td>
<td>1</td>
<td>2 PPM</td>
<td>2880 颗</td>
<td>2880 颗</td>
</tr>
<tr>
<td>化成</td>
<td>96</td>
<td>12 h/柜 × 7 颗</td>
<td>14 颗/柜 × 96 = 1344 颗</td>
<td>1344 颗</td>
</tr>
<tr>
<td>分容</td>
<td>96</td>
<td>12 h/柜 × 7 颗</td>
<td>14 颗/柜 × 96 = 1344 颗</td>
<td>1344 颗</td>
</tr>
</tbody>
</table>

**化成是瓶颈**——日产能 1344 颗远低于卷绕 34560 颗和注液 2880 颗，瓶颈比 1:25.7。APS 必须把化成柜能力作为**主约束**，按化成柜可用能力倒推前工序排产，**让前工序产出节奏匹配化成柜吞吐**——涂布不能一天产 1440 片，要按化成柜当天能消化多少产多少，避免 WIP 堆积。

## 底层原理：Vue3 仪表板 + 简易 APS 算法

### Vue3 仪表板技术栈

```yaml
技术栈:
  框架: Vue 3 + Vite
  UI库: Element Plus + ECharts 5
  状态管理: Pinia
  路由: Vue Router 4
  HTTP: Axios
  实时推送: WebSocket + Server-Sent Events
  大屏适配: postcss-px-to-viewport (1920x1080 → 自适应)
```

### 厂长仪表板组件设计

```vue
<!-- DashboardDirector.vue -->
<template>
  <div class="dashboard-director">
    <h1>厂长仪表板 - {{ factoryName }} - {{ currentTime }}</h1>

    <!-- 8 个核心指标卡 -->
    <el-row :gutter="16" class="metrics-cards">
      <el-col :span="6" v-for="m in metrics" :key="m.key">
        <MetricCard :metric="m" @click="showDetail(m)" />
      </el-col>
    </el-row>

    <!-- 实时 Andon 呼叫滚动条 -->
    <AndonScroll :calls="openAndonCalls" />

    <!-- 全厂 OEE 矩阵 -->
    <OeeMatrix :devices="devices" :oee-data="oeeData" />

    <!-- 化成瓶颈告警 -->
    <BottleneckAlert v-if="bottleneckWarning" :warning="bottleneckWarning" />

    <!-- 周排产甘特图 -->
    <ApsGantt :schedule="weeklySchedule" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useWebSocket } from '@vueuse/core'

const metrics = ref([])
const openAndonCalls = ref([])
const oeeData = ref({})
const weeklySchedule = ref([])
const bottleneckWarning = ref(null)

// WebSocket 实时推送
const { status, data } = useWebSocket('wss://api.mashangan.com/ws/dashboard')

watch(data, (msg) => {
  const evt = JSON.parse(msg)
  if (evt.type === 'METRIC_UPDATE') {
    const idx = metrics.value.findIndex(m => m.key === evt.key)
    if (idx >= 0) metrics.value[idx] = evt
  } else if (evt.type === 'ANDON_CALL') {
    openAndonCalls.value.unshift(evt.call)
  } else if (evt.type === 'BOTTLENECK_WARNING') {
    bottleneckWarning.value = evt.warning
  }
})

onMounted(async () => {
  // 初次加载
  const [m, a, o, s] = await Promise.all([
    api.getMetrics(), api.getOpenAndon(),
    api.getOeeMatrix(), api.getWeeklySchedule()
  ])
  metrics.value = m
  openAndonCalls.value = a
  oeeData.value = o
  weeklySchedule.value = s
})
</script>
```

### Andon 车间大屏组件

```vue
<!-- AndonBigScreen.vue - 投影到车间大屏（55寸电视） -->
<template>
  <div class="andon-big-screen">
    <div class="header">
      <h1>车间 Andon 看板</h1>
      <div class="time">{{ currentTime }}</div>
    </div>

    <div class="calls-grid">
      <div v-for="call in sortedCalls" :key="call.id"
           :class="['call-card', `level-${call.level.toLowerCase()}`,
                    isOverdue(call) ? 'overdue-blink' : '']">
        <div class="level">{{ call.level }}</div>
        <div class="title">{{ call.title }}</div>
        <div class="meta">
          <span>呼叫 {{ timeAgo(call.callTime) }}</span>
          <span>剩余 {{ remaining(call.slaResponseDeadline) }}</span>
          <span>{{ call.assigneeName || '待响应' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.andon-big-screen {
  font-size: 24px;  /* 大屏字号要大 */
  background: #1a1a2e;
  color: #fff;
  min-height: 100vh;
}
.level-l1 { background: #e74c3c; }  /* 红 */
.level-l2 { background: #e67e22; }  /* 橙 */
.level-l3 { background: #f1c40f; color: #000; }  /* 黄 */
.level-l4 { background: #3498db; }  /* 蓝 */
.overdue-blink { animation: blink 0.5s infinite; }
@keyframes blink {
  50% { opacity: 0.5; }
}
</style>
```

### 简易 APS 算法：化成瓶颈驱动排产

APS 主流算法有 3 种：① TOC（Theory of Constraints）瓶颈驱动；② 遗传算法；③ LP（线性规划）启发式。本期选 TOC 简化版——**化成柜是瓶颈，按化成柜能力倒推前工序**。

```java
@Service
public class SimpleApsService {
    @Autowired private WorkOrderService woService;
    @Autowired private DeviceService deviceService;
    @Autowired private ShiftCalendarService shiftService;

    /**
     * 排产算法（化成瓶颈驱动）:
     * 1. 找出化成柜的可用时段
     * 2. 按工单优先级 + 化成曲线分配化成柜
     * 3. 倒推前工序（注液/卷绕/分切/辊压/涂布）的开始时间
     */
    public ScheduleResult schedule(LocalDate fromDate, int days) {
        // 1. 拉取所有待排产工单，按优先级排序
        List<WorkOrder> wos = woService.findPending(fromDate, days);
        wos.sort(Comparator.comparing(WorkOrder::getPriority).reversed());

        // 2. 找出化成柜可用时段
        List<DeviceAvailability> formationSlots =
            deviceService.findAvailability("化成", fromDate, days);

        // 3. 按化成柜能力分配工单
        List<ScheduledOperation> scheduled = new ArrayList<>();
        for (WorkOrder wo : wos) {
            // 化成需要的柜时 = 数量 / 单柜容量(7) × 单柜时长(12h)
            int formationCells = (int) Math.ceil(wo.getQuantity() / 7.0);
            int formationHours = formationCells * 12;

            // 找最早可用的化成柜时段
            DeviceAvailability slot = findEarliestSlot(
                formationSlots, formationHours, wo.getFormationCurve());
            if (slot == null) {
                log.warn("化成柜能力不足，工单 " + wo.getWoNo() + " 排不下");
                continue;
            }

            // 分配化成工序
            scheduled.add(scheduleFormation(wo, slot, formationHours));
            // 占用化成柜时段
            slot.consume(formationHours);

            // 倒推注液/卷绕/分切/辊压/涂布的开始时间
            LocalDate formationStart = slot.getStartTime();
            scheduled.addAll(scheduleUpstreamOps(wo, formationStart));
        }

        return new ScheduleResult(scheduled, detectBottleneck(scheduled));
    }

    private List<ScheduledOperation> scheduleUpstreamOps(WorkOrder wo,
            LocalDate formationStart) {
        List<ScheduledOperation> ops = new ArrayList<>();
        // 注液在化成前 4 小时（注液 2 PPM × 1000 颗 = 500 分钟 ≈ 8 小时，重叠排）
        ops.add(scheduleOp(wo, 5, formationStart.minusHours(4)));
        // 卷绕在注液前 2 小时
        ops.add(scheduleOp(wo, 4, formationStart.minusHours(6)));
        // 分切在卷绕前 1 小时
        ops.add(scheduleOp(wo, 3, formationStart.minusHours(7)));
        // 辊压在分切前 1 小时
        ops.add(scheduleOp(wo, 2, formationStart.minusHours(8)));
        // 涂布在辊压前 4 小时（涂布 0.5 片/min，1000 片 = 2000 分钟 = 33 小时）
        ops.add(scheduleOp(wo, 1, formationStart.minusHours(37)));
        return ops;
    }

    private BottleneckWarning detectBottleneck(List<ScheduledOperation> scheduled) {
        // 检测 WIP 堆积：若某工序产出与下工序消化能力差 > 200 颗，告警
        // ...
    }
}
```

### 排产甘特图组件

```vue
<!-- ApsGantt.vue -->
<template>
  <div class="aps-gantt">
    <h2>周排产甘特图</h2>
    <div class="gantt-container" ref="ganttRef"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps(['schedule'])
const ganttRef = ref(null)
let chart = null

onMounted(() => {
  chart = echarts.init(ganttRef.value)
  renderGantt()
})

watch(() => props.schedule, renderGantt)

function renderGantt() {
  // 把 schedule 转换为 ECharts 自定义系列甘特图
  const categories = ['涂布', '辊压', '分切', '卷绕', '注液', '化成', '分容']
  const data = props.schedule.map(op => ({
    name: op.woNo,
    value: [categories.indexOf(op.opName),
            op.startTs, op.endTs],
    itemStyle: { color: levelToColor(op.priority) }
  }))

  chart.setOption({
    tooltip: { formatter: p => `${p.data.name}<br/>${p.data.value[1]} - ${p.data.value[2]}` },
    xAxis: { type: 'time' },
    yAxis: { type: 'category', data: categories },
    series: [{ type: 'custom', renderItem: renderGanttBar, data }]
  })
}
</script>
```

## 数据说话：前端整合 + APS 上线后对比

上线 3 个月，对比事故前后：

<table>
<thead><tr>
<th>维度</th>
<th>事故前</th>
<th>上线后</th>
<th>改进</th>
</tr></thead>
<tbody>
<tr>
<td>厂长上班看指标耗时</td>
<td>8 tab × 5 min = 40 min</td>
<td>1 仪表板 5 min</td>
<td>-87%</td>
</tr>
<tr>
<td>Andon 车间响应时间</td>
<td>后台看到 2h</td>
<td>大屏看到 5min</td>
<td>-96%</td>
</tr>
<tr>
<td>WIP 库存堆积</td>
<td>月均 1500 颗 × 24h</td>
<td>月均 200 颗 × 4h</td>
<td>-87%</td>
</tr>
<tr>
<td>化成柜利用率</td>
<td>71%</td>
<td>89%</td>
<td>+18%</td>
</tr>
<tr>
<td>排产准确率</td>
<td>Excel 排，化成漏 30%</td>
<td>APS 算法化成 0 漏</td>
<td>+30%</td>
</tr>
<tr>
<td>厂长对 MES 满意度</td>
<td>"增加工作量"</td>
<td>"5 分钟看完状态"</td>
<td>显著提升</td>
</tr>
</tbody>
</table>

关键发现：

1. **WIP 堆积减少 87%**——化成柜倒推排产后，前工序按化成能力产出，**不再过量生产**。
2. **化成柜利用率从 71% → 89%**——APS 把工单合理分配到不同化成柜，避免某柜空闲某柜堵塞。
3. **排产准确率 +30%**——APS 考虑了化成曲线与产品料号匹配（ch05 的核心校验），不再排错曲线。

## 面试怎么答：MES 前端与 APS

**问：MES 前端为什么按角色做仪表板，不做单页面？**

答：3 个原因：① **信息密度**——单页面聚合所有功能信息密度高，厂长看不懂（厂长关心 OEE 全厂/客诉/瓶颈，不关心单设备 SPC 控制图）；② **角色关注点差异**——厂长看 8 个核心指标卡（5 秒判断异常），车间主任看本车间设备实时状态（5 分钟决策改派），操作员看当前派工单（实时操作），三个角色三个仪表板；③ **大屏适配**——车间大屏 55 寸要求字号大、信息简洁，操作员工位终端 21 寸要求操作按钮大，单页面无法同时适配。**按角色仪表板是工业软件标准实践**。

**问：APS 为什么选 TOC 瓶颈驱动，不用遗传算法或 LP？**

答：3 个原因：① **算法透明度**——TOC 是"找瓶颈→按瓶颈倒推"的确定性算法，结果可解释（厂长问"为什么这么排"，工程师能解释化成柜能力约束）；遗传算法是元启发式，结果不可解释；② **计算复杂度**——TOC 是 O(N×M) 线性复杂度，96 设备 × 30 工单 30 秒出结果；LP 需建模为整数规划，96 设备变 96 决策变量 × 30 工时窗 = 2880 变量，求解需商用求解器 Gurobi/CPLEX；③ **场景匹配**——电芯厂 7 段工艺瓶颈明确是化成柜，TOC 适用"单瓶颈"场景；多瓶颈场景才需要遗传/LP。本期 TOC 够用，未来多车间多瓶颈可升级到 LP。

**问：化成柜为什么是瓶颈？APS 怎么利用这个信息？**

答：化成柜是瓶颈因为单柜 12 小时一柜 × 7 颗电芯，96 柜日产能 1344 颗，远低于卷绕 34560 颗、注液 2880 颗——瓶颈比 1:25.7。APS 利用这个信息的核心算法：① **找化成柜可用时段**——按班次日历 + 设备维护计划算出每个化成柜的可用时段；② **按工单优先级分配化成柜**——优先级高的工单先排，按化成曲线匹配可用柜（ch05 校验）；③ **倒推前工序开始时间**——化成开始前 4 小时注液、前 6 小时卷绕、前 7 小时分切、前 8 小时辊压、前 37 小时涂布。倒推让前工序在化成柜需要时刚好产出，避免 WIP 堆积。

**问：车间 Andon 大屏与 MES 后台有什么不同？**

答：3 点差异：① **字号**——后台 14px，大屏 24px+（55 寸电视观看距离 5-10 米，14px 看不清）；② **配色**——后台浅色主题（白底黑字），大屏深色主题（黑底彩字，黑暗车间更醒目，超时闪烁红蓝）；③ **信息密度**——后台显示所有 Andon 呼叫（含历史），大屏只显示当前 OPEN 状态 + SLA 倒计时，按级别排序，超时高亮。大屏是**车间操作员的第一信息源**，事故 1 客诉响应慢 2 小时的根因就是没大屏。

## 落地清单：Vue3 仪表板 + APS 工程化动作

1. **3 个角色仪表板**：厂长（8 指标卡+Andon 滚动+OEE 矩阵+瓶颈告警+甘特图）、车间主任（设备实时+Andon+派工进度+SPC）、操作员（当前派工+设备状态+Andon 按钮+SOP），禁用单页面整合。
2. **车间 Andon 大屏**：55 寸电视深色主题，字号 24px+，超时闪烁，只显示 OPEN 呼叫。
3. **WebSocket 实时推送**：禁用轮询，WS 推送指标更新/Andon 呼叫/瓶颈告警。
4. **APS TOC 瓶颈驱动**：化成柜能力作主约束，倒推前工序开始时间，禁用 Excel 排产。
5. **化成曲线与产品料号匹配校验**：APS 分配化成柜时校验曲线 capacity_spec 与产品 capacity_spec 一致（ch05 防线）。
6. **排产甘特图 ECharts**：自定义系列渲染甘特图，按工序分行，按优先级配色。
7. **WIP 堆积检测**：APS 输出后自动检测各工序在制品堆积，超 200 颗告警。
8. **黄金回归**：① 厂长仪表板 5 秒内加载完 8 指标卡；② Andon 大屏超时必须闪烁；③ APS 排产必须把化成柜作主约束；④ 化成曲线与产品料号不匹配的工单不能排产；⑤ WIP 超 200 颗必须告警。

最后一章我们离开工程实现，把全 13 章知识结构化成面试总纲——30 道高频 MES 面试题，覆盖九大模块的答题框架。
