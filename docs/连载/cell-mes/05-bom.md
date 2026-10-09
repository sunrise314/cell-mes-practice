---
title: ch05 工艺路线与 BOM 展开：替代料 + 派工级联 + 改派校验
slug: cell-mes-05-bom
---

## 事故现场：314Ah 改派 280Ah，正极粉料号没换，整柜 672 颗报废

2025 年 12 月 4 日 14:20，电芯厂 A 接到客户临时改单——原计划次日上线的 314Ah 半固态电芯（型号 X-314）因客户 Pack 装配进度提前，改派为 280Ah 同型号系列（X-280）顶上。计划员在 MES 派工界面点了"改派"：原派工单 `WO-20251204-001` 的 1000 颗 X-314 改派为 1000 颗 X-280，目标设备仍是化成柜 F1-012，开班次 B 班。

改派操作 8 秒完成，MES 给化成柜 F1-012 下发了新曲线 `FORN-280-CHG-V3`，化成柜按 280Ah 工艺曲线（0.05C 恒流→4.2V 恒压→0.05C 截止）开始充电。但**没有人通知前工序的涂布机 BC-001**：X-314 用的是 314Ah 专用正极粉料号 `LFP-POS-314`（面密度 240 g/m²），X-280 用的是 280Ah 专用正极粉料号 `LFP-POS-280`（面密度 200 g/m²），两者料号差一位、面密度差 40 g/m²。

涂布线 14:25 已经在按原派工单继续涂布——面密度 240 g/m² 的 314Ah 极片卷，15:30 进入辊压，17:00 进入分切，19:00 进入卷绕，22:00 进入注液，次日凌晨 02:00 进入化成柜 F1-012。化成柜按 280Ah 曲线充电，**实际容量是 314Ah 的极片装在 280Ah 体积的壳体里，化成后壳体鼓胀、电流密度超规格，0.05C 截止时电压只到 4.05V（规格 4.2V）**。次日上午 8 点开柜，**整柜 672 颗电芯外观鼓胀、容量 195Ah（规格 275Ah 下限），直接报废**。

损失核算：672 颗 × 280Ah × 1.0 元/Ah = **18.8 万元材料损失 + 化成柜 12 小时占用 + 客户投诉一次**。厂长又一次停产复盘，问的第一个问题就是：「改派不是 MES 的核心功能吗？为什么没拦住？」

## 排查：BOM 替代料级联失效的三个缺口

复盘时把改派操作全链路拉出来，发现 MES 的 BOM 展开有**三个缺口级联失效**，任何一个补上都不会报废这柜料：

**缺口 1：改派只改派工单头，没改派工单 BOM 行**。MES 派工单表 `work_order` 有头表（工单号、产品料号、数量、目标设备、班次）和行表 `work_order_bom`（BOM 行：序号、子料号、用量、替代料号列表）。改派操作只 UPDATE 了头表的产品料号 `X-314 → X-280`，行表的 BOM 行没动——`LFP-POS-314` 还在 BOM 行的子料号字段里，前工序的涂布线继续按这个料号领料。

**缺口 2：替代料规则没在改派时触发放行**。MES 里 X-314 的正极粉料 `LFP-POS-314` 配了替代料 `LFP-POS-280`（业务上确实允许替代——化学体系相同、面密度不同、容量差异通过化成曲线补偿），但**替代料的"放行规则"没在改派时被触发**——放行规则要求"改派时必须重新展开 BOM 并校验所有子料号是否与目标产品料号匹配"，这条规则在 MES 的 Routing Service 里写了，但改派 API 走的是另一个 Service，**改派 Service 没调 Routing Service 的 reExpandBom 方法**。

**缺口 3：化成曲线与产品料号没做双向校验**。化成曲线 `FORN-280-CHG-V3` 是 280Ah 专用，X-314 应该用 `FORN-314-CHG-V2`，但化成柜下发接口只校验"曲线存在+设备支持该曲线"，**没校验"曲线的容量规格与产品料号的容量规格一致"**。化成柜按错误的曲线充电，电压永远到不了 4.2V 截止。

三个缺口单独看都不是大问题，但**级联失效**就报废了一柜料。这暴露了一个工程原则：**BOM 展开、工艺路线、派工单、化成参数这些数据不是孤岛，改任何一个都要级联校验所有相关方**。

## 底层原理：BOM、Routing、Dispatch 的三件套关系

要讲清楚为什么改派会失效，必须先把 MES 的三件套——**BOM（物料清单）、Routing（工艺路线）、Dispatch（派工单）**——的关系说透。这三件套是 ISA-95 Level 3 生产调度的核心数据模型，缺任何一个都跑不动。

### BOM：从成品倒推到原料的树状展开

BOM（Bill of Materials）描述"做一颗 280Ah 电芯需要哪些子料、各用多少"。电芯厂的 BOM 是**多层树**：

```
成品 X-280（280Ah 半固态电芯）
├── 正极极片 LFP-POS-280（面密度 200 g/m²）
│   ├── 正极粉料 LFP-POWDER-280（LFP 材料 200g + 导电剂 8g + 粘结剂 6g）
│   ├── 铝箔 AL-FOIL-16（16μm）
│   └── 极耳 AL-TAB-280
├── 负极极片 GR-NEG-280
│   ├── 负极粉料 GR-POWDER-280
│   ├── 铜箔 CU-FOIL-8（8μm）
│   └── 极耳 CU-TAB-280
├── 隔膜 SEP-280
├── 电解液 EC-280
├── 铝壳 CASE-280
├── 顶盖 LID-280
└── 极柱 PILLAR-280
```

BOM 的关键属性：
- **子料号**（child_item）：子料的唯一编号
- **用量**（quantity）：单位产品的子料用量，电芯 BOM 用量是 1（1 颗电芯用 1 张正极片），但粉料 BOM 是 200g（1 张正极片用 200g 粉料）
- **替代料**（substitutes）：可选替代子料的料号列表，按优先级排序
- **工序绑定**（operation_id）：子料在哪个工序投入，正极粉在涂布投入，电解液在注液投入
- **生效期**（effective_date）：BOM 行的生效起止时间，同一料号不同时期可能用不同 BOM 行

BOM 表设计（简化版）：

```sql
CREATE TABLE bom_header (
    id BIGINT PRIMARY KEY,
    product_item_id BIGINT NOT NULL,   -- 父料号
    revision VARCHAR(32) NOT NULL,     -- 版本
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(16) DEFAULT 'ACTIVE'
);
CREATE TABLE bom_line (
    id BIGINT PRIMARY KEY,
    bom_header_id BIGINT NOT NULL REFERENCES bom_header(id),
    line_no INT NOT NULL,
    child_item_id BIGINT NOT NULL,     -- 子料号
    quantity DECIMAL(12,4) NOT NULL,
    unit VARCHAR(16) NOT NULL,         -- g / pcs / m²
    operation_id BIGINT,               -- 在哪个工序投入
    substitutes JSONB,                 -- [{"item_id":123,"priority":1},{"item_id":456,"priority":2}]
    effective_from DATE,
    effective_to DATE,
    UNIQUE(bom_header_id, line_no)
);
```

### Routing：从投入到产出的工艺段次序

Routing（工艺路线）描述"一颗电芯从粉料到成品要经过哪些工序、每道工序在什么设备上做什么动作"。电芯厂的 Routing 是 7 段：

```
1. 涂布（Coating）  - BC-001/002 - 投入正极粉/铝箔 - 产出正极极片卷
2. 辊压（Rolling）  - RP-001     - 辊压极片         - 产出辊压后极片
3. 分切（Slitting） - SL-001     - 分切到电芯宽度   - 产出分切后极片
4. 卷绕（Winding）  - WND-001~008 - 卷绕成电芯裸芯   - 产出裸芯
5. 注液（Filling）  - FL-001     - 注入电解液        - 产出注液后裸芯
6. 化成（Formation）- F1-001~096 - 充电活化          - 产出化成后电芯
7. 分容（Grading）  - FC-001~096 - 容量分档         - 产出成品
```

每段 Routing 的关键属性：
- **工序号**（operation_id）：1-7
- **工序名**（operation_name）
- **设备组**（resource_group_id）：该工序可用的设备组，涂布有 BC-001 和 BC-002 两台可互替
- **标准工时**（standard_duration_min）：单件标准节拍，涂布 0.5 片/min（极片 2m × 0.6m）
- **投入料号**（input_items）：本工序投入的子料
- **产出料号**（output_item）：本工序的产出
- **工艺参数**（process_params）：化成曲线 FORN-280-CHG-V3 是化成工序的参数
- **前后工序**（prev_op / next_op）：工序的次序

Routing 表设计：

```sql
CREATE TABLE routing (
    id BIGINT PRIMARY KEY,
    product_item_id BIGINT NOT NULL,
    revision VARCHAR(32) NOT NULL,
    status VARCHAR(16) DEFAULT 'ACTIVE'
);
CREATE TABLE routing_operation (
    id BIGINT PRIMARY KEY,
    routing_id BIGINT NOT NULL REFERENCES routing(id),
    seq INT NOT NULL,                  -- 工序号 1-7
    operation_name VARCHAR(64) NOT NULL,
    resource_group_id BIGINT,          -- 可用设备组
    standard_duration_min DECIMAL(8,2),
    input_item_ids BIGINT[],           -- 本工序投入的子料
    output_item_id BIGINT,             -- 本工序产出
    process_params JSONB,              -- {"formation_curve":"FORN-280-CHG-V3"}
    UNIQUE(routing_id, seq)
);
```

### Dispatch：把 BOM 和 Routing 落到具体设备具体班次

派工单（Dispatch / Work Order）是把抽象的 BOM 和 Routing 落到**具体设备、具体班次、具体操作员、具体开始时间**的实例：

- 工单号：WO-20251204-001
- 产品料号：X-280
- 数量：1000
- 工艺路线：X-280 的 Routing v3
- BOM：X-280 的 BOM v5
- 每段工序的派工：
  - 涂布：BC-001 / A 班 / 08:00 / 派工单号 WO-...-OP1
  - 化成：F1-012 / B 班 / 02:00 次日 / 派工单号 WO-...-OP6
- 每段工序的 BOM 行展开：化成工序投入 WO-...-OP6 投入裸芯，涂布工序投入 WO-...-OP1 投入 LFP-POWDER-280 粉料

**BOM 和 Routing 是模板，派工单是实例**——派工单把模板的每个工序、每个 BOM 行实例化到"具体时间具体设备具体人"。改派是改实例，但实例必须与模板一致，所以改实例的"产品料号"必须重新展开模板的 BOM 和 Routing，校验实例的 BOM 行和工序参数与模板匹配。

### 三件套的级联关系图

```
[模板层]              [实例层]
BOM Header  ─────展开──> Work Order BOM Line
     │                          │
     │ 替代料                    │ 领料校验
     ↓                          ↓
BOM Substitutes ──选择──> Material Lot (实际领料批次)
     │                          │
     │                          │
Routing     ─────展开──> Work Order Operation
     │                          │
     │ 工艺参数                  │ 下发设备
     ↓                          ↓
Process Params ──选择──> Device Recipe (实际下发参数)
```

**事故根因**：改派时只改了实例层的"产品料号"字段，没重新触发模板层到实例层的展开，导致实例层的 BOM Line（粉料 LFP-POS-314）和模板层（X-280 的 BOM Line 是 LFP-POS-280）不一致，化成曲线（FORN-280-CHG-V3）与产品料号（X-314 的极片）也不一致。

## 正确姿势：改派的级联校验四步法

事故复盘后，我们重写了改派 API 的完整流程，**核心是改派不只是改字段，是一次小型 BOM 重展开 + Routing 校验**。流程分四步：

### 第 1 步：改派前置校验（Pre-Flight Check）

改派前先校验"能不能改"，避免改到一半失败留下脏数据：

```java
@Service
public class DispatchChangeService {
    @Autowired private WorkOrderService woService;
    @Autowired private BomService bomService;
    @Autowired private RoutingService routingService;
    @Autowired private DeviceRecipeService recipeService;

    public PreFlightResult preFlight(String woNo, String newProductItemId) {
        WorkOrder wo = woService.getByNo(woNo);
        // 1.1 校验工单状态：只在 CREATED/RELEASED 状态可改派
        if (wo.getStatus() == WorkOrderStatus.STARTED || wo.getStatus() == WorkOrderStatus.COMPLETED) {
            return PreFlightResult.fail("工单状态为 " + wo.getStatus() + ", 不允许改派");
        }

        // 1.2 校验已经发料的工序：若已有工序领了原 BOM 的料，需要先退料再改派
        List<WorkOrderOperation> issuedOps = woService.getIssuedOperations(woNo);
        if (!issuedOps.isEmpty()) {
            return PreFlightResult.fail("已发料工序: " + issuedOps.stream()
                .map(o -> o.getOperationName()).collect(Collectors.joining(","))
                + ", 请先退料再改派");
        }

        // 1.3 校验新 BOM 是否已发布
        BomHeader newBom = bomService.getActiveBom(newProductItemId);
        if (newBom == null) {
            return PreFlightResult.fail("产品 " + newProductItemId + " 无生效 BOM");
        }

        // 1.4 校验新 Routing 是否已发布
        Routing newRouting = routingService.getActiveRouting(newProductItemId);
        if (newRouting == null) {
            return PreFlightResult.fail("产品 " + newProductItemId + " 无生效 Routing");
        }

        // 1.5 校验目标设备是否支持新 Routing 的工序
        for (RoutingOperation op : newRouting.getOperations()) {
            ResourceGroup rg = resourceGroupService.get(op.getResourceGroupId());
            if (!rg.getDeviceIds().contains(wo.getTargetDeviceId())) {
                return PreFlightResult.fail("工序 " + op.getOperationName() +
                    " 的设备组不含目标设备 " + wo.getTargetDeviceId());
            }
        }

        // 1.6 校验新 BOM 子料的库存是否充足
        for (BomLine line : newBom.getLines()) {
            BigDecimal stock = inventoryService.getStock(line.getChildItemId());
            BigDecimal needed = line.getQuantity().multiply(wo.getQuantity());
            if (stock.compareTo(needed) < 0) {
                return PreFlightResult.fail("子料 " + line.getChildItemId() +
                    " 库存 " + stock + ", 需求 " + needed + ", 不足");
            }
        }

        // 1.7 校验化成曲线与产品料号容量规格匹配（事故 1 的核心校验）
        RoutingOperation formationOp = newRouting.getOperations().stream()
            .filter(o -> "化成".equals(o.getOperationName())).findFirst().orElse(null);
        if (formationOp != null) {
            String curveCode = formationOp.getProcessParams().getString("formation_curve");
            DeviceRecipe recipe = recipeService.getByCode(curveCode);
            Item newItem = itemService.get(newProductItemId);
            if (!recipe.getCapacitySpec().equals(newItem.getCapacitySpec())) {
                return PreFlightResult.fail("化成曲线 " + curveCode +
                    " 容量规格 " + recipe.getCapacitySpec() +
                    " 与产品 " + newItem.getItemId() + " 容量规格 " + newItem.getCapacitySpec() +
                    " 不一致");
            }
        }

        return PreFlightResult.ok();
    }
}
```

第 1.7 步是事故 1 的核心防线——化成曲线的容量规格必须与产品料号的容量规格一致，X-314 容量规格是 314Ah，X-280 是 280Ah，曲线 FORN-280-CHG-V3 的容量规格是 280Ah，**校验直接 fail**。

### 第 2 步：BOM 重新展开（Re-Expand）

前置校验通过后，开始事务级 BOM 重新展开——**删旧的派工单 BOM 行，按新 BOM 模板重新生成**：

```java
@Transactional
public void reExpandBom(String woNo, String newProductItemId) {
    // 2.1 删旧 BOM 行
    workOrderBomMapper.deleteByWoNo(woNo);

    // 2.2 按新 BOM 模板重新展开
    BomHeader newBom = bomService.getActiveBom(newProductItemId);
    for (BomLine line : newBom.getLines()) {
        WorkOrderBomLine woLine = new WorkOrderBomLine();
        woLine.setWoNo(woNo);
        woLine.setLineNo(line.getLineNo());
        woLine.setChildItemId(line.getChildItemId());
        woLine.setQuantity(line.getQuantity().multiply(woService.getQuantity(woNo)));
        woLine.setUnit(line.getUnit());
        woLine.setOperationId(line.getOperationId());
        woLine.setSubstitutes(line.getSubstitutes());
        woLine.setStatus("PENDING");  // 待领料
        workOrderBomMapper.insert(woLine);
    }

    // 2.3 同步更新工单头表产品料号
    woService.updateProductItemId(woNo, newProductItemId);
}
```

### 第 3 步：Routing 重新展开 + 工序派工重排

```java
@Transactional
public void reExpandRouting(String woNo, String newProductItemId) {
    // 3.1 删旧工序派工
    workOrderOperationMapper.deleteByWoNo(woNo);

    // 3.2 按新 Routing 重新展开
    Routing newRouting = routingService.getActiveRouting(newProductItemId);
    for (RoutingOperation op : newRouting.getOperations()) {
        WorkOrderOperation woOp = new WorkOrderOperation();
        woOp.setWoNo(woNo);
        woOp.setSeq(op.getSeq());
        woOp.setOperationName(op.getOperationName());
        woOp.setResourceGroupId(op.getResourceGroupId());
        woOp.setStandardDurationMin(op.getStandardDurationMin());
        woOp.setProcessParams(op.getProcessParams());
        woOp.setStatus("WAITING");
        workOrderOperationMapper.insert(woOp);
    }

    // 3.3 重新排程：每段工序的目标设备和开始时间
    // 关键：化成工序必须排到容量规格匹配的化成柜
    scheduleService.reSchedule(woNo);
}
```

### 第 4 步：化成曲线下发前最后一次校验

即使前 3 步都过了，**化成曲线下发到化成柜前还要做最后一次双向校验**——这是事故 1 的最后一道防线，防止有人在改派后手动改了化成曲线：

```java
public class FormationCurveDispatcher {
    public DispatchResult dispatch(String woNo, String deviceCode) {
        WorkOrder wo = woService.getByNo(woNo);
        WorkOrderOperation formationOp = woService.getOpBySeq(woNo, 6);  // 化成
        String curveCode = formationOp.getProcessParams().getString("formation_curve");

        // 最后一次双向校验
        DeviceRecipe recipe = recipeService.getByCode(curveCode);
        Item product = itemService.get(wo.getProductItemId());
        if (!recipe.getCapacitySpec().equals(product.getCapacitySpec())) {
            throw new BizException("曲线容量规格 " + recipe.getCapacitySpec() +
                " 与产品容量规格 " + product.getCapacitySpec() + " 不一致，禁止下发");
        }

        // 校验曲线与设备型号
        Device device = deviceService.getByCode(deviceCode);
        if (!recipe.getDeviceModels().contains(device.getModel())) {
            throw new BizException("曲线 " + curveCode + " 不支持设备型号 " + device.getModel());
        }

        // 调用采集层 OPC UA/Modbus 下发曲线参数
        return collectAdapter.writeRecipe(deviceCode, recipe.getParams());
    }
}
```

## 数据说话：改派级联校验前后对比

级联校验四步法上线后，跑了 3 个月，对比事故前后的改派场景：

<table>
<thead><tr>
<th>维度</th>
<th>事故前（无校验）</th>
<th>事故后（四步校验）</th>
<th>改进</th>
</tr></thead>
<tbody>
<tr>
<td>改派操作总次数</td>
<td>月均 18 次</td>
<td>月均 22 次（改派更频繁，因为安全）</td>
<td>+22%</td>
</tr>
<tr>
<td>改派导致 BOM 行不一致</td>
<td>月均 2.3 次</td>
<td>0 次</td>
<td>**100% 消除**</td>
</tr>
<tr>
<td>改派导致化成曲线错配</td>
<td>月均 0.8 次</td>
<td>0 次</td>
<td>**100% 消除**</td>
</tr>
<tr>
<td>改派前置校验拦截率</td>
<td>不存在</td>
<td>14.5% 被前置校验拦下</td>
<td>拦下 3.2 次/月</td>
</tr>
<tr>
<td>改派平均耗时</td>
<td>8 秒（不校验）</td>
<td>220ms（前置校验+展开）</td>
<td>性能仍可接受</td>
</tr>
<tr>
<td>改派后人工核对工时</td>
<td>32 工时/月</td>
<td>4 工时/月</td>
<td>-87%</td>
</tr>
<tr>
<td>改派导致返工/报废</td>
<td>18.8 万元/次 × 0.8 次/月</td>
<td>0</td>
<td>**月省 15 万元**</td>
</tr>
</tbody>
</table>

关键发现：

1. **改派前置校验拦下 14.5%**——很多改派请求本身就不合理（已发料工序想改派、新 BOM 没发布、目标设备不支持工序），拦下后计划员重新走退料/补发布流程，避免了改派后才发现没料的尴尬。
2. **月省 15 万元返工成本**——按 0.8 次/月的化成曲线错配事故率，四步校验直接消除，月省成本可量化。
3. **改派后人工核对工时从 32 工时降到 4 工时**——以前改派完要人到现场看每道工序的 BOM 行、化成曲线对不对，现在系统自动校验，人只需要看异常告警。

## 面试怎么答：BOM 展开与替代料

**问：电芯厂 BOM 与机械装配 BOM 有什么本质不同？**

答：三点本质差异：① **BOM 形态**——机械装配 BOM 是离散的（1 件壳 + 4 颗螺丝 = 1 件成品），电芯 BOM 是连续-离散混合的（200g 粉料 → 1 张极片 → 切成 N 个极片 → 卷绕成 1 颗电芯），粉料按重量、极片按张数、电芯按颗数，单位不一；② **BOM 工序绑定**——机械 BOM 不绑工序（装配 BOM 列出全部零件，装配时按装配图取），电芯 BOM 必须绑工序（粉料只在涂布投入，电解液只在注液投入，**派工时按工序分段展开 BOM**）；③ **替代料规则**——机械替代料是"等径等长等材质"可替代（M5 螺丝不同厂家可替代），电芯替代料是"化学体系相同、面密度不同、化成曲线补偿"可替代，替代规则更复杂，**必须在 BOM 行上配 substitutes JSON 字段，改派时按优先级选择**。

**问：替代料在 MES 里怎么实现？直接 SQL 关联不行吗？**

答：SQL 关联能查到，但放行规则不能。MES 的替代料实现分三层：① **数据层**——BOM 行 `substitutes` 字段存 JSON 列表 `[{"item_id":123,"priority":1},{"item_id":456,"priority":2}]`，按优先级排序；② **选择层**——领料时按优先级+库存选择，主料库存够用主料，主料不够用优先级 1 替代料，仍不够用优先级 2 替代料，写一条 substitution_log 记录选择过程（合规审计要求）；③ **放行规则层**——替代料放行有 4 条规则必须满足：化学体系一致（LFP 不能替代 NCM）、面密度差异 < 20%、化成曲线已配对替代料的容量规格、客户允许替代（有些客户合同里明确不允许某些替代料）。**第 3 层是 SQL 关联做不到的，必须 Service 层规则引擎**。

**问：为什么改派必须重新展开 BOM，不能只改产品料号字段？**

答：改派只改产品料号字段是事故 1 的根因。原因：派工单的 BOM 行是从模板 BOM 展开来的**实例**，模板变了（产品料号变了 = 模板变了），实例必须重展开，否则实例的 BOM 行（LFP-POS-314）与模板（X-280 BOM 是 LFP-POS-280）不一致，前工序按实例领料，化成工序按模板下曲线，**实例-模板错配就是事故**。改派 API 必须事务级地做：① 前置校验能否改派；② 删旧 BOM 行；③ 按新模板 BOM 重新展开；④ 删旧工序派工；⑤ 按新 Routing 重新展开工序；⑥ 重新排程目标设备；⑦ 下发前最后一次化成曲线双向校验。**这 7 步在同一个事务里，要么全成功要么全回滚，不留半改状态**。

**问：化成曲线与产品料号怎么做的双向校验？**

答：化成曲线在 DeviceRecipe 表里有一个 `capacity_spec` 字段（如 280Ah、314Ah），产品料号在 Item 表里也有一个 `capacity_spec` 字段，**两个值相等才允许下发**。校验在两个时机触发：① 改派前置校验第 1.7 步——改派时校验新 Routing 的化成曲线 capacity_spec 与新产品 capacity_spec 一致；② 化成曲线下发前最后一次校验——下发到化成柜前再次校验，防止改派后有人手动改了曲线。**两次校验冗余设计**，因为化成曲线错配的损失大（一次 18.8 万），冗余校验成本低（两次 SELECT 几毫秒），ROI 极高。

## 落地清单：BOM 展开工程化动作

1. **BOM 多层树展开**：用 CTE 递归 SQL 展开多层 BOM（电芯 BOM 7 层），按工序分组返回；禁用应用层循环展开（N+1 性能灾难）。
2. **BOM 行绑工序**：BOM 行必须有 `operation_id` 字段，派工时按工序分段展开，禁用整张 BOM 一把展开（前工序不知道该领哪些料）。
3. **替代料 substitutes JSON 字段**：禁用单独 substitutes 表关联查询，JSON 字段直接在 BOM 行上，按 priority 排序，领料时 Service 层按优先级+库存选择。
4. **替代料放行规则引擎**：化学体系一致、面密度差异 < 20%、化成曲线容量规格匹配、客户合同允许——4 条规则在 Service 层校验，不满足全部规则不允许替代。
5. **改派前置校验四步法**：① 工单状态校验；② 已发料工序校验；③ 新 BOM/Routing 发布校验；④ 目标设备工序支持校验；⑤ 库存充足校验；⑥ 化成曲线-产品容量规格双向校验。
6. **改派事务级 BOM 重展开**：删旧 BOM 行 + 按新模板重新展开，单事务，禁用半改派（删了旧 BOM 行没插新行）。
7. **化成曲线下发双向校验**：改派时校验 + 下发前校验，冗余设计，单次校验成本 5ms。
8. **黄金回归**：① 改派 X-314 → X-280 时 LFP-POS-314 行必须被删，LFP-POS-280 行必须被插入；② 已发料工单改派必须被前置校验拦下；③ 化成曲线 FORN-280-CHG-V3 与 X-314 产品料号必须双向校验 fail；④ 替代料 substitutes 选择日志 substitution_log 必须记录选择理由。

下一章我们离开 BOM 层进入追溯层，看批次三链怎么把 36 颗电芯的召回从 11 天降到 30 分钟——这是事故 3 的核心防线。
