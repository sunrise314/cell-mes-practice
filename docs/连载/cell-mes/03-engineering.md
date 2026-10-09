---
title: ch03 工程化：Spring Boot 3 + MyBatis-Plus + Flyway 多租户项目骨架
slug: cell-mes-03-engineering
---

## 事故现场：上线 3 周做 7 次数据迁移，每次都漏字段

电芯厂 A 二期 MES 项目按 ch02 四层架构搭好之后，进入 mes-core 业务层工程化阶段。第一批 Flyway 迁移脚本 V1（建表）→ V7（加字段），三周内改了 7 次数据库结构。每次改完都漏一两个地方：

- V2 加 `dispatch_order.param_set_version` 字段，DTO 忘了加，前端拿不到，派工时还是按旧参数集跑——又触发了一次"化成截止电流错配"事故险情。
- V5 改 `material_consumption` 表加 `tenant_id` 索引，迁移脚本执行了但**索引名拼错**（`idx_tenant_id` 写成 `idx_tenantid`），生产环境慢查询暴增，但本地测试的 10 万行数据根本暴露不出来。
- V7 给 `oee_downtime` 表加 `code_level2`（二级停机编码），但 MyBatis-Plus 实体类的 `@TableField` 注解忘改，写入时这字段一直是 NULL——OEE 报表二级编码全空，业务方吐槽"和没加一样"。

这三次事故的共同根因：**多模块项目的工程化没纪律**——DTO/Entity/Migration 三处变更要同步、租户拦截器配置要测试、字段改名要走"添加新字段→双写→切读→删旧字段"四步而非一步替换。这一章把项目工程化的"必走流程"全列出来。

## 排查：MES 工程化的三个高频踩坑点

排查发现 80% 的工程化事故集中在三个地方：

1. **DTO/Entity/Migration 三层不同步**——加字段时只改了其中两层，第三层漏改。Flyway 迁移只在执行那一刻验证 SQL 能跑通，**不验证 MyBatis 映射是否对应**——本地测试小数据量暴露不出来，生产数据量上去才慢查询。
2. **租户拦截器漏网**——MyBatis-Plus 的 InnerInterceptor 拦截器对原生 SQL、自定义 XML SQL 的覆盖不完全，部分手写 SQL 漏掉 `WHERE tenant_id=?` 注入，**多租户数据互相污染**——A 工厂的工单列表里出现 B 工厂的工单。
3. **Java 21 虚拟线程踩坑**——Spring Boot 3.2+ 支持虚拟线程，但 JDBC 连接池（HikariCP）的 `synchronized` 在虚拟线程下会 pin platform 线程，导致连接池假死。OPC UA 长连接和 JDBC 共用一个线程池的配置，曾导致 OPC UA 重连时把数据库连接全占住。

下面我们按"模块拆分 → 数据迁移纪律 → 租户拦截器 → 虚拟线程配置"四步讲工程化骨架。

## 底层原理：MES 多模块项目骨架

### Maven 多模块分层

```
cell-mes-practice/
├── pom.xml                                # parent
├── cell-mes-common/                       # 共享 DTO/常量/异常
├── cell-mes-domain/                       # 领域模型 + Repository 接口
├── cell-mes-infra/                        # 基础设施：DB/Redis/Kafka/IoTDB 客户端
├── cell-mes-collector/                    # 采集层（独立进程）
├── cell-mes-core/                         # 业务层（独立进程）
│   ├── cell-mes-core-routing             # 工艺路线
│   ├── cell-mes-core-dispatch             # 派工单
│   ├── cell-mes-core-trace                # 批次追溯
│   ├── cell-mes-core-oee                  # OEE
│   ├── cell-mes-core-spc                  # SPC
│   └── cell-mes-core-andon                # Andon
├── cell-mes-web-bff/                      # BFF
└── docs/                                  # 文档+连载源稿
```

**为什么按业务领域而不是按技术分层（Controller/Service/Dao）**：MES 业务逻辑复杂，跨模块调用多（追溯调派工、SPC 调追溯、Andon 调 OEE）；按业务领域分模块后，模块内自包含 Controller+Service+Dao，模块间靠接口暴露，符合 DDD 限界上下文原则。

### Flyway 迁移纪律

每次加字段走"四步迁移法"，禁止单步替换：

<table>
<thead><tr>
<th>步骤</th>
<th>迁移脚本</th>
<th>行为</th>
<th>兼容期</th>
</tr></thead>
<tbody>
<tr>
<td>1</td>
<td>V8.1: ALTER ADD new_col</td>
<td>加字段，DEFAULT NULL</td>
<td>1 周双写</td>
</tr>
<tr>
<td>2</td>
<td>V8.2: 应用层双写（旧+新）</td>
<td>代码改 `setOld + setNew`</td>
<td>-</td>
</tr>
<tr>
<td>3</td>
<td>V8.3: 应用层切读 + 数据回填</td>
<td>UPDATE t SET new=old WHERE new IS NULL</td>
<td>切读验证</td>
</tr>
<tr>
<td>4</td>
<td>V8.4: ALTER DROP old_col</td>
<td>删旧字段</td>
<td>-</td>
</tr>
</tbody>
</table>

**强制纪律**：禁止 "ALTER RENAME COLUMN"（一步替换）——应用进程在迁移过程中可能还在用旧字段，会报 column not found。

### 多租户拦截器完整覆盖

```java
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 多租户拦截器（必须最先，否则权限/分页拦截器会拼到错位置）
        TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor();
        tenant.setTenantLineHandler(new TenantLineHandler() {
            @Override public Expression getTenantId() {
                Long t = TenantContext.get();
                if (t == null) throw new IllegalStateException("tenant_id required");
                return new LongValue(t);
            }
            @Override public String getTenantIdColumn() { return "tenant_id"; }
            @Override public boolean ignoreTable(String tableName) {
                // 全局表（如 tenant/sys_dict）不注入
                return IGNORE_TABLES.contains(tableName.toLowerCase());
            }
        });
        interceptor.addInnerInterceptor(tenant);
        // 2. 权限拦截器
        interceptor.addInnerInterceptor(new DataPermissionInterceptor());
        // 3. 分页拦截器（必须最后）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
```

**手写 SQL 必须验证**：所有 `@Select` / XML SQL 在 CI 上跑 `TenantInterceptorCoverageTest`，断言每条 SQL 执行后都带 `tenant_id` 条件——这是事故 2 的根因防线。

### Java 21 虚拟线程配置

Spring Boot 3.2+ 开虚拟线程：

```yaml
spring:
  threads:
    virtual:
      enabled: true
  datasource:
    hikari:
      maximum-pool-size: 50
      # 关键：连接借出使用 FastPath，避免 synchronized pin platform 线程
      # 虚拟线程下 HikariCP 的 synchronized 在 pool entry 上会 pin
      # 解决方案：HikariCP 5.1+ 已优化，配置 minimumIdle=maximumPoolSize 消除借出抖动
      minimum-idle: 50
```

**为什么 OPC UA 采集层不开虚拟线程**：Milo OPC UA SDK 的 `CompleteFuture` 内部用 `synchronized`，开虚拟线程会被 pin 反而性能差。采集层保留 platform 线程，业务层开虚拟线程——这是 ch04 的话题。

## 正确姿势：项目骨架的工程化清单

### pom.xml 父项目

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.cellmes</groupId>
  <artifactId>cell-mes-parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.11</version>
  </parent>
  <properties>
    <java.version>21</java.version>
    <mybatis-plus.version>3.5.7</mybatis-plus.version>
    <iotdb.version>1.3.4</iotdb.version>
    <kafka.version>3.7.0</kafka.version>
  </properties>
  <modules>
    <module>cell-mes-common</module>
    <module>cell-mes-domain</module>
    <module>cell-mes-infra</module>
    <module>cell-mes-collector</module>
    <module>cell-mes-core</module>
    <module>cell-mes-web-bff</module>
  </modules>
</project>
```

### Flyway 迁移目录结构

```
cell-mes-core/src/main/resources/db/migration/
├── V1.0.0__init_schema.sql                  # 基础表
├── V1.0.1__seed_routing.sql                 # 工艺路线种子
├── V1.0.2__seed_bom.sql                     # BOM 种子
├── V1.0.3__add_dispatch_param_version.sql   # 加字段（四步第 1 步）
├── V1.0.4__backfill_dispatch_param_version.sql # 回填（第 3 步）
├── V1.0.5__add_tenant_index.sql             # 加索引
├── V1.0.6__add_oee_downtime_code_level2.sql # 加二级编码字段
└── V1.0.7__drop_legacy_oee_code.sql         # 删旧字段（四步第 4 步）
```

**命名规则**：`V<大版本>.<小版本>.<补丁>__<动词>_<对象>.sql`，禁止 `V1__xxx.sql` 这种无小版本号写法（合并冲突难处理）。

### Entity 与 DTO 分离

```java
// cell-mes-domain: 领域模型（DB 映射）
@TableName("dispatch_order")
public class DispatchOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;          // 必带，租户拦截器用
    private String erpWorkOrderNo;
    private Long routingId;
    private Long bomId;
    private Integer operationSeq;
    private String workCellCode;
    private LocalDate shiftDate;
    private String shiftCode;
    private Integer plannedQty;
    private Integer actualQty;       // 实际产出（ch04 实时更新）
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private LocalDateTime actualStart; // ch04 推送设备启动事件时回写
    private LocalDateTime actualEnd;
    private Integer paramSetVersion; // 改派校验用
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// cell-mes-common: 对外 DTO（不暴露 tenantId）
public record DispatchOrderDTO(
    Long id,
    String erpWorkOrderNo,
    String operationCode,
    String workCellCode,
    Integer plannedQty,
    Integer actualQty,
    String status,
    String shiftLabel
) {}

// 严禁：DispatchOrder 实体直接返回前端（泄漏 tenantId）
```

**纪律**：Controller 永远返回 DTO，不返回 Entity——避免字段误泄漏（如 tenant_id、内部审计字段）。Mapper 用 MapStruct 显式声明映射，禁止反射拷贝。

### 异步任务显式传租户

```java
public class TraceService {
    @Async("traceExecutor")
    public void buildTraceIndexAsync(Long cellSn) {
        Long tid = TenantContext.get();  // 关键：从主线程取
        CompletableFuture.runAsync(() -> {
            try (TenantContext.Scope scope = TenantContext.with(tid)) {
                // 异步线程内显式设置租户
                doBuildTrace(cellSn);
            }
        }, traceExecutor);
    }
}

// TenantContext.Scope 是 AutoCloseable，try-with-resources 自动清理
public class TenantContext {
    private static final ThreadLocal<Long> CTX = new ThreadLocal<>();
    public static Long get() { return CTX.get(); }
    public static Scope with(Long tid) {
        Long old = CTX.get();
        CTX.set(tid);
        return () -> { if (old == null) CTX.remove(); else CTX.set(old); };
    }
    public interface Scope extends AutoCloseable { default void close() {} }
}
```

**踩坑警告**：Java 21 虚拟线程下 ThreadLocal 仍可用，但 `InheritableThreadLocal` 在虚拟线程下行为不一致（虚拟线程可能复用 carrier），**禁止用 InheritableThreadLocal**，必须显式传递。

## 数据说话：多模块骨架的工程化指标

电芯厂 A 二期重构后的工程化指标：

<table>
<thead><tr>
<th>指标</th>
<th>一期（单模块）</th>
<th>二期（多模块+纪律）</th>
<th>改善</th>
</tr></thead>
<tbody>
<tr>
<td>Flyway 迁移脚本数</td>
<td>23 个（混在一起）</td>
<td>87 个（按版本分）</td>
<td>-</td>
</tr>
<tr>
<td>迁移冲突次数</td>
<td>7 次/月</td>
<td>0 次/月</td>
<td>-</td>
</tr>
<tr>
<td>多租户拦截器漏 SQL</td>
<td>4 处</td>
<td>0 处（CI 强校验）</td>
<td>-</td>
</tr>
<tr>
<td>字段同步漏改</td>
<td>3 次/月</td>
<td>0 次/月</td>
<td>-</td>
</tr>
<tr>
<td>单元测试覆盖率</td>
<td>18%</td>
<td>62%</td>
<td>3.4x</td>
</tr>
<tr>
<td>编译时间</td>
<td>12s</td>
<td>28s（多模块）</td>
<td>-2.3x（接受）</td>
</tr>
<tr>
<td>部署包大小</td>
<td>92 MB（fat jar）</td>
<td>38 MB（mes-core）+ 26 MB（mes-collector）</td>
<td>-</td>
</tr>
<tr>
<td>虚拟线程下 HikariCP pin 次数</td>
<td>4 次/天</td>
<td>0 次/天</td>
<td>-</td>
</tr>
</tbody>
</table>

**关键数字：单元测试覆盖率从 18% 到 62%**——这是工程化纪律最大的收益，事故数从 7 次/月降到 0 次/月。

## 面试怎么答：MES 工程化三个高频问题

**问：MyBatis-Plus 多租户拦截器为什么有 SQL 漏注入？**

答：① **手写 XML SQL不在拦截范围**——TenantLineInnerInterceptor 默认只拦截 `BaseMapper` 方法，自定义 `@Select` / XML SQL 需要显式调用 `InterceptorIgnoreHelper`；② **复杂 SQL 的子查询**——某些嵌套 SQL 的 inner table 拦截器解析失败漏注入；③ **DDL 语句**——`CREATE TABLE` / `ALTER` 不拦截但插入数据时要带 tenant_id。**强制纪律**：CI 跑 `TenantInterceptorCoverageTest`，对每条 SQL 断言执行计划带 `tenant_id` 过滤条件。

**问：Java 21 虚拟线程下 HikariCP 为什么会 pin？**

答：HikariCP 在 pool entry 借出/归还连接时用了 `synchronized`，Java 21 虚拟线程在 `synchronized` 块内会被 pin 到 platform 线程（carrier thread），导致虚拟线程"退化"为 platform 线程，失去轻量级优势。**解决方案**：① HikariCP 5.1+ 已针对虚拟线程优化（用 ReentrantLock 替代 synchronized）；② 配置 `minimumIdle=maximumPoolSize` 消除借出抖动；③ OPC UA 长连接和 JDBC 不共用线程池。JDK 24+ 将支持 `synchronized` 不 pin，届时可彻底解决。

**问：MES 多模块项目按业务领域分还是按技术分层？**

答：按业务领域分（DDD 限界上下文）。理由：① MES 业务跨模块调用多（追溯调派工、SPC 调追溯），按业务分模块后模块内自包含，跨模块走接口暴露，限界清晰；② 团队按业务组分工（追溯组、SPC 组），按业务模块对齐团队结构；③ 单模块可以独立打 jar 部署（采集层/业务层分进程），按技术分层无法独立部署。代价是模块多编译慢（28s vs 12s），可接受。

## 落地清单：工程化骨架动作

1. **多模块拆分**：parent + common + domain + infra + collector + core + web-bff；core 内部再按业务子模块（routing/dispatch/trace/oee/spc/andon）。
2. **Java 21 + Spring Boot 3.2.11**：开虚拟线程 + HikariCP 5.1+ + minimumIdle=maximumPoolSize。
3. **MyBatis-Plus 3.5.7+**：TenantLineInnerInterceptor 必须最先添加（拦截器顺序敏感），最后加 PaginationInnerInterceptor。
4. **Flyway 命名 `V<大>.<小>.<补丁>__<动词>_<对象>.sql`**，禁止无小版本号；改字段走四步迁移法（加→双写→切读回填→删），禁止单步 RENAME。
5. **Entity / DTO 严格分离**：Controller 返回 DTO，MapStruct 显式映射，禁止反射拷贝；Entity 必带 `tenantId`。
6. **TenantContext 用 ThreadLocal，禁用 InheritableThreadLocal**；异步任务显式 `try (TenantContext.Scope scope = TenantContext.with(tid))`。
7. **CI 强制 TenantInterceptorCoverageTest**：每条 SQL 执行计划断言带 `tenant_id` 条件，跑 EXPLAIN 解析。
8. **黄金回归**：① 迁移脚本回滚测试（V8.4 删字段后能用 V8.3 数据回滚）；② 多租户测试（A 租户看不到 B 租户的工单）；③ 虚拟线程下 HikariCP pin 计数（0 次/天）。

工程化是项目的"骨架肌肉"，下一章我们深入 L2 采集层，看 OPC UA、Modbus、SECS/GEM 三协议怎么适配。
