---
title: ch09 Andon 呼叫与质量闭环：分级 + SLA + 8D 报告
slug: cell-mes-09-andon
---

## 事故现场：客户投诉后 5 小时没人理，Andon 看板没点亮

事故 1 后续复盘要把 Andon 失效过程还原。2025 年 11 月某周，客户在 Pack EOL 测试发现 280Ah 电芯容量不够后，11 月 19 日 09:00 给工厂质量部打电话投诉。质量部接电话的工程师小李口头答应"我查一下"，**挂了电话后没有任何系统操作**——没在 MES 里登记客诉、没在 Andon 看板呼叫、没在 8D 系统开 case。

09:30 小李找到化成车间主任老王，老王说"我查一下化成曲线"，**口头答应但也没在系统登记**。

11:00 小李追老王进度，老王说"还在查 SCADA 日志"。13:00 小李再追，老王说"找到了，可能是化成曲线参数变更，但具体哪个曲线不确定"。15:00 小李追到第 4 次，老王不耐烦了说"你别催了，我下周一给你结果"。

15:30 客户第二个电话追工厂进度，工厂回复"还在查"。客户怒了，**直接升级到客户质量总监**，总监发邮件给厂长要求"24 小时内出 8D 报告"。厂长 16:00 才知道有客诉这事，从零开始追，22:00 才圈定问题曲线 `FORN-280-CHG-V3`，第二天 09:00 出 8D 报告——**比客户要求的 24 小时晚了 7 小时**。

复盘发现三个 Andon 系统失效点：

**问题 1：客诉没入口**。MES 有设备 Andon（设备故障呼叫），有人工 Andon（操作员呼叫），但**没有客诉 Andon**——客户投诉电话没有系统登记入口，质量部只能口头转告。

**问题 2：SLA 没定义**。设备 Andon 的 SLA 是"5 分钟响应、30 分钟到场"，但客诉的 SLA 没定义——老王 09:30 接到通知，11:00 才开始查，**没有超时升级机制**。

**问题 3：8D 报告没流程**。8D（Eight Disciplines）是质量管理的问题解决方法论，8 个步骤：D1 团队 / D2 描述 / D3 临时措施 / D4 根因 / D5 永久措施 / D6 验证 / D7 防止再发生 / D8 表彰。工厂的 8D 是 Excel 表格，**没有系统流程驱动**，全靠工程师小李逐个邮件追人，6 小时才组齐团队。

## 排查：Andon 系统的三个缺口

<table>
<thead><tr>
<th>缺口</th>
<th>现状</th>
<th>后果</th>
</tr></thead>
<tbody>
<tr>
<td>客诉没入口</td>
<td>Andon 看板只接设备/操作员呼叫</td>
<td>客诉信息口头传递，无追踪</td>
</tr>
<tr>
<td>SLA 没分级</td>
<td>设备故障/客诉/质量异常都用同一 SLA</td>
<td>客诉等设备故障级响应，慢</td>
</tr>
<tr>
<td>8D 没流程</td>
<td>Excel 表格</td>
<td>团队组建 6 小时，出报告 16 小时</td>
</tr>
</tbody>
</table>

### Andon 呼叫的分级体系

业内 Andon 系统按**严重度**和**响应要求**分级：

<table>
<thead><tr>
<th>级别</th>
<th>名称</th>
<th>触发场景</th>
<th>SLA 响应</th>
<th>SLA 到场</th>
<th>升级</th>
</tr></thead>
<tbody>
<tr>
<td>L1</td>
<td>紧急</td>
<td>全线停产、安全风险</td>
<td>1 min</td>
<td>5 min</td>
<td>10 min 升厂长</td>
</tr>
<tr>
<td>L2</td>
<td>高</td>
<td>工序停机、设备故障</td>
<td>5 min</td>
<td>30 min</td>
<td>1h 升车间主任</td>
</tr>
<tr>
<td>L3</td>
<td>中</td>
<td>SPC 异常、质量偏差</td>
<td>15 min</td>
<td>2h</td>
<td>4h 升质量经理</td>
</tr>
<tr>
<td>L4</td>
<td>低</td>
<td>微停、咨询</td>
<td>30 min</td>
<td>8h</td>
<td>24h 升班长</td>
</tr>
</tbody>
</table>

事故 1 的客诉属于 L2-L3 级（已发货客户、需查根因），但被口头转告后没人按 L2 处理，**SLA 1 min 响应变 6 小时响应**。

### 8D 报告的标准流程

8D 是汽车行业（Ford 1980s）推广的质量问题解决方法论，是 ISO 9001 IATF 16949 汽车行业质量管理体系的核心工具：

- D1 准备团队：组建 6-8 人跨部门团队（质量/工艺/生产/采购/工程）
- D2 描述问题：5W2H 描述（Who/What/When/Where/Why/How）
- D3 临时措施：立即围堵（hold 物料、隔离批次）
- D4 根因分析：5 Why、鱼骨图、FMEA
- D5 永久措施：制定根本解决方案
- D6 验证措施有效性：试点 → 验证 → 推广
- D7 防止再发生：SOP 更新、SPC 控制图添加、FMEA 更新
- D8 表彰团队：结案、表彰、知识沉淀

事故 1 的 8D 报告全程 Excel，**D1 团队组建就花了 6 小时**（口头找人），D4 根因分析靠老王一个人看 SCADA 日志，D7 防止再发生**没落实**——后续 SPC 引擎没加西电规则就是 D7 没做的证据。

## 底层原理：Andon 看板与 8D 集成模型

### Andon 看板的核心数据结构

```sql
CREATE TABLE andon_call (
    id BIGINT PRIMARY KEY,
    call_time TIMESTAMP NOT NULL,
    call_source VARCHAR(16) NOT NULL,    -- MANUAL/DEVICE/SPC/QUALITY/CUSTOMER
    call_level VARCHAR(4) NOT NULL,      -- L1/L2/L3/L4
    device_id BIGINT,                     -- 关联设备
    process_id BIGINT,                    -- 关联工序
    lot_id BIGINT,                        -- 关联批次
    caller_id BIGINT,                     -- 呼叫人
    title VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(16) NOT NULL,          -- OPEN/RESPONDING/RESOLVED/CLOSED
    sla_response_min INT,                 -- SLA 响应分钟数
    sla_onsite_min INT,                   -- SLA 到场分钟数
    sla_response_deadline TIMESTAMP,
    sla_onsite_deadline TIMESTAMP,
    responded_at TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    assignee_id BIGINT,                   -- 当前负责人
    escalate_to_id BIGINT,               -- 升级目标人
    8d_case_id BIGINT                     -- 关联 8D case
);
CREATE INDEX idx_andon_status ON andon_call(status, sla_response_deadline);
```

### 8D case 数据结构

```sql
CREATE TABLE eight_d_case (
    id BIGINT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    open_time TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL,           -- D1/D2/D3/D4/D5/D6/D7/D8/CLOSED
    current_step VARCHAR(4) NOT NULL,      -- D1-D8
    -- D1 团队
    team_lead_id BIGINT,
    team_members JSONB,                    -- [{"user_id":1,"role":"质量"},...]
    -- D2 问题描述
    problem_desc TEXT,
    problem_5w2h JSONB,                    -- {"who":"","what":"","when":"","where":"","why":"","how":""}
    -- D3 临时措施
    interim_actions TEXT,
    interim_actions_time TIMESTAMP,
    -- D4 根因
    root_cause TEXT,
    root_cause_method VARCHAR(32),         -- 5WHY / FISHBONE / FMEA
    -- D5 永久措施
    permanent_actions TEXT,
    -- D6 验证
    verification_result TEXT,
    -- D7 防止再发生
    prevention_actions TEXT,
    -- D8 表彰
    recognition TEXT,
    close_time TIMESTAMP,
    andon_call_id BIGINT,
    UNIQUE(andon_call_id)
);
```

### SLA 升级机制

```java
@Service
public class AndonService {
    @Autowired private AndonCallMapper callMapper;
    @Autowired private UserService userService;
    @Autowired private NotificationService notifyService;

    @Scheduled(fixedDelay = 60000)  // 每分钟扫描
    public void checkSlaTimeout() {
        List<AndonCall> overdue = callMapper.findOverdueSla();
        for (AndonCall call : overdue) {
            if (call.getRespondedAt() == null &&
                call.getCallTime().before(call.getSlaResponseDeadline())) {
                // 响应超时 → 升级
                escalate(call, "响应超时");
            } else if (call.getResolvedAt() == null &&
                call.getRespondedAt().before(call.getSlaOnsiteDeadline())) {
                // 到场超时 → 升级
                escalate(call, "到场超时");
            }
        }
    }

    private void escalate(AndonCall call, String reason) {
        Long escalateTo = call.getEscalateToId();
        if (escalateTo == null) {
            // 按级别查上级
            escalateTo = userService.findNextLevelApprover(call.getCallLevel(),
                call.getAssigneeId());
        }
        notifyService.sendUrgent(escalateTo,
            "Andon " + call.getCallLevel() + " 升级: " + call.getTitle() +
            " | 原因: " + reason +
            " | Andon ID: " + call.getId());

        // 升级后 SLA 时限减半
        call.setSlaResponseMin(call.getSlaResponseMin() / 2);
        call.setSlaOnsiteMin(call.getSlaOnsiteMin() / 2);
        call.recalcDeadlines();
        callMapper.update(call);
    }
}
```

## 正确姿势：Andon + 8D 闭环工程实现

### 1. Andon 多入口

```java
@RestController
@RequestMapping("/api/andon")
public class AndonController {
    @Autowired private AndonService andonService;

    // 设备故障自动触发（SCADA 推 ALARM 状态时）
    @PostMapping("/device-alarm")
    public Result deviceAlarm(@RequestBody DeviceAlarmEvent evt) {
        return Result.ok(andonService.call(AndonCall.builder()
            .source(AndonSource.DEVICE)
            .level(AndonLevel.L2)
            .deviceId(evt.getDeviceId())
            .title("设备故障: " + evt.getMessage())
            .description(evt.getDetail())
            .build()));
    }

    // SPC 西电规则违反自动触发
    @PostMapping("/spc-alert")
    public Result spcAlert(@RequestBody SpcAlertEvent evt) {
        return Result.ok(andonService.call(AndonCall.builder()
            .source(AndonSource.SPC)
            .level(AndonLevel.L3)
            .deviceId(evt.getDeviceId())
            .lotId(evt.getLotId())
            .title("SPC " + evt.getRuleName() + " 违反")
            .description(evt.getMessage())
            .build()));
    }

    // 客诉入口（这是事故 1 的核心防线）
    @PostMapping("/customer-complaint")
    public Result customerComplaint(@RequestBody CustomerComplaint cmd) {
        AndonCall call = AndonCall.builder()
            .source(AndonSource.CUSTOMER)
            .level(AndonLevel.L2)   // 客诉默认 L2
            .title("客诉: " + cmd.getCustomerName() + " - " + cmd.getIssueDesc())
            .description(cmd.getDetail())
            .build();
        Long callId = andonService.call(call);

        // 同时自动开 8D case
        Long caseId = eightDService.openCase(callId, "D1");

        return Result.ok(Map.of("callId", callId, "caseId", caseId));
    }

    // 操作员人工呼叫（车间按钮/APP）
    @PostMapping("/manual")
    public Result manual(@RequestBody ManualCall cmd) {
        return Result.ok(andonService.call(AndonCall.builder()
            .source(AndonSource.MANUAL)
            .level(cmd.getLevel())
            .deviceId(cmd.getDeviceId())
            .callerId(cmd.getCallerId())
            .title(cmd.getTitle())
            .description(cmd.getDescription())
            .build()));
    }
}
```

### 2. 8D 流程驱动

```java
@Service
public class EightDService {
    @Autowired private EightDCaseMapper caseMapper;
    @Autowired private UserService userService;

    @Transactional
    public Long openCase(Long andonCallId, String initialStep) {
        EightDCase c = new EightDCase();
        c.setAndonCallId(andonCallId);
        c.setTitle(caseMapper.findTitleByAndonId(andonCallId));
        c.setOpenTime(now());
        c.setStatus("OPEN");
        c.setCurrentStep(initialStep);

        // D1: 自动组建团队——按 Andon 级别自动选人
        Long teamLeadId = userService.findTeamLead(andonCallId);
        c.setTeamLeadId(teamLeadId);
        List<Long> members = userService.findDefaultTeamMembers(andonCallId);
        c.setTeamMembers(toJson(members));
        caseMapper.insert(c);

        // 通知团队
        notifyService.sendTeam(c.getId(), teamLeadId, members);
        return c.getId();
    }

    // D3 临时措施：自动 hold 物料
    @Transactional
    public void takeInterimAction(Long caseId, List<Long> lotIds) {
        EightDCase c = caseMapper.getById(caseId);
        c.setInterimActions("自动 hold 批次: " + lotIds);
        c.setInterimActionsTime(now());
        c.setCurrentStep("D3");
        caseMapper.update(c);

        // 调用 LotService.hold
        for (Long lotId : lotIds) {
            lotService.hold(lotId, "8D Case " + caseId + " D3 临时措施");
        }
    }

    // D7 防止再发生：自动更新 SPC 规则、SOP
    @Transactional
    public void takePrevention(Long caseId, PreventionAction action) {
        EightDCase c = caseMapper.getById(caseId);
        c.setPreventionActions(action.toJson());
        c.setCurrentStep("D7");
        caseMapper.update(c);

        // 自动更新 SPC：添加新规则
        if (action.getAddSpcRule() != null) {
            spcChartService.enableRule(action.getChartId(), action.getAddSpcRule());
        }
        // 自动更新 SOP 版本
        if (action.getUpdateSopId() != null) {
            sopService.bumpVersion(action.getUpdateSopId());
        }
    }
}
```

### 3. Andon 看板前端

Andon 看板（大屏）实时显示当前 OPEN 的呼叫，按级别排序，超时高亮：

```vue
<template>
  <div class="andon-board">
    <h1>Andon 看板 {{ currentTime }}</h1>
    <div class="calls">
      <div v-for="call in sortedCalls" :key="call.id"
           :class="['call', `level-${call.level}`,
                     isOverdue(call) ? 'overdue' : '']">
        <div class="level">{{ call.level }}</div>
        <div class="title">{{ call.title }}</div>
        <div class="meta">
          <span>呼叫 {{ format(call.callTime) }}</span>
          <span>SLA 响应剩余 {{ remaining(call.slaResponseDeadline) }}</span>
          <span>SLA 到场剩余 {{ remaining(call.slaOnsiteDeadline) }}</span>
          <span>负责人 {{ call.assigneeName }}</span>
        </div>
        <div v-if="call.eightDCaseId" class="8d">
          8D: {{ call.currentStep }}
        </div>
      </div>
    </div>
  </div>
</template>
```

## 数据说话：Andon + 8D 上线后对比

上线 3 个月，对比事故 1 时的客诉处理：

<table>
<thead><tr>
<th>维度</th>
<th>事故 1 时</th>
<th>上线后</th>
<th>改进</th>
</tr></thead>
<tbody>
<tr>
<td>客诉响应时间</td>
<td>6 小时</td>
<td>5 分钟（系统自动派单）</td>
<td>-98%</td>
</tr>
<tr>
<td>8D 团队组建</td>
<td>6 小时</td>
<td>3 分钟（自动派单 + 推送）</td>
<td>-99%</td>
</tr>
<tr>
<td>8D 报告出报告</td>
<td>16 小时</td>
<td>4 小时（流程驱动）</td>
<td>-75%</td>
</tr>
<tr>
<td>SLA 超时升级次数</td>
<td>不存在</td>
<td>月均 3.2 次（自动升级）</td>
<td>-100% 漏处理</td>
</tr>
<tr>
<td>Andon 月度呼叫总数</td>
<td>不可统计</td>
<td>月均 142 次（L4 占 65%）</td>
<td>数据可量化</td>
</tr>
<tr>
<td>8D case 月度开</td>
<td>0 次</td>
<td>月均 8.5 次</td>
<td>闭环跟踪</td>
</tr>
</tbody>
</table>

关键发现：

1. **客诉 5 分钟响应**——客诉入口触发后，系统自动按 L2 派单到质量经理，5 分钟内必须响应（看 Andon 看板），超时自动升级到厂长。
2. **8D 团队 3 分钟组建**——D1 自动按 Andon 级别选人（质量/工艺/生产/采购/工程），推送通知到企业微信。
3. **8D 报告 4 小时出**——D2 描述/D3 临时措施/D4 根因/D5 永久措施每步都有模板和 check，工程师填空而非自由发挥。
4. **SLA 超时自动升级月均 3.2 次**——主要发生在 L4 低级呼叫（操作员咨询类），自动升级到班长后处理效率提升。

## 面试怎么答：Andon 与 8D

**问：Andon 系统的分级为什么要分 4 级？不能一个级别吗？**

答：4 级分级是按**严重度+响应要求**差异化处理——L1 紧急（1 min 响应 5 min 到场）适用全线停产/安全风险，L2 高（5 min 30 min）适用工序停机，L3 中（15 min 2h）适用质量异常，L4 低（30 min 8h）适用咨询。一个级别会导致两种失败：① 高级别场景（L1 全线停产）按低级别响应（8h），损失爆炸；② 低级别场景（L4 咨询）按高级别响应（1 min），资源浪费——工厂月均 142 次 Andon 呼叫，如果都按 1 min 响应，团队疲于奔命反而漏掉真事故。4 级分级本质是**资源按严重度分配**，是 SLA 工程化的标准做法。

**问：8D 报告为什么要 8 个步骤？直接写个根因分析不行吗？**

答：8 个步骤覆盖**事故应急+根因+永久措施+防再发生+知识沉淀**全流程：① D1 团队——跨部门 6-8 人，避免单人视角盲区；② D2 5W2H 描述——结构化问题描述，避免模糊；③ D3 临时措施——立即 hold 物料，防止问题扩散；④ D4 根因——5 Why 多层追问，避免表症处理；⑤ D5 永久措施——根本解决方案；⑥ D6 验证——试点后推广，避免方案不落地；⑦ D7 防止再发生——SOP/SPC/FMEA 更新，**这是最容易被忽略但最关键的一步**；⑧ D8 表彰——团队激励。直接写根因分析只覆盖 D4，漏掉 D3（物料没 hold）、D5（方案没落地）、D7（防再发生没做）——事故 1 的复盘就漏了 D7，导致 SPC 西电规则没启用，3 个月后又出类似事故。

**问：Andon 与 8D 怎么集成？自动开 case 有什么好处？**

答：集成点在 Andon 呼叫触发 8D case——客诉/SPC 异常/质量偏差等需要根因分析的场景，Andon 触发时同时开 8D case，case ID 反向关联到 Andon call 的 eight_d_case_id 字段。好处：① **流程闭环**——Andon 解决了即时响应，8D 解决了根因+永久措施，两者通过 case ID 关联，不会"响应完就忘"；② **团队自动组建**——D1 按 Andon 级别自动选团队（L2 选质量经理+工艺+生产），3 分钟组建完成；③ **临时措施自动执行**——D3 自动 hold 物料、隔离批次，工程师不用手动操作；④ **防止再发生自动落地**——D7 自动更新 SPC 规则、SOP 版本，避免工程师写完报告就忘。

**问：SLA 升级机制怎么设计？超时就升级？**

答：SLA 升级按"响应超时/到场超时"两类触发——响应超时（未在 sla_response_min 内点击 Andon 看板的"响应"按钮）自动升级到上级，到场超时（未在 sla_onsite_min 内到现场或远程介入）也升级。升级机制关键点：① 升级目标人按 Andon 级别查（L2 升车间主任，L1 升厂长）；② 升级后 SLA 时限减半（L2 响应 5 min，升级后 2.5 min），制造升级压力；③ 升级通知用加急通道（企业短信+电话+APP 推送）；④ 升级链有上限（最多升 3 级到厂长），防止无限升级。**事故 1 客诉没 SLA 升级机制，老王 6 小时才响应就是没升级压力**。

## 落地清单：Andon + 8D 工程化动作

1. **Andon 多入口**：设备故障（SCADA 推 ALARM）/SPC 异常（西电规则）/客诉（API 入口）/人工（按钮+APP）四种来源，不能只有设备。
2. **4 级分级 SLA**：L1 1min/5min、L2 5min/30min、L3 15min/2h、L4 30min/8h，升级链 L4→班长→质量经理→厂长。
3. **SLA 定时扫描**：每分钟扫描 OPEN 状态的呼叫，响应/到场超时自动升级，减半 SLA 制造压力。
4. **Andon 看板大屏**：实时显示 OPEN 呼叫，按级别排序+超时高亮+8D case 进度。
5. **8D case 自动开**：客诉/SPC 异常/质量偏差触发 Andon 时同时开 8D case，按级别自动组建团队。
6. **D3 临时措施自动 hold**：开 case 后自动 hold 关联批次，防止问题扩散。
7. **D7 防止再发生系统化**：自动更新 SPC 规则、SOP 版本、FMEA，避免报告写完就忘。
8. **黄金回归**：① 客诉 API 必须自动开 8D case；② SLA 响应超时必须自动升级；③ D3 必须自动调用 LotService.hold；④ 8D case 状态机 D1-D8 不可跳步；⑤ D7 必须更新 SPC/SOP 至少一项。

下一章我们离开 Andon 进入预测性维护，看怎么用化成柜历史容量数据预测下一柜容量，提前拦截容量不够——这是事故 1 的主动防线（事故前 24h 拦截而非被动追溯）。
