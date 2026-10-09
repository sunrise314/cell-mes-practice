---
title: ch06 批次追溯三链：向前/向后追溯 + 召回边界 + CTE 递归
slug: cell-mes-06-trace
---

## 事故现场：36 颗电芯召回查了 11 天，5 个跨工序会议、12 张 Excel

事故 3 的复盘要把当时的查追溯过程还原：2025 年 12 月 17 日 09:23，客户在装配 Pack 后做 EOL 测试，发现某颗 280Ah 半固态电芯（SN: X280A2025121500231，下文记为 SN-231）充电 14 天后内短路。客户要求工厂按"同一物料批次"召回——客户合同里"同一物料批次"的定义是：**同一正极粉料批次 + 同一涂布卷 + 同一化成柜 + 同一化成曲线**的所有电芯。

工厂接到召回通知后，质量工程师老李打开 Excel 开始查：

**第 1 天（12/17）**：老李从客户那里拿到 SN-231，从 SAP 工单查到这颗电芯出自工单 WO-20251215-018，1000 颗，型号 X-280，化成曲线 FORN-280-CHG-V3。**SAP 能查到工单但查不到物料批次**——物料批次信息在哪？

**第 2-3 天（12/18-19）**：老李跑到涂布车间，找涂布班组长查"12 月 15 日给工单 WO-...-018 供料的正极粉料批次号是什么"。涂布班组长翻交接班记录本，找到 12 月 15 日 A 班用了粉料批次 `LFP-PW-20251128-007`（128 公斤），但**记录本没写这批粉料做了几卷极片、流到哪条卷绕线**。涂布班组长让老李去问辊压车间。

**第 4-5 天（12/20-21）**：辊压车间说"我们按卷接收按卷产出，没记粉料批次号"。老李又问涂布车间能不能从涂布机日志反查。涂布机日志在 SCADA 里，SCADA 工程师导了 4G 数据，老李用 Python 脚本按时间过滤——找到 12 月 15 日 08:23-09:45 用了粉料批次 007 涂了 1 卷（卷号 BC-001-20251215-003）。但**辊压没记粉料批次**这信息断了，老李只能假设"辊压按卷顺序接收，涂布卷号 BC-...-003 应该进了辊压机 RP-001 的第 N 卷"。

**第 6-7 天（12/22-23）**：老李追到卷绕车间，卷绕机 WND-003 的 SCADA 日志显示 12 月 15 日 14:30 卷了 240 颗裸芯，但**卷绕机也没记极片卷号**——只知道按时间窗口产出了 240 颗裸芯，裸芯 SN 区间 X280A2025121500001-240。老李用 Excel 把这 240 颗裸芯标记为"可能受影响"，但**卷绕机日志没和辊压日志对齐**，"哪些裸芯用了 BC-...-003 这卷"还是猜的。

**第 8-10 天（12/24-26）**：化成车间比较好查——化成柜 F1-012 的 SCADA 日志显示 12 月 15 日 22:00 进了 240 颗裸芯，化成曲线 FORN-280-CHG-V3。**这 240 颗裸芯就是召回范围**。但其中 36 颗已经发往客户了，老李查 ERP 销售订单，36 颗发往 3 个客户，**只有 1 个是投诉客户**。最终召回范围：36 颗 + 现仓的 204 颗返检。

**第 11 天（12/27）**：老李把 12 张 Excel 整合成报告，圈定 36 颗召回 + 204 颗返检，开召回会。厂长问："这 11 天里你做了什么？"，老李说："60% 时间在跨车间求人查数据，30% 时间在 Excel 里 VLOOKUP，10% 时间在写 Python 脚本过滤 SCADA 日志"。厂长："那如果客户合同定义'同一电解液批次'召回呢？"，老李："……我可能要再花 11 天"。

## 排查：为什么追溯链会断

复盘把 11 天的追溯过程拆开，发现**追溯链在 4 个工序节点断了**：

<table>
<thead><tr>
<th>工序</th>
<th>断点</th>
<th>原因</th>
</tr></thead>
<tbody>
<tr>
<td>涂布→辊压</td>
<td>极片卷号没传递</td>
<td>辊压车间按"辊数"接收，没扫涂布卷号</td>
</tr>
<tr>
<td>辊压→分切</td>
<td>辊压卷号没传递</td>
<td>分切按"米数"接收，没扫辊压卷号</td>
</tr>
<tr>
<td>分切→卷绕</td>
<td>分切后小卷号没传递</td>
<td>卷绕按"裸芯数量"接收，没扫分切卷号</td>
</tr>
<tr>
<td>卷绕→注液</td>
<td>裸芯 SN 已生成但没扫到注液</td>
<td>注液按"批量"接收，只记了批次号没记 SN</td>
</tr>
</tbody>
</table>

每一段断点都是同样的根因：**下游工序接收上游物料时，没强制扫描上游的批次号/SN，只记自己工序的产出批次号**。这导致追溯链只能正向走（从粉料推到电芯），反向走（从电芯追回粉料）必须靠时间窗口猜——客户召回正是反向追溯，正好踩在断点上。

更深层根因：**没有"批次三链"模型**——业界把物料追溯抽象为三条链：**料链（物料批次）、工序链（工序产出）、设备链（设备时段）**，三条链必须交叉关联，任何一颗电芯都能从料链反查工序链再反查设备链，反之亦然。电芯厂 A 的现状是每条链都记了，但三条链之间没关联——料链在 ERP 里、工序链在 MES 派工单里、设备链在 SCADA 日志里，**三套系统三套 ID**。

## 底层原理：批次三链与 CTE 递归追溯

### 批次三链的数据模型

批次三链的核心是**一个物料移动事件同时记录三条链的 ID**：

```
[料链]    料批 LFP-PW-20251128-007  → 涂布极片卷 BC-001-...-003
[工序链]  工单 WO-...-018-OP1       → 涂布产出 BC-001-...-003
[设备链]  BC-001 @ 12/15 08:23-09:45 → 产出 BC-001-...-003
```

每条物料移动记录（"X 批次 → Y 工序 → Z 设备 → W 产出批次"）都同时记录三条链的 ID，这样反查时任意一条链都能 join 到另两条。物料移动表设计：

```sql
CREATE TABLE material_movement (
    id BIGINT PRIMARY KEY,
    event_time TIMESTAMP NOT NULL,
    -- 料链
    from_lot_id BIGID,         -- 上游批次号（料批/卷号/SN）
    from_lot_type VARCHAR(16), -- POWDER/COIL/CELL/...
    -- 工序链
    work_order_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,    -- 1=涂布 2=辊压 ... 7=分容
    -- 设备链
    device_id BIGINT NOT NULL,
    -- 产出
    to_lot_id BIGINT,            -- 本工序产出的新批次号
    to_lot_type VARCHAR(16),
    quantity DECIMAL(12,4),
    unit VARCHAR(16),
    -- 关键：前后移动的关联（递归追溯的入口）
    prev_movement_id BIGINT,     -- 上游的 movement_id
    next_movement_id BIGINT      -- 下游的 movement_id（首次入库时为空）
);
CREATE INDEX idx_mm_from_lot ON material_movement(from_lot_id);
CREATE INDEX idx_mm_to_lot ON material_movement(to_lot_id);
CREATE INDEX idx_mm_wo_op ON material_movement(work_order_id, operation_id);
```

电芯 7 段工艺的物料移动链条：

```
涂布:   粉料 LFP-PW-...-007   → BC-001 @ 08:23 → 极片卷 BC-...-003
辊压:   极片卷 BC-...-003     → RP-001 @ 10:15 → 辊压卷 RP-...-003
分切:   辊压卷 RP-...-003     → SL-001 @ 11:30 → 分切小卷 SL-...-001~024
卷绕:   分切小卷 SL-...-001~024 → WND-003 @ 14:30 → 裸芯 SN X280A...0001-0240
注液:   裸芯 SN + 电解液 EC-...-005 → FL-001 @ 16:00 → 注液裸芯 SN（同 SN）
化成:   注液裸芯 SN           → F1-012 @ 22:00 → 化成电芯 SN（同 SN）
分容:   化成电芯 SN           → FC-001 @ 次日 → 成品 SN（同 SN）
```

每行 material_movement 都有 prev_movement_id 指向上一段的 movement，next_movement_id 指向下一段——整条链是一个**双向链表**。

### CTE 递归 SQL：30 分钟追溯到 30 秒

老李用 11 天做的事，本质是**从成品 SN 反向走完 6 段 material_movement 链**——SQL 用 CTE 递归 30 秒搞定：

```sql
-- 反向追溯：从成品 SN 反查到粉料批次
WITH RECURSIVE backward_trace AS (
    -- 起点：从 SN 找到它在分容工序的 movement
    SELECT m.id, m.from_lot_id, m.to_lot_id, m.operation_id, m.device_id,
           m.prev_movement_id, 1 AS depth
    FROM material_movement m
    JOIN lot l ON l.id = m.to_lot_id
    WHERE l.lot_no = 'X280A2025121500231'  -- SN-231
      AND m.operation_id = 7  -- 分容工序

    UNION ALL

    -- 递归：沿 prev_movement_id 反向走
    SELECT m.id, m.from_lot_id, m.to_lot_id, m.operation_id, m.device_id,
           m.prev_movement_id, t.depth + 1
    FROM material_movement m
    JOIN backward_trace t ON m.id = t.prev_movement_id
    WHERE t.depth < 20  -- 防止无限递归
)
SELECT * FROM backward_trace ORDER BY depth;
```

这条 SQL 返回 SN-231 的完整反向追溯链：分容→化成→注液→卷绕→分切→辊压→涂布，每段的 from_lot_id / to_lot_id / device_id 都查出来。

**向前追溯**（从粉料批次找到所有受影响的成品 SN）反过来：

```sql
WITH RECURSIVE forward_trace AS (
    -- 起点：从粉料批次找到它在涂布工序的 movement
    SELECT m.id, m.from_lot_id, m.to_lot_id, m.operation_id, m.device_id,
           m.next_movement_id, 1 AS depth
    FROM material_movement m
    JOIN lot l ON l.id = m.from_lot_id
    WHERE l.lot_no = 'LFP-PW-20251128-007'  -- 粉料批次
      AND m.operation_id = 1  -- 涂布工序

    UNION ALL

    -- 递归：沿 next_movement_id 正向走
    SELECT m.id, m.from_lot_id, m.to_lot_id, m.operation_id, m.device_id,
           m.next_movement_id, t.depth + 1
    FROM material_movement m
    JOIN forward_trace t ON m.id = t.next_movement_id
    WHERE t.depth < 20
)
SELECT * FROM forward_trace ORDER BY depth;
```

向前追溯返回粉料批次 007 影响的所有下游批次——极片卷、辊压卷、分切小卷、裸芯 SN、注液裸芯、化成电芯、成品 SN。**最后一行 depth=7 的 to_lot_id 就是受影响的成品 SN 列表**，召回范围一次 SQL 圈定。

### CTE 递归性能：14 万条 movement 30 秒

老李担心递归 SQL 性能。我们用 14 万条 material_movement（电芯厂 A 半年数据）做压测：

<table>
<thead><tr>
<th>追溯方向</th>
<th>数据量</th>
<th>耗时</th>
<th>索引使用</th>
</tr></thead>
<tbody>
<tr>
<td>反向追溯（SN→粉料）</td>
<td>14 万 movement</td>
<td>0.32 秒</td>
<td>idx_mm_from_lot + prev_movement_id</td>
</tr>
<tr>
<td>正向追溯（粉料→所有 SN）</td>
<td>14 万 movement</td>
<td>28.7 秒</td>
<td>idx_mm_to_lot + next_movement_id</td>
</tr>
<tr>
<td>多 SN 批量追溯</td>
<td>36 个 SN 反向</td>
<td>1.4 秒</td>
<td>同上</td>
</tr>
</tbody>
</table>

正向追溯慢是因为一棵粉料批次可能影响几百个成品 SN，递归深度 7 层、宽度 N 倍增长。优化手段：

1. **next_movement_id 必须建索引**——单条 B-tree 索引能跳过全表扫描。
2. **递归深度限制 depth < 20**——电芯最多 7 段，20 层足够，防止数据有环时无限递归。
3. **大批量追溯用物化视图**——若常查"粉料批次 X 影响哪些 SN"，建物化视图 mv_lot_forward_cache 增量更新。
4. **召回场景用 forward_trace 反向**——召回是从成品 SN 反查粉料，走 backward_trace 路径（30 秒内）。

## 正确姿势：批次三链的工程实现

### 1. 物料接收强制扫批次号

事故根因是下游工序接收时不扫上游批次号。工程上的解法是**物料接收必须扫 from_lot_id**，不扫不让接收：

```java
@Service
public class MaterialReceiveService {
    @Autowired private MaterialMovementMapper movementMapper;
    @Autowired private LotService lotService;

    @Transactional
    public MovementResult receive(ReceiveCommand cmd) {
        // 1. 校验 from_lot_id 必须扫
        if (cmd.getFromLotId() == null) {
            throw new BizException("物料接收必须扫上游批次号");
        }

        // 2. 校验 from_lot 存在且未消耗完
        Lot fromLot = lotService.getById(cmd.getFromLotId());
        if (fromLot == null) {
            throw new BizException("上游批次 " + cmd.getFromLotId() + " 不存在");
        }
        BigDecimal consumed = movementMapper.sumConsumed(cmd.getFromLotId());
        if (consumed.add(cmd.getQuantity()).compareTo(fromLot.getQuantity()) > 0) {
            throw new BizException("上游批次余量不足: 余 " +
                fromLot.getQuantity().subtract(consumed) +
                " 需 " + cmd.getQuantity());
        }

        // 3. 生成本工序产出的新批次（to_lot）
        Lot toLot = lotService.createChildLot(fromLot, cmd);

        // 4. 写 material_movement，链上 prev_movement_id 指向上游最近一条
        MaterialMovement prev = movementMapper.findLatestByToLot(cmd.getFromLotId());
        MaterialMovement m = new MaterialMovement();
        m.setFromLotId(cmd.getFromLotId());
        m.setToLotId(toLot.getId());
        m.setWorkOrderId(cmd.getWorkOrderId());
        m.setOperationId(cmd.getOperationId());
        m.setDeviceId(cmd.getDeviceId());
        m.setQuantity(cmd.getQuantity());
        m.setPrevMovementId(prev != null ? prev.getId() : null);
        movementMapper.insert(m);

        // 5. 反向更新上游 movement 的 next_movement_id
        if (prev != null) {
            movementMapper.updateNextMovementId(prev.getId(), m.getId());
        }

        return new MovementResult(m.getId(), toLot.getId());
    }
}
```

### 2. SN 在注液后冻结，全程不变

电芯的特殊性：**注液工序开始才有 SN，注液之前都是按卷/批追溯，注液之后按 SN 追溯**。所以 SN 必须在注液工序生成，**生成后冻结全程不变**（化成、分容都用同一个 SN）：

```java
@Service
public class SnService {
    @Autowired private LotService lotService;

    // 注液工序生成 SN
    @Transactional
    public List<String> generateSnForFilling(String woNo, int count) {
        WorkOrder wo = woService.getByNo(woNo);
        String snPrefix = buildSnPrefix(wo);  // X280A20251215
        List<String> sns = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String sn = snPrefix + String.format("%04d", i);  // X280A202512150001
            lotService.createSnLot(sn, wo.getProductItemId());
            sns.add(sn);
        }
        return sns;
    }

    // 化成/分容工序接收时校验 SN 已存在
    public void validateSn(String sn) {
        if (lotService.getByLotNo(sn) == null) {
            throw new BizException("SN " + sn + " 未在注液工序生成");
        }
    }
}
```

### 3. 召回边界规则引擎

客户合同里"同一物料批次"召回的定义五花八门，必须用规则引擎配置：

```java
public interface RecallRule {
    List<Long> findAffectedLots(String seedLotNo);
}

// 规则 1：同一粉料批次召回
@Component("samePowderLot")
public class SamePowderLotRecallRule implements RecallRule {
    @Autowired private TraceDao traceDao;
    public List<Long> findAffectedLots(String powderLotNo) {
        // CTE 正向追溯 depth=1 到 depth=7
        return traceDao.forwardTrace(powderLotNo, 7);
    }
}

// 规则 2：同一化成曲线召回（柜+曲线）
@Component("sameFormationCurve")
public class SameFormationCurveRecallRule implements RecallRule {
    public List<Long> findAffectedLots(String curveCode) {
        // 找到所有用该曲线的化成 movement，再正向走到成品 SN
        return traceDao.findByFormationCurve(curveCode);
    }
}

// 规则 3：同电解液批次召回
@Component("sameElectrolyteLot")
public class SameElectrolyteLotRecallRule implements RecallRule {
    public List<Long> findAffectedLots(String electrolyteLotNo) {
        // 找到所有用了该电解液的注液 movement，正向走到成品
        return traceDao.forwardTraceFromOp(electrolyteLotNo, 4);  // 注液=op4
    }
}

@Service
public class RecallService {
    @Autowired private Map<String, RecallRule> rules;

    public RecallResult recall(String ruleName, String seedLotNo) {
        RecallRule rule = rules.get(ruleName);
        if (rule == null) {
            throw new BizException("召回规则 " + ruleName + " 不存在");
        }
        List<Long> affectedLotIds = rule.findAffectedLots(seedLotNo);

        // 区分已发客户 / 在库
        List<Lot> shipped = lotService.findShipped(affectedLotIds);
        List<Lot> inStock = lotService.findInStock(affectedLotIds);

        return new RecallResult(affectedLotIds, shipped, inStock);
    }
}
```

## 数据说话：11 天 → 30 秒的对比

上线批次三链后，跑了一次召回演练——模拟客户投诉 SN-231，按"同一粉料批次"召回：

<table>
<thead><tr>
<th>维度</th>
<th>老李手工（事故 3）</th>
<th>批次三链系统</th>
<th>改进</th>
</tr></thead>
<tbody>
<tr>
<td>追溯耗时</td>
<td>11 天</td>
<td>32 秒</td>
<td>**3 万倍**</td>
</tr>
<tr>
<td>涉及人</td>
<td>5 个跨工序会议</td>
<td>1 个质量工程师</td>
<td>-80% 人力</td>
</tr>
<tr>
<td>Excel 表数</td>
<td>12 张</td>
<td>0 张</td>
<td>-100%</td>
</tr>
<tr>
<td>召回范围准确率</td>
<td>36 颗（猜的）</td>
<td>36 颗（精确）</td>
<td>100% 准确</td>
</tr>
<tr>
<td>误召回风险</td>
<td>高（猜时间窗口）</td>
<td>0</td>
<td>0 误召回</td>
</tr>
<tr>
<td>召回报告生成</td>
<td>11 天手写</td>
<td>自动生成</td>
<td>-100% 工时</td>
</tr>
<tr>
<td>不同规则召回</td>
<td>重新 11 天</td>
<td>切规则 30 秒</td>
<td>灵活性指数级</td>
</tr>
</tbody>
</table>

关键发现：

1. **32 秒圈定召回范围**——CTE 递归 SQL 0.32 秒，剩余 31.7 秒是 join 物料批次表查"已发客户/在库"状态、生成召回报告 PDF。
2. **召回准确率 100%**——老李手工时是按时间窗口猜的，可能漏召回也可能多召回。批次三链是按 material_movement 链精确圈定，**不多不少**。
3. **切换召回规则 30 秒**——客户合同改定义"同电解液批次"召回时，老李要再花 11 天，系统切 RecallRule 只需 30 秒。

## 面试怎么答：批次追溯与召回

**问：电芯厂的批次追溯与机械装配的 SN 追溯有什么本质不同？**

答：三点本质差异：① **追溯单位**——机械装配全程按 SN 追溯（每颗螺丝都有 SN），电芯在注液前按"批次/卷号"追溯（粉料按批次、极片按卷号），注液后才按 SN 追溯，**追溯单位中途切换**；② **BOM 形态**——机械装配 BOM 是离散（1 件壳 + 4 颗螺丝 = 1 件成品），电芯 BOM 是连续-离散混合（200g 粉料 → 1 卷极片 → 切 24 段 → 卷 240 颗裸芯），**1 对 N 展开**，追溯链宽度爆炸式增长；③ **召回规则**——机械召回按"同批次零件"召回，规则单一，电芯召回按客户合同定义（同粉料/同电解液/同化成曲线/同设备），**多规则可配置**，需要规则引擎。

**问：CTE 递归 SQL 追溯有什么性能瓶颈？怎么解决？**

答：CTE 递归性能瓶颈在三个地方：① **递归深度**——电芯 7 段工艺递归深度 7，看起来不大，但每段是 1 对 N 展开（1 卷极片 → 24 段 → 240 颗裸芯），递归宽度指数增长，14 万条 movement 数据下正向追溯 28 秒；② **索引使用**——递归 SQL 必须用 prev_movement_id / next_movement_id 索引，否则全表扫描直接挂；③ **环检测**——若数据有环（movement A 的 next 指向 B，B 的 next 又指回 A），递归会无限循环，必须 depth < 20 兜底。解决方案：① next_movement_id 单独建索引；② depth 限制 20 层；③ 大批量追溯用物化视图 mv_lot_forward_cache 增量更新；④ 召回场景优先用反向追溯（30 秒以内）。

**问：物料接收强制扫批次号为什么能解决追溯链断的问题？**

答：事故根因是下游工序接收时不扫上游批次号——辊压按"辊数"接收、卷绕按"裸芯数量"接收，都只记自己工序的产出批次，**没把上游批次号关联进来**。强制扫批次号从工程上做了三件事：① **数据完整性**——from_lot_id 字段非空，不允许 NULL；② **余量校验**——扫的 from_lot 余量必须够，不够不让接收，防止超领；③ **双向链表**——扫的瞬间同时写 prev_movement_id 指向上游、反向更新上游的 next_movement_id 指向自己，链表双向打通。这三件事让追溯链物理上不可能断，**事故 3 的 11 天追溯直接降到 30 秒**。

**问：召回规则怎么做成可配置？不能写死在代码里？**

答：召回规则用 RecallRule 接口 + 多实现类 + Spring 注入 Map<String, RecallRule> 实现。每个规则（同粉料批次、同电解液批次、同化成曲线、同设备）一个实现类，规则名作为 key。运营在召回界面选规则名，系统从 Map 里取对应实现类调 findAffectedLots()。**规则配置不在代码里写死**——规则元数据（规则名、参数定义、SQL 模板）放在 recall_rule_config 表里，运营可加新规则不重启服务（用 Spring 动态 Bean 注册或策略模式 + 反射）。客户合同里写"同 X 召回"时，运营在配置表加一条规则，**业务无代码改动**。

## 落地清单：批次追溯工程化动作

1. **物料移动表 material_movement**：from_lot_id + to_lot_id + work_order_id + operation_id + device_id + prev_movement_id + next_movement_id，**六元组必填**，缺一不可追溯。
2. **三个索引必建**：idx_mm_from_lot（反向追溯）、idx_mm_to_lot（正向追溯）、idx_mm_wo_op（按工单查所有 movement）。
3. **物料接收强制扫批次号**：from_lot_id 非空，校验余量，写 prev/next 双向链表，禁止"按数量接收不扫批次"。
4. **SN 在注液工序生成后冻结**：注液前按批次/卷号追溯，注液后按 SN 追溯，SN 全程不变（化成/分容都用同 SN）。
5. **CTE 递归 SQL 双向追溯**：backward_trace（SN→粉料）+ forward_trace（粉料→SN），depth < 20 兜底防环。
6. **召回规则引擎**：RecallRule 接口 + 多实现 + Map 注入 + recall_rule_config 配置表，业务无代码改动加规则。
7. **召回报告自动生成**：圈定受影响 lot 后，自动 join 库存表区分已发客户/在库，生成 PDF 报告。
8. **黄金回归**：① 注液工序生成的 SN 在化成/分容 movement 中作为 from_lot_id 必须出现；② 辊压接收不扫极片卷号必须被 from_lot_id 非空校验拦下；③ CTE 递归 depth=20 时返回的行数不超过预期；④ 召回规则 samePowderLot 圈定的 SN 数与 forward_trace SQL 结果一致。

下一章我们离开追溯链进入绩效分析层，看 OEE 三因子怎么把"88% OEE 实际是 77%"的口径问题拆穿——这是事故 2 的核心防线。
