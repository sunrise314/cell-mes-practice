---
title: ch01 业务建模：7 段工艺 + ISA-95 设备层级 + 工单/BOM/工艺路线三件套
slug: cell-mes-01-modeling
---

## 事故现场：一次订单改派把全产线拖死 11 小时

电芯厂 A 上线 MES 第三个月，业务方来一个常见诉求：「这个月车厂追加 2000 颗 314Ah 高镍电芯订单，把原计划那 2000 颗 280Ah 半固态订单改派到下月，加急上 314Ah」。听上去是个 ERP 改派动作，但实际发生了：

- 涂布工序正极浆料配方不同（314Ah 用更高镍含量），浆料罐没清洗直接走 → 报废 1 罐 200kg 浆料 + 涂布机 4 小时清洗
- 辊压厚度规格从 134μm 改 142μm，PLC 参数要换，但辊压机操作员还是按 134μm 跑 → 第一卷 800m 报废
- 卷绕机卷绕针规格不同（314Ah 是更大电芯），换规格要换针，但备件库存查到月底才有 → 等料 11 小时
- 化成柜充放电程序不同（314Ah 是新化成曲线），新曲线没人写 → 工艺工程师现场写，又拖 3 小时

**这一改派从早上 9 点开干，到晚上 8 点才出第一颗 314Ah 合格电芯。** 全产线 11 小时几乎空转，加上报废的浆料、极片，损失 23 万。复盘时业务方说："我以为改派就是改 ERP 工单。"——他不知道 7 段工艺里**每一段都有独立的工艺参数、独立的关键料、独立的换型作业**，改派是全链改派。

## 排查：为什么 BOM 平铺不行、工艺路线必须建模

复盘发现根因：**电芯厂 A 的"工艺路线"只是一张 Excel 表**——每行一个工序名（涂布/辊压/分切/卷绕/注液/化成/分容），每行后面跟一串参数（厚度、速度、张力、电流…）。这张 Excel 有三个致命缺陷：

1. **工序是平铺的，不建模依赖关系**——化成必须等卷绕完、注液必须等化成完，但 Excel 里这七行没有前置后置关系，无法自动算"改派时要换哪些工序"。
2. **BOM 与工艺路线没绑定**——BOM 写"314Ah 电芯 = 正极粉 + 负极粉 + 隔膜 + 电解液 + 铝塑膜 + 极柱"，但**没写每个料在哪个工序投入、替代料是什么**——314Ah 的正极粉料号和 280Ah 不同，但 BOM 不体现这层关系。
3. **关键工艺参数与料号绑定不清晰**——314Ah 化成曲线参数集和 280Ah 不同，但都散在工程师电脑里的 Word 文档里，没有版本管理，没有派工单下发时的参数自动加载。

业内对这三件套的标准定义来自 ISA-88（批次配方标准）+ ISA-95（设备层级标准）：

- **工艺路线（Routing）**：定义工单必须经过哪些工序、工序的前置后置关系、各工序的标准工时。
- **BOM（Bill of Materials）**：定义产品需要哪些料、料的数量、料在哪个工序投入、是否有替代料。
- **派工单（Dispatch Order）**：把工单按工艺路线展开到具体设备、具体班次、具体料号、具体参数集。

这三件套分开建、相互引用，是 MES 业务建模的"必经之路"。

## 底层原理：ISA-95 设备层级与 ISA-88 工艺路线树

### ISA-95 设备层级（Equipment Hierarchy）

ISA-95 把工厂物理资产按 5 层切分：

```
Enterprise       企业        集团（如：宁德时代）
  └─ Site        工厂        物理地址（如：溧阳工厂）
      └─ Area    车间/区域   工艺大段（如：极片车间/装配车间/化成车间）
          └─ WorkCenter   工作中心   工序级设备组（如：涂布 1 线/涂布 2 线）
              └─ WorkCell 工作单元   具体设备（如：涂布机 BC-001）
```

电芯厂 A 的设备层级落地后：

<table>
<thead><tr>
<th>Enterprise</th>
<th>Site</th>
<th>Area</th>
<th>WorkCenter</th>
<th>WorkCell</th>
</tr></thead>
<tbody>
<tr>
<td>CellCo</td>
<td>长三角工厂</td>
<td>极片车间</td>
<td>涂布 1 线</td>
<td>BC-001/BC-002</td>
</tr>
<tr>
<td>CellCo</td>
<td>长三角工厂</td>
<td>极片车间</td>
<td>辊压 1 线</td>
<td>RP-001</td>
</tr>
<tr>
<td>CellCo</td>
<td>长三角工厂</td>
<td>装配车间</td>
<td>卷绕 1 线</td>
<td>WND-001~008</td>
</tr>
<tr>
<td>CellCo</td>
<td>长三角工厂</td>
<td>化成车间</td>
<td>化成 1 区</td>
<td>F1-001~096（96 柜）</td>
</tr>
</tbody>
</table>

**关键约束**：派工单只能下发到 WorkCell 级，不能下发到 WorkCenter 级（下到 WorkCenter 让操作员自己选设备，等于没排产）；状态采集从 WorkCell 上报到 WorkCenter 再到 Area。这是 ch02 架构的基础。

### ISA-88 工艺路线树（Routing Tree）

电芯 7 段工艺的工艺路线树（树形 DAG，非纯线性）：

```
工单根
├─ 涂布（前置：无；产出：正极片卷/负极片卷）
│   └─ 替代：涂布外包（紧急情况，外购极片）
├─ 辊压（前置：涂布；产出：辊压极片）
├─ 分切（前置：辊压；产出：分切后极片，宽向切多卷）
├─ 卷绕（前置：分切 + 隔膜 + 极柱；产出：电芯裸芯）
├─ 注液（前置：卷绕；产出：注液后电芯；关键料：电解液）
│   └─ 替代：预充注液（不同电解液配方）
├─ 化成（前置：注液；产出：化成后电芯）
│   └─ 关键参数：充放电曲线集（按电芯型号不同）
└─ 分容（前置：化成；产出：分容后合格电芯，可发货）
```

**关键概念**：

- **前置关系（Predecessor）**：化成必须等注液完，分切可以与同批料的辊压并行（极片缓存）；前置不是 1:1 线性，是 DAG（有向无环图）。
- **替代工序（Alternative Operation）**：正常走自产涂布，紧急时走外购极片；MES 在派工时按工艺路线策略选默认路径，遇料缺/设备挂时按替代路径切。
- **关键料（Critical Material）**：注液的电解液、化成的化成曲线参数集；这些料的批次/参数变更触发全链追溯（ch06）。
- **关键参数（Critical Parameter）**：涂布厚度、辊压压力、化成截止电流；这些参数变更触发 SPC 重算（ch08）。

### BOM 的三种类型

电芯 BOM 不是平铺，是分层的（业界惯用 E/M/S 三型）：

- **E-BOM（Engineering BOM）**：研发给的电芯物料清单，按"结构组装"展开（电芯 = 正极 + 负极 + 隔膜 + 电解液 + 铝塑膜 + 极柱）。
- **M-BOM（Manufacturing BOM）**：在 E-BOM 上叠加"用量、损耗、替代料、投入工序"——314Ah 的正极粉料号 LFP-314、用量 0.85 kg/颗、损耗 1.5%、投入涂布工序、替代料 LFP-280（仅紧急替代，容量低 7%）。
- **S-BOM（Sales BOM）**：销售口径的 BOM（一个 Pack = 多颗电芯 + 结构件 + BMS），ERP 主。

MES 用的是 M-BOM。下面我们建模三件套的表结构。

## 正确姿势：三件套的表结构设计

### 工艺路线表 routing

```sql
CREATE TABLE routing (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,         -- 工艺路线编码 RT-CELL-314AH
    name VARCHAR(128) NOT NULL,
    product_sku VARCHAR(64) NOT NULL,  -- 关联产品 SKU 314AH-CELL
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL,       -- DRAFT/RELEASED/DEPRECATED
    UNIQUE (tenant_id, code, version)
);
CREATE TABLE routing_operation (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    routing_id BIGINT NOT NULL,
    seq INT NOT NULL,                  -- 工序顺序 10/20/30...
    operation_code VARCHAR(64) NOT NULL, -- 工序编码 COAT/ROLL/SLIT/WIND/FILL/FORM/TEST
    operation_name VARCHAR(64) NOT NULL,
    work_center_code VARCHAR(64),      -- 默认工作中心（可改派）
    std_cycle_time_ms BIGINT,          -- 标准节拍（毫秒）
    predecessor_seqs VARCHAR(128),     -- 前置工序 seq 列表 "10,20"
    alternative_op_id BIGINT,           -- 替代工序 ID
    critical_material_id BIGINT,        -- 关键料（追溯用）
    critical_param_set VARCHAR(64),    -- 关键参数集
    UNIQUE (tenant_id, routing_id, seq)
);
```

### BOM 表 bom + bom_item

```sql
CREATE TABLE bom (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,         -- BOM-CELL-314AH
    product_sku VARCHAR(64) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL,
    UNIQUE (tenant_id, code, version)
);
CREATE TABLE bom_item (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    bom_id BIGINT NOT NULL,
    material_sku VARCHAR(64) NOT NULL,
    quantity DECIMAL(14,4) NOT NULL,    -- 单颗用量
    unit VARCHAR(16),                  -- kg/g/m
    scrap_rate DECIMAL(5,4) DEFAULT 0, -- 损耗率
    operation_seq INT,                 -- 投入工序 seq
    alternative_sku VARCHAR(64),      -- 替代料号
    is_critical BOOLEAN DEFAULT FALSE, -- 关键料标记
    UNIQUE (tenant_id, bom_id, material_sku, operation_seq)
);
```

### 派工单表 dispatch_order

```sql
CREATE TABLE dispatch_order (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    erp_work_order_no VARCHAR(64) NOT NULL,  -- ERP 工单号（外键软引用）
    routing_id BIGINT NOT NULL,
    bom_id BIGINT NOT NULL,
    operation_seq INT NOT NULL,             -- 本派工单的工序
    work_cell_code VARCHAR(64) NOT NULL,     -- 下发到的设备
    shift_date DATE NOT NULL,
    shift_code VARCHAR(8) NOT NULL,         -- A/B/C 三班
    planned_qty INT NOT NULL,
    planned_start TIMESTAMP NOT NULL,
    planned_end TIMESTAMP NOT NULL,
    param_set_version INT,                  -- 参数集版本（防止改派时参数错配）
    status VARCHAR(16) NOT NULL,            -- RELEASED/STARTED/PAUSED/COMPLETED/CLOSED
    UNIQUE (tenant_id, erp_work_order_no, operation_seq)
);
```

**派工单级联展开算法**：ERP 工单下来，MES 自动展开成 7 张派工单（每段工艺一张），并按 DAG 前置关系排好计划起止时间。

```java
public List<DispatchOrder> expandWorkOrder(WorkOrder wo) {
    Routing r = routingRepo.findReleased(wo.getProductSku());
    Bom b = bomRepo.findReleased(wo.getProductSku());
    List<RoutingOperation> ops = r.getOperationsSorted();
    List<DispatchOrder> result = new ArrayList<>();
    for (RoutingOperation op : ops) {
        DispatchOrder d = new DispatchOrder();
        d.setErpWorkOrderNo(wo.getNo());
        d.setRoutingId(r.getId());
        d.setBomId(b.getId());
        d.setOperationSeq(op.getSeq());
        // 默认派到 work_center 的第一个空闲 work_cell，实际由 APS 调度
        d.setWorkCellCode(scheduleService.pickWorkCell(op, wo));
        d.setPlannedQty(wo.getQty());
        // 关键参数集：与料号绑定，改派时校验
        d.setParamSetVersion(paramService.getLatestVersion(op.getCriticalParamSet()));
        // 计划起止 = max(前置工序结束) + std_cycle_time × qty
        d.setPlannedStart(calcEarliestStart(op, result));
        d.setPlannedEnd(d.getPlannedStart().plusMillis(op.getStdCycleTimeMs() * wo.getQty()));
        result.add(d);
    }
    return dispatchRepo.saveAll(result);
}
```

## 数据说话：7 段工艺的真实节拍与 BOM 链

电芯厂 A 的 314Ah 高镍电芯（额定 314Ah，1C 容量规格下限 305Ah）的 7 段工艺真实节拍（实测 2025 年 11 月）：

<table>
<thead><tr>
<th>工序</th>
<th>设备</th>
<th>节拍</th>
<th>1000 颗工单工时</th>
<th>关键料</th>
<th>关键参数</th>
</tr></thead>
<tbody>
<tr>
<td>涂布</td>
<td>BC-001</td>
<td>60 m/min</td>
<td>1.5 h</td>
<td>正极粉 LFP-314</td>
<td>涂布厚度 142μm</td>
</tr>
<tr>
<td>辊压</td>
<td>RP-001</td>
<td>80 m/min</td>
<td>1.1 h</td>
<td>(无)</td>
<td>辊压厚度 134μm</td>
</tr>
<tr>
<td>分切</td>
<td>SL-001</td>
<td>200 m/min</td>
<td>0.4 h</td>
<td>(无)</td>
<td>分切宽度 135mm</td>
</tr>
<tr>
<td>卷绕</td>
<td>WND-001</td>
<td>8 PPM</td>
<td>2.1 h</td>
<td>隔膜 + 极柱</td>
<td>张力 20 N</td>
</tr>
<tr>
<td>注液</td>
<td>FL-001</td>
<td>30 PPM</td>
<td>0.6 h</td>
<td>电解液 EL-314</td>
<td>注液量 145g</td>
</tr>
<tr>
<td>化成</td>
<td>F1-001~096</td>
<td>96 柜 100 颗/柜</td>
<td>24 h</td>
<td>化成曲线 FORM-314-V3</td>
<td>截止电流 0.05C</td>
</tr>
<tr>
<td>分容</td>
<td>FC-001~096</td>
<td>96 柜 100 颗/柜</td>
<td>12 h</td>
<td>(无)</td>
<td>分容温度 25°C</td>
</tr>
</tbody>
</table>

**关键洞察**：化成和分容是瓶颈工序——单柜 100 颗，96 柜 9600 颗/天，1000 颗工单占柜 1/9.6 个工位，化成 24 小时不可压缩（电化学决定的）。APS 排产时必须先排化成（瓶颈资源），其他工序"贴"化成的开始/结束时间。这是 ch12 APS 看板的关键。

314Ah 电芯的 M-BOM 链（节选）：

<table>
<thead><tr>
<th>物料 SKU</th>
<th>名称</th>
<th>用量/颗</th>
<th>单位</th>
<th>损耗</th>
<th>投入工序</th>
<th>替代料</th>
<th>关键</th>
</tr></thead>
<tbody>
<tr>
<td>LFP-314</td>
<td>正极粉 314</td>
<td>0.85</td>
<td>kg</td>
<td>1.5%</td>
<td>涂布</td>
<td>LFP-280</td>
<td>TRUE</td>
</tr>
<tr>
<td>GRA-314</td>
<td>负极粉 314</td>
<td>0.55</td>
<td>kg</td>
<td>1.5%</td>
<td>涂布</td>
<td>GRA-280</td>
<td>FALSE</td>
</tr>
<tr>
<td>SEP-135</td>
<td>隔膜 135mm</td>
<td>12.0</td>
<td>m</td>
<td>0.5%</td>
<td>卷绕</td>
<td>-</td>
<td>TRUE</td>
</tr>
<tr>
<td>EL-314</td>
<td>电解液 314</td>
<td>145</td>
<td>g</td>
<td>0.3%</td>
<td>注液</td>
<td>EL-280</td>
<td>TRUE</td>
</tr>
<tr>
<td>ALP-314</td>
<td>铝塑膜</td>
<td>0.42</td>
<td>张</td>
<td>0.1%</td>
<td>卷绕</td>
<td>-</td>
<td>FALSE</td>
</tr>
<tr>
<td>TAB-314</td>
<td>极柱</td>
<td>2</td>
<td>件</td>
<td>0</td>
<td>卷绕</td>
<td>-</td>
<td>FALSE</td>
</tr>
</tbody>
</table>

**召回追溯的 SQL（ch06 详讲，这里先给味道）**：

```sql
-- 反向追溯：从成品 SN 找到所有料批次
WITH RECURSIVE trace AS (
    SELECT 1 AS level, '成品' AS node, sn AS id, parent_sn AS p
    FROM cell_sn WHERE sn = 'C314AH-20251109-001'
    UNION ALL
    SELECT t.level + 1, '工序产出', m.output_sn, m.input_sn
    FROM material_consumption m JOIN trace t ON m.input_sn = t.id
    WHERE t.level < 7
)
SELECT * FROM trace ORDER BY level;
-- 0.3s 圈定 36 颗受影响电芯（电芯厂 A 11 天 → 0.3 秒）
```

## 面试怎么答：工艺路线 vs BOM、ISA-95 设备层级

**问：MES 的工艺路线（Routing）和 ERP 的 BOM 有什么不同？为什么不能合并？**

答：① **关注点不同**——BOM 回答"用什么料"，工艺路线回答"经过哪些工序"；② **结构不同**——BOM 是树形展开（一棵树），工艺路线是 DAG（前置后置关系，可并行可替代）；③ **变更频率不同**——BOM 变更慢（产品研发定，季度级），工艺路线变更快（同一产品的工艺路线因换型、设备状况会按天换替代路径）；④ **关键属性不同**——工艺路线带"标准工时/工作中心/前置工序/替代工序"，BOM 不带。合并的代价：BOM 表字段膨胀，工艺路线变更触发 BOM 重发版，全 ERP 工单链重排。

**问：ISA-95 设备层级 Enterprise-Site-Area-WorkCenter-WorkCell 在 MES 里怎么落地？**

答：① **多租户的天然边界**：Enterprise = tenant_id；② **WorkCenter 是排产最小颗粒**：派工单下发到 WorkCell（不派到 WorkCell 等于没排），但 APS 排产到 WorkCenter 级（一个 WorkCenter 多个 WorkCell 按节拍轮转）；③ **Area 是看板/Andon 的组织单元**：Andon 看板按 Area 显示（极片车间/装配车间/化成车间三块大屏）；④ **状态聚合方向**：WorkCell → WorkCenter → Area，每一级聚合一次（WorkCell 秒级、WorkCenter 分钟级、Area 小时级）——这是 ch11 IoTDB 分级存储的依据。

**问：电芯 7 段工艺里哪段是瓶颈？为什么 MES 必须先排它？**

答：化成。原因：① 时间最长（24h，其他工序 1~2h）；② 电化学硬约束，不可压缩；③ 资源最贵（96 柜 × 100 颗 = 9600 颗/天，投资 1.2 亿）；④ 容量预测的Early Warning 信号源（ch10）。APS 排产必须先排化成，其他工序"贴"化成起止时间——这是 TOC（约束理论）的"瓶颈驱动"原则，也是为什么我们 ch12 APS 看板要先把化成柜建模为瓶颈资源池。

## 落地清单：业务建模落地动作

1. **梳理 ISA-95 五层设备层级**：Enterprise = 集团；Site = 工厂；Area = 工艺大段（极片/装配/化成）；WorkCenter = 工序级设备组；WorkCell = 具体设备。这一步是后续所有派工、追溯、看板的"坐标系"。
2. **建 Routing 主表**：每个产品一份 Routing，按工序 seq 排序（10/20/30...留间隔便于插工序），保留 version 字段。
3. **建 BOM 主表**：M-BOM 不 E-BOM，物料带"投入工序 seq""替代料""关键料标记"三字段。
4. **建 Dispatch Order 派工单表**：派工单字段必须包含 `param_set_version`，防止改派时参数错配（事故 1 的根因）。
5. **派工级联展开算法**：ERP 工单 → 按 Routing 自动展开 N 张派工单 → 按 DAG 前置关系排计划起止时间 → 按 WorkCell 节拍计算产能可行性 → 不满足则报"工时溢出"。
6. **改派兜底**：任何派工单 status != RELEASED 时改派，必须重新展开后续工序派工单 + 重算参数集版本 + 校验替代料库存——这是事故 2 的根因防线。
7. **关键料与关键参数双标记**：`bom_item.is_critical = true` 的料、`routing_operation.critical_param_set != null` 的参数，变更触发 ch06 追溯 + ch08 SPC 重算。
8. **黄金数据**：建 4 张派工单的"标准改派场景"测试数据（同型号改派/不同型号改派/料缺替代/化成柜占满），单元测试 + 黄金回归必须过，这是事故 1/2/3 不再发生的代码层保障。

业务建模是 MES 项目的"宪法"——后面所有章节（采集、追溯、OEE、SPC、Andon）都建立在这套设备层级 + 工艺路线 + BOM + 派工单的骨架上。下一章我们解剖这套骨架的物理架构：分层、双库、多租户。
