---
title: ch02 架构解剖：四层分层 + 时序/关系双库 + 多租户隔离
slug: cell-mes-02-architecture
---

## 事故现场：一套"看起来很美"的 MES 上线后崩了三回

电芯厂 A 一期 MES 项目的初版架构是这么设计的：一套 Spring Boot 应用 + MySQL + Kafka + Vue3 前端，看起来技术栈主流、组件齐全。上线 30 天内崩了三回：

**崩 1：把秒级点位写 MySQL，3 个月锁表**。涂布+辊压+化成 96 柜设备，OPC UA 推 5000 个点位/秒，全往 MySQL 一张 `device_point_log` 表写。3 个月表行数 4.2 亿，索引膨胀到 18 GB，**任何按设备+时间段查询都超过 30 秒**。化成柜实时看板加载要 1 分钟一刷。

**崩 2：采集进程和业务进程同进程，OPC UA 重连打挂 MES**。采集模块和派工单/追溯/SPC 业务逻辑全在一个 Spring Boot 进程里跑。某次化成柜 OPC UA 服务端故障，采集模块启动指数退避重连，重连风暴下线程池被打满，**整个 MES 进程 OOM**，业务系统全停 1 小时。期间正好客户来现场参观，看到的是黑屏。

**崩 3：单租户设计，二期接 3 个分厂全部推倒重来**。一期只管长三角工厂，二期要接西北工厂、华南工厂。代码里大量"长三角工厂"的硬编码（SQL `WHERE site_id = 1`），二期要么改代码加 if 分支、要么改库加 site_id 列。算下来工作量比一期还大，老板脸色铁青。

这三起事故的根因是**架构没分层**——采集与业务耦合、时序与关系混存、租户没隔离。MES 不是 ERP，它有工业实时性、海量时序、多工厂扩展三大硬约束，必须在架构层就划清。

## 排查：MES 的三个架构级硬约束

我把 MES 与传统 Web/ERP 的架构差异列出来：

<table>
<thead><tr>
<th>维度</th>
<th>传统 Web/ERP</th>
<th>MES 工业硬约束</th>
</tr></thead>
<tbody>
<tr>
<td>**数据写入频率**</td>
<td>用户输入，<10 TPS</td>
<td>设备推点，1000~50000 TPS</td>
</tr>
<tr>
<td>**单条价值密度**</td>
<td>高（订单/财务一条值钱）</td>
<td>低（一颗点位不值钱，海量才有价值）</td>
</tr>
<tr>
<td>**可用性要求**</td>
<td>8x5 + 99.5%</td>
<td>7x24 + 99.95%（停产=亏钱）</td>
</tr>
<tr>
<td>**多租户规模**</td>
<td>1 公司/客户</td>
<td>多工厂，多车间，多产线</td>
</tr>
<tr>
<td>**延迟敏感**</td>
<td>用户操作 1s 内可接受</td>
<td>设备控制 < 100ms，看板 < 1s</td>
</tr>
<tr>
<td>**数据生命周期**</td>
<td>长期保留</td>
<td>时序数据 1~3 个月在线，3~12 月归档</td>
</tr>
</tbody>
</table>

三个硬约束：

1. **海量时序写入**——5000 TPS × 86400s = 4.3 亿条/天，传统关系库吃不下；必须有专用时序库（IoTDB/InfluxDB/TDengine）。
2. **采集与业务隔离**——OPC UA 长连接断线重连风暴、Modbus 设备无响应、SECS 设备异常，这些"工业级故障"不能传到业务进程；必须进程隔离。
3. **多租户隔离**——按工厂租户、按车间 schema 隔离；不能靠 if 分支兜底。

## 底层原理：四层架构 + 双库 + 多租户

### 四层架构

我把 MES 切成四层，每层独立进程、独立部署、独立扩展：

```
┌──────────────────────────────────────────────────────────┐
│ L4  看板层 (Vue3 + Nginx)                                 │
│   Andon 大屏 / OEE 看板 / 班次报表 / 排产看板              │
└──────────────────────────────────────────────────────────┘
                  ↑ HTTP/WS（BFF 聚合）
┌──────────────────────────────────────────────────────────┐
│ L3  业务层 (Spring Boot MES-Core)                         │
│   派工 / 追溯 / SPC / OEE / Andon / 排产                  │
│   - 关系库 PostgreSQL (工单/BOM/批次/控制图)              │
│   - Redis (Andon 状态机/限流/锁)                          │
└──────────────────────────────────────────────────────────┘
                  ↑ 业务事件订阅 (Kafka - cellmes.events)
┌──────────────────────────────────────────────────────────┐
│ L2  采集层 (Spring Boot MES-Collector)                    │
│   OPC UA 长连接 / Modbus 轮询 / SECS/GEM 会话             │
│   - 时序库 IoTDB (秒级点位)                               │
│   - 边缘规则引擎 (Threshold / 状态机)                     │
└──────────────────────────────────────────────────────────┘
                  ↑ 工业协议
┌──────────────────────────────────────────────────────────┐
│ L1  设备层 (PLC / 涂布机 / 化成柜 / 卷绕机)                │
└──────────────────────────────────────────────────────────┘
```

**L4 看板层**：Vue3 + Vite + Element Plus + ECharts，Nginx 静态托管，BFF 聚合接口（按看板一次性聚合 L3 业务数据 + L2 时序聚合数据）。**不直连 L3 业务库**——所有数据走 BFF REST，避免看板 SQL 把业务库拖死。

**L3 业务层**：Spring Boot 3 + MyBatis-Plus，承载 ch01 建模的三件套（Routing/BOM/Dispatch）+ 派工/追溯/SPC/OEE/Andon 全部业务逻辑。用 PostgreSQL 存工单/BOM/批次/控制图主键库（关系数据，<10 TPS）。Redis 存 Andon 状态机 + 派工单草稿 + 限流 token bucket。

**L2 采集层**：独立 Spring Boot 进程，专门做协议适配。OPC UA 长连接（Milo SDK）+ Modbus 轮询（j2mod）+ SECS/GEM（HSMS 半双工）。点位写入 IoTDB 时序库，**不写 PostgreSQL**。状态变更（设备状态机变化、点位越阈值）通过 Kafka 推 L3。

**L1 设备层**：物理资产，不归 MES 管，但 MES 必须懂它们的协议——这是 ch04 的主题。

### 双库：时序库 IoTDB + 关系库 PostgreSQL

为什么必须双库？看一组数字：

<table>
<thead><tr>
<th>数据</th>
<th>频率</th>
<th>单条价值</th>
<th>查询模式</th>
<th>库</th>
</tr></thead>
<tbody>
<tr>
<td>设备秒级点位</td>
<td>5000/s</td>
<td>低</td>
<td>时间窗口扫描</td>
<td>IoTDB</td>
</tr>
<tr>
<td>设备分钟级 KPI</td>
<td>96/min</td>
<td>中</td>
<td>设备+时间段</td>
<td>IoTDB</td>
</tr>
<tr>
<td>工单/派工单</td>
<td>100/day</td>
<td>高</td>
<td>主键查/列表</td>
<td>PostgreSQL</td>
</tr>
<tr>
<td>批次/追溯</td>
<td>10万/day</td>
<td>高</td>
<td>SN 反查（递归）</td>
<td>PostgreSQL</td>
</tr>
<tr>
<td>SPC 控制图原始值</td>
<td>10万/day</td>
<td>中</td>
<td>设备+料号+批次</td>
<td>PostgreSQL（控制图只存聚合值+样本，不存全量）</td>
</tr>
<tr>
<td>Andon 事件</td>
<td>50/day</td>
<td>高</td>
<td>状态机</td>
<td>PostgreSQL + Redis</td>
</tr>
<tr>
<td>用户/权限</td>
<td><1000</td>
<td>高</td>
<td>主键查</td>
<td>PostgreSQL</td>
</tr>
</tbody>
</table>

**关键约束**：时序数据进时序库，**关系数据绝不进时序库**——把工单、批次写 IoTDB 等于在时序库建一张大宽表，工单关联查询会全表扫描，时序库的"对齐时间线"优势被抵消。

**反向也成立**：把秒级点位写 PostgreSQL（电芯厂 A 一期崩 1 的根因），3 个月表锁死。

### 双库间的"事件桥"

L2 采集到设备点位/事件后，怎么和 L3 业务打通？三道桥：

1. **状态变更事件**（设备状态机变化 RUN→STOP）：L2 → Kafka topic `cellmes.events.device-status` → L3 订阅，更新 `dispatch_order.actual_status`、`oee_downtime` 表。
2. **阈值越界事件**（如化成电流 > 控制线）：L2 边缘规则引擎判定 → Kafka topic `cellmes.events.spc-violation` → L3 SPC 服务订阅，触发控制图异常。
3. **批产出事件**（每完成一颗电芯）：L2 收 PLC 信号 → Kafka topic `cellmes.events.cell-produced` → L3 追溯服务订阅，更新 `material_consumption`、`cell_sn` 表。

**关键设计**：L2 → L3 单向，**L3 不直接读 L2**——业务进程永远不直连时序库做实时查询（避免时序库被业务拖累），所有需要"实时设备状态"的业务场景都靠 Kafka 推送 + Redis 缓存。这是工业实时系统设计的重要原则，叫"读少写多走推不拉"。

### 多租户隔离

电芯厂 A 的多租户模型：

- **租户层级**：Enterprise（集团）→ 多个 Site（工厂）租户
- **隔离策略**：行级（Row-Level Multi-Tenancy）+ 敏感数据 schema 隔离
- **tenant_id 字段**：每张业务表必带 `tenant_id BIGINT NOT NULL`，MyBatis-Plus 拦截器自动注入 `WHERE tenant_id = ?`

```java
@Component
public class TenantInterceptor implements InnerInterceptor {
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms,
                            Object parameter, RowBounds rb, ResultHandler rh,
                            BoundSql sql) {
        String sqls = sql.getSql();
        Long tid = TenantContext.get();
        if (tid == null) throw new IllegalStateException("tenant_id required");
        // 注入 WHERE tenant_id = ?
        String injected = sqls.replaceAll("(?i)\\bfrom\\b(\\s+\\w+)",
            "$0 WHERE tenant_id = " + tid);
        // 实际用 JsqlParser 重构更稳，这里给意思
        reflectSet(sql, injected);
    }
}
```

**敏感数据 schema 隔离**：批次追溯的 `cell_sn` / `material_consumption` 表数据量巨大（千万级/天），放独立 schema `cellmes_trace_<tenant>`，物理上与主业务表分库（同一 PostgreSQL 集群不同 schema，跨 schema 查询仍可 JOIN，但物理表空间隔离）。

**为什么不用独立的物理库而用 schema 隔离**：单租户（一个工厂）的批次量不到必须分库的程度（10 亿行内 PostgreSQL 单库扛得住），schema 隔离足够；如果未来某工厂规模突破（如宁德溧阳 100GWh），再做物理库迁移。

### 部署拓扑

```
物理机 1：mes-collector (Spring Boot) + IoTDB + Kafka
物理机 2：mes-core (Spring Boot) + PostgreSQL + Redis
物理机 3：mes-web (Nginx + Vue3 dist)
物理机 4（备份）：DB backup / 日志归档
```

**为什么采集和业务物理隔离**：采集进程吃 IO + 网络长连接（OPC UA），业务进程吃 CPU + 数据库连接池，物理资源错位，混部署会互相影响（采集重连风暴会拖垮业务的数据库连接池）。这是电芯厂 A 一期崩 2 的根因防线。

## 正确姿势：架构关键决策

### 决策 1：时序库选 IoTDB 而不是 InfluxDB

<table>
<thead><tr>
<th>维度</th>
<th>IoTDB</th>
<th>InfluxDB</th>
<th>TDengine</th>
</tr></thead>
<tbody>
<tr>
<td>写入吞吐</td>
<td>100万 TPS/单机</td>
<td>70万</td>
<td>100万</td>
</tr>
<tr>
<td>工业协议 native</td>
<td>OPC UA/Modbus 适配</td>
<td>无</td>
<td>有</td>
</tr>
<tr>
<td>SQL 方言</td>
<td>类 SQL（IoTDB-SQL）</td>
<td>Flux/InfluxQL</td>
<td>SQL</td>
</tr>
<tr>
<td>商业授权</td>
<td>Apache 2.0</td>
<td>商业版收费</td>
<td>开源</td>
</tr>
<tr>
<td>国内社区</td>
<td>国产（清华系）</td>
<td>国外</td>
<td>国产</td>
</tr>
</tbody>
</table>

选 IoTDB 的关键理由：① 与 gansu-ems 同栈，团队复用；② Apache 2.0 全开源无商业风险；③ 国产工业时序库，对 OPC UA / Modbus 协议有 native 适配层（IoTDB 的 IotSession 支持 OPC UA 数据源直连，省一层适配代码）。

### 决策 2：消息中间件选 Kafka 而不是 RabbitMQ

工业事件量大（设备状态变更 5万/天、点位越界 1万/天、批产出 5万/天），且需要回放（事故复盘要重放某天事件流），RabbitMQ 是工作队列模型，不利于"事件流回放"。Kafka 的"分区+持久化+回放"特性正好适配。

### 决策 3：采集层用 Java 而不是 Python

Python 有 opcua 库，但团队是 Java 栈，且 OPC UA Milo SDK（Java）的认证、长连接管理比 Python opcua 库成熟很多。Modbus j2mod、SECS/GEM 开源库（如 OpenSECS）也成熟。Python 适合边缘脚本（如视觉检测），不适合长连接采集层。

### 决策 4：看板 BFF 而不是直连数据库

电芯厂 A 一期 V0 版本是看板直连 PostgreSQL + IoTDB，看板 SQL 复杂，IoTDB 被复杂查询打挂。改造为 BFF：看板只调 BFF REST 接口，BFF 内部用 Promise.all 并行查 PG + IoTDB，做结果聚合。代价是 BFF 多一层进程，收益是 IoTDB 不再被看板拖累。

## 数据说话：四层架构上线后 30 天的实测数据

电芯厂 A 二期（重构后）上线 30 天的关键指标对比：

<table>
<thead><tr>
<th>指标</th>
<th>一期（崩 3 次）</th>
<th>二期（四层架构）</th>
<th>改善</th>
</tr></thead>
<tbody>
<tr>
<td>IoTDB 写入 TPS</td>
<td>-</td>
<td>4872 / 5000 配置</td>
<td>接近线速</td>
</tr>
<tr>
<td>IoTDB 查询 99 线</td>
<td>(MySQL 30s+)</td>
<td>180 ms</td>
<td>100x</td>
</tr>
<tr>
<td>MES 业务可用率</td>
<td>99.5% (崩 3 次)</td>
<td>99.96%</td>
<td>-</td>
</tr>
<tr>
<td>采集进程 OOM 次数</td>
<td>4 次</td>
<td>0</td>
<td>-</td>
</tr>
<tr>
<td>多工厂接入耗时</td>
<td>推倒重来 3 月</td>
<td>加 tenant_id 1 周</td>
<td>12x</td>
</tr>
<tr>
<td>Kafka 端到端延迟</td>
<td>-</td>
<td>25 ms P99</td>
<td>-</td>
</tr>
<tr>
<td>看板加载时间</td>
<td>60s</td>
<td>1.2s</td>
<td>50x</td>
</tr>
<tr>
<td>单工厂数据库规模</td>
<td>4.2 亿行/3 月</td>
<td>4.1 亿/3 月时序 + 800 万关系</td>
<td>时序归 IoTDB，关系库可控</td>
</tr>
</tbody>
</table>

**关键数字：MySQL 30s 查询 → IoTDB 180ms = 100 倍**。这是时序库与关系库的本质差异。崩溃事故归零。

## 面试怎么答：MES 架构的三个关键问题

**问：MES 为什么必须用双库（时序+关系），不能合并？**

答：① **写入模式不同**——时序是海量小追加（5000 TPS），关系是事务性小批量（<10 TPS），合并后任何一种模式都会拖累另一种；② **查询模式不同**——时序按"设备+时间段"扫描，关系按"主键/外键"JOIN，时序库的列存对 JOIN 弱，关系库的行存对时序扫描弱；③ **数据生命周期不同**——时序高频归档（30 天/3 月），关系长期保留，合并后归档策略无法差异化；④ **故障隔离**——时序库被看板拖垮不影响关系业务，反之亦然。

**问：采集层和业务层为什么要进程隔离？**

答：工业协议（OPC UA/Modbus/SECS）有"长连接 + 重连风暴"特性，断网后所有设备同时重连，线程池打满，如果采集和业务同进程，业务进程直接 OOM。进程隔离后，采集进程崩了业务还能继续（设备实时状态有 Redis 缓存兜底 60 秒，业务层用最近缓存做判定）。这是工业实时系统"故障域隔离"的基本要求。

**问：多租户为什么用行级隔离而不是 schema 隔离？**

答：行级隔离（tenant_id 字段）的优势：① 表结构统一，跨租户统计 SQL 简单；② 单租户规模可控时性能 OK（<1000 万行/月）；③ 运维简单（一份 schema）。schema 隔离的优势：① 物理隔离强；② 单租户慢不影响其他租户；③ 适合超大规模租户（每租户 > 1 亿行）。MES 单租户（一个工厂）通常在 1000 万行/月以下，行级够用，schema 隔离是过度设计。但**敏感数据可独立 schema**——批次追溯表数据量大，做物理表空间隔离。

## 落地清单：架构落地动作

1. **物理部署拓扑**：采集进程（mes-collector）+ 业务进程（mes-core）+ 看板进程（mes-web）**分物理机**部署，不混部。
2. **时序库 IoTDB**：单实例 4 核 8G 起，按 5000 TPS 配置 `chunk_size=64KB`、`enable_wal=true`、复制 1 副。
3. **关系库 PostgreSQL**：单实例 8 核 16G，主要业务表 + 批次追溯 schema 分离；定期 VACUUM ANALYZE（每周日）。
4. **消息中间件 Kafka**：3 副本起，topic 按事件类型分：`cellmes.events.device-status` / `cellmes.events.spc-violation` / `cellmes.events.cell-produced`，分区数=租户数×4。
5. **缓存 Redis**：主从 + Sentinel，存 Andon 状态机、派工草稿、限流 token bucket。
6. **多租户拦截器**：MyBatis-Plus InnerInterceptor 自动注入 `WHERE tenant_id=?`，TenantContext 走 ThreadLocal（HTTP 入口过滤器设置，异步任务显式传）。
7. **BFF 聚合**：看板接口在 BFF 内 Promise.all 并行查 PG + IoTDB，超时 200ms 各自降级返回缓存。
8. **黄金回归**：① 时序写入基准（5000 TPS 持续 1h）；② Kafka 端到端延迟 P99 < 50ms；③ 多租户拦截器全 SQL 覆盖（用 Mybatis-Plus Plugin 测试工具）。

四层架构是 MES 的"骨架"，下一章我们拆开 mes-core 这个进程，看 Spring Boot 3 + MyBatis-Plus + Flyway 的工程化骨架怎么搭。
