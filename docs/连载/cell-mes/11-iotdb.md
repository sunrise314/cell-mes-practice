---
title: ch11 IoTDB 分级存储：秒级/分钟级/小时级归档 + 时序生命周期
slug: cell-mes-11-iotdb
---

## 事故现场：PostgreSQL 存秒级点位，14 天后查询慢到 40 秒

事故复盘要把数据存储的工程坑还原。ch04-ch10 的所有时序功能（OEE、SPC、化成预测）都依赖时序数据，最早一期 MES 把秒级点位直接存 PostgreSQL：

```sql
CREATE TABLE device_metric (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    metric_name VARCHAR(32) NOT NULL,
    value DECIMAL(12,4) NOT NULL,
    timestamp TIMESTAMP NOT NULL
);
CREATE INDEX idx_dm_device_time ON device_metric(device_id, timestamp);
```

数据量测算：96 台化成柜 × 30 点位 × 1 秒频率 = 2880 行/秒 = **2.49 亿行/天**。14 天后表 35 亿行，查询慢到无法用——事故 1 的复盘过程中，老李查"化成柜 F1-012 当周电压曲线"用 `SELECT * FROM device_metric WHERE device_id=12 AND timestamp BETWEEN ... AND ...`，**返回 604800 行耗时 40 秒**，老李说"这速度我都打瞌睡了"。

更糟的是**磁盘成本**：PostgreSQL 行存每行约 80 字节，35 亿行 = 280 GB，单表 280 GB 让备份恢复、迁移都成了噩梦。化成车间主任说"我只想看一柜电芯的化成曲线，凭什么要扫 280 GB"。

工程上的三个具体问题：

**问题 1：写入慢**。96 台化成柜每秒 2880 行 INSERT，PostgreSQL 单机写入极限约 5000 行/秒，**化成柜一秒占 58% 写入能力**，其他设备（涂布、辊压、卷绕）写入排队，数据延迟 5-10 秒——SPC 控制图用延迟数据，**异常发生 5 秒后才报警**。

**问题 2：查询慢**。PostgreSQL 的 B-tree 索引在 35 亿行表上做范围查询性能差，单设备 14 天范围 604800 行查 40 秒，**SPC 控制图加载慢，工程师不愿意看**。

**问题 3：存储成本高**。280 GB 单表，月度 600 GB，年化 7.2 TB，备份恢复成本爆炸。

## 排查：为什么 PostgreSQL 不适合存时序数据

PostgreSQL 是 OLTP 关系数据库，设计目标是**事务一致性 + 行级随机读写**，时序数据的特征与之完全相反：

<table>
<thead><tr>
<th>维度</th>
<th>PostgreSQL 设计</th>
<th>时序数据特征</th>
<th>匹配度</th>
</tr></thead>
<tbody>
<tr>
<td>写入模式</td>
<td>随机 INSERT/UPDATE</td>
<td>顺序追加，几乎不 UPDATE</td>
<td>不匹配</td>
</tr>
<tr>
<td>查询模式</td>
<td>行级点查 + 范围查询</td>
<td>时间范围扫描 + 聚合</td>
<td>不匹配</td>
</tr>
<tr>
<td>数据生命周期</td>
<td>长期保留</td>
<td>老化归档+删除</td>
<td>不匹配</td>
</tr>
<tr>
<td>索引结构</td>
<td>B-tree（行级）</td>
<td>列式（按 metric_name 聚簇）</td>
<td>不匹配</td>
</tr>
<tr>
<td>压缩</td>
<td>行级压缩</td>
<td>列式压缩（同类型数据）</td>
<td>不匹配</td>
</tr>
<tr>
<td>数据模型</td>
<td>关系表</td>
<td>时间序列 + 标签</td>
<td>不匹配</td>
</tr>
</tbody>
</table>

时序数据库（TSDB）专为时序数据设计，典型代表：InfluxDB、IoTDB、TimescaleDB（PostgreSQL 时序扩展）、TDengine。本期选 IoTDB 原因：

1. **列式存储**——按 metric_name 聚簇，查单点位 14 天数据扫描 1/30 数据量
2. **原生压缩**——同类型数据列式压缩，280 GB 行存 → 30 GB 列存，**节省 89%**
3. **批量写入优化**——顺序追加 + 批量 commit，写入 50000 行/秒
4. **老化归档**——原生支持 TTL + 分级存储（秒级→分钟级→小时级）
5. **Apache 顶级项目**——开源、社区活跃、工业场景验证

## 底层原理：IoTDB 的时序数据模型

### 时间序列（Time Series）模型

IoTDB 把每条时间序列抽象为 `root.<storage_group>.<device>.<metric>` 四级路径：

```
root.cell_mes.f1_012.formation_voltage
root.cell_mes.f1_012.formation_current
root.cell_mes.f1_012.formation_temperature
root.cell_mes.bc_001.coating_speed
root.cell_mes.bc_001.coating_tension
```

每条时间序列是一组 `(timestamp, value)` 对，按 timestamp 顺序存储。IoTDB 的存储组（storage group）是物理存储单元，**同组数据落同一 TsFile**，跨组查询走并行。

### 时序生命周期分级

事故 1 复盘时的痛点是"查 14 天数据慢"，根因是**所有数据都在同一级别（秒级）**，没分级。工程上的解法是**时序生命周期分级**——秒级数据保留 7 天，7 天后归档成分钟级，30 天后归档成小时级，**各级别独立查询性能优化**：

<table>
<thead><tr>
<th>级别</th>
<th>数据频率</th>
<th>保留期</th>
<th>查询场景</th>
<th>单设备 1 月数据量</th>
</tr></thead>
<tbody>
<tr>
<td>秒级</td>
<td>1 秒</td>
<td>7 天</td>
<td>实时监控、Andon</td>
<td>604800 行</td>
</tr>
<tr>
<td>分钟级</td>
<td>1 分钟</td>
<td>30 天</td>
<td>SPC 控制图、OEE</td>
<td>43200 行</td>
</tr>
<tr>
<td>小时级</td>
<td>1 小时</td>
<td>1 年</td>
<td>月度报表、趋势分析</td>
<td>720 行</td>
</tr>
</tbody>
</table>

老李查"化成柜 F1-012 当周电压曲线"在秒级数据（7 天内）走，查"最近 1 月容量趋势"在小时级数据走——**1 月 720 行秒级查 = 720 行小时级查，40 秒 → 0.05 秒**。

### 列式存储与压缩

IoTDB 用 TsFile（列式文件）存储时序数据，每个 metric 单独一列：

```
TsFile: cell_mes_f1_012.tsfile
├── formation_voltage 列
│   ├── Chunk 1 (2025-12-15 00:00 - 04:00, 14400 个点)
│   ├── Chunk 2 (2025-12-15 04:00 - 08:00, 14400 个点)
│   └── ...
├── formation_current 列
└── formation_temperature 列
```

每个 Chunk 内同类型数据连续存储，可应用**RLE / Dictionary / Delta 编码**压缩。化成电压数据是浮点数 3.65-4.20V，相邻点 delta < 0.01V，**Delta 编码压缩比 10:1**，280 GB 行存 → 30 GB 列存。

## 正确姿势：IoTDB 分级存储工程实现

### 1. 存储组与时间序列定义

```sql
-- IoTDB 不用 SQL DDL，用 SESSION API 创建时间序列
-- 等价于创建"表结构"
session.setStorageGroup("root.cell_mes");

-- 化成柜 F1-012 的三个核心点位
session.createTimeseries(
    "root.cell_mes.f1_012.formation_voltage",
    TSDataType.DOUBLE, TSEncoding.GORILLA, CompressionType.SNAPPY);
session.createTimeseries(
    "root.cell_mes.f1_012.formation_current",
    TSDataType.DOUBLE, TSEncoding.GORILLA, CompressionType.SNAPPY);
session.createTimeseries(
    "root.cell_mes.f1_012.formation_temperature",
    TSDataType.DOUBLE, TSEncoding.RLE, CompressionType.SNAPPY);
```

存储组 `root.cell_mes` 是物理存储单元，**同组落同一 TsFile**——化成柜、涂布机、辊压机的数据按存储组分文件，跨设备查询走并行。

### 2. 写入优化：批量 + 异步

```java
@Service
public class MetricWriteService {
    private final IoTDBSession session;
    private final BlockingQueue<MetricEvent> queue = new LinkedBlockingQueue<>(100000);

    @PostConstruct
    public void init() {
        // 单独线程消费队列，批量写入
        Executors.newSingleThreadExecutor().submit(this::flushLoop);
    }

    public void write(MetricEvent evt) {
        // 不阻塞采集线程，入队即返回
        if (!queue.offer(evt, 100, MILLISECONDS)) {
            log.warn("IoTDB queue full, drop metric: " + evt);
        }
    }

    private void flushLoop() {
        List<MetricEvent> batch = new ArrayList<>(5000);
        while (true) {
            MetricEvent first = queue.poll(500, MILLISECONDS);
            if (first == null) continue;
            batch.add(first);
            queue.drainTo(batch, 4999);  // 一次最多 5000 条
            try {
                flushBatch(batch);
            } catch (Exception e) {
                log.error("IoTDB write fail", e);
            }
            batch.clear();
        }
    }

    private void flushBatch(List<MetricEvent> batch) throws Exception {
        List<TSRecord> records = batch.stream()
            .map(e -> new TSRecord(
                e.getTimestamp(),
                "root.cell_mes." + e.getDeviceCode(),
                new MeasurementVector(
                    new String[]{e.getMetricName()},
                    new Object[]{e.getValue()})))
            .collect(Collectors.toList());
        session.insertBatch(records);  // 批量 INSERT
    }
}
```

### 3. 分级归档：秒级 → 分钟级 → 小时级

```java
@Service
public class TieredArchivingService {

    @Scheduled(cron = "0 0 * * * *")  // 每小时
    public void archiveSecondToMinute() {
        // 把 8 天前的秒级数据按分钟聚合（取均值/最大值/最小值）
        long from = now() - 8 * DAY - 1 * HOUR;
        long to = now() - 8 * DAY;

        // IoTDB 用 SQL 聚合（IoTDB 支持 GROUP BY 子句）
        String sql = "SELECT max_value(value), min_value(value), avg(value) " +
            "FROM root.cell_mes.*.* " +
            "WHERE time >= " + from + " AND time <= " + to + " " +
            "GROUP BY (1m)";  // 1 分钟窗口聚合
        SessionDataSet ds = session.executeQuerySql(sql);
        while (ds.hasNext()) {
            Row r = ds.next();
            // 写入分钟级时间序列 root.cell_mes_minute.*
            session.insertRecord(
                "root.cell_mes_minute." + r.getDevice(),
                r.getTimestamp(),
                Arrays.asList("max", "min", "avg"),
                Arrays.asList(r.getDouble(1), r.getDouble(2), r.getDouble(3)));
        }

        // 删除秒级原数据
        session.deleteData("root.cell_mes.*.*", from, to);
    }

    @Scheduled(cron = "0 0 0 * * *")  // 每天 0 点
    public void archiveMinuteToHour() {
        // 把 31 天前的分钟级数据按小时聚合
        long from = now() - 31 * DAY - 1 * HOUR;
        long to = now() - 31 * DAY;

        String sql = "SELECT max_value(max), min_value(min), avg(avg) " +
            "FROM root.cell_mes_minute.*.* " +
            "WHERE time >= " + from + " AND time <= " + to + " " +
            "GROUP BY (1h)";
        // 写入小时级时间序列 root.cell_mes_hour.*
        // ...
    }
}
```

### 4. 查询路由：自动选级别

```java
@Service
public class MetricQueryService {
    @Autowired private IoTDBSession session;

    public TimeSeries query(Long deviceId, String metric,
                            long from, long to) {
        // 按时间范围自动选级别
        long spanMs = to - from;
        String path;
        if (spanMs <= 7 * DAY) {
            // 7 天内走秒级
            path = "root.cell_mes." + deviceCode(deviceId) + "." + metric;
        } else if (spanMs <= 30 * DAY) {
            // 30 天内走分钟级
            path = "root.cell_mes_minute." + deviceCode(deviceId) + "." + metric;
        } else {
            // 1 年内走小时级
            path = "root.cell_mes_hour." + deviceCode(deviceId) + "." + metric;
        }

        String sql = "SELECT * FROM " + path +
            " WHERE time >= " + from + " AND time <= " + to;
        return session.executeQuerySql(sql).toTimeSeries();
    }
}
```

### 5. 冷数据归档到对象存储

```java
@Service
public class ColdArchiveService {

    @Scheduled(cron = "0 0 0 1 * ?")  // 每月 1 号
    public void archiveColdData() {
        // 把 13 个月前的小时级数据归档到 S3/MinIO
        long from = now() - 13 * MONTH - 1 * DAY;
        long to = now() - 13 * MONTH;

        // IoTDB 把 TsFile 导出
        File tsFile = session.exportTsFile(
            "root.cell_mes_hour.*.*", from, to);

        // 上传到 MinIO（S3 兼容）
        s3Client.putObject("cell-mes-archive",
            "iotdb/" + tsFile.getName(), tsFile);

        // 删除 IoTDB 内的冷数据
        session.deleteData("root.cell_mes_hour.*.*", from, to);
    }

    public InputStream loadColdData(long from, long to) {
        // 从 MinIO 拉冷数据
        String key = "iotdb/cell_mes_hour_" +
            Instant.ofEpochMilli(from).toString() + ".tsfile";
        return s3Client.getObject("cell-mes-archive", key);
    }
}
```

## 数据说话：分级存储前后对比

分级存储上线后 1 个月，对比事故前后的存储与查询性能：

<table>
<thead><tr>
<th>维度</th>
<th>PostgreSQL 单级</th>
<th>IoTDB 分级</th>
<th>改进</th>
</tr></thead>
<tbody>
<tr>
<td>单表/文件大小</td>
<td>280 GB（14 天）</td>
<td>35 GB（秒级 7 天）+ 2 GB（分钟级 30 天）+ 0.1 GB（小时级 1 年）= 37 GB</td>
<td>-87%</td>
</tr>
<tr>
<td>写入吞吐</td>
<td>5000 行/秒</td>
<td>50000 行/秒</td>
<td>+10 倍</td>
</tr>
<tr>
<td>写入延迟</td>
<td>5-10 秒</td>
<td>< 1 秒</td>
<td>-90%</td>
</tr>
<tr>
<td>14 天范围查询</td>
<td>40 秒（604800 行）</td>
<td>0.05 秒（秒级）+ 0.01 秒（分钟级）</td>
<td>-99.9%</td>
</tr>
<tr>
<td>1 月趋势查询</td>
<td>不可能（表太大）</td>
<td>0.02 秒（小时级 720 行）</td>
<td>∞</td>
</tr>
<tr>
<td>备份大小</td>
<td>280 GB</td>
<td>37 GB</td>
<td>-87%</td>
</tr>
<tr>
<td>备份耗时</td>
<td>4 小时</td>
<td>30 分钟</td>
<td>-87%</td>
</tr>
<tr>
<td>磁盘成本</td>
<td>月 600 GB</td>
<td>月 80 GB</td>
<td>-87%</td>
</tr>
</tbody>
</table>

关键发现：

1. **写入吞吐 50000 行/秒**——比 PostgreSQL 5000 行/秒快 10 倍，96 台化成柜 + 涂布 + 辊压 + 卷绕所有设备同时写入不堵。
2. **14 天范围查询 0.05 秒**——老李说"打瞌睡的查询"现在瞬出，工程师愿意看 SPC 控制图了。
3. **磁盘成本月省 520 GB**——按 0.5 元/GB/月 算，月省 260 元，年省 3120 元，**ROI 高**（IoTDB 部署成本 0，开源）。
4. **冷数据归档**——1 年前的数据归档到 MinIO（S3 兼容），存 IoTDB 的热数据保留 1 年，**热冷分离**。

## 面试怎么答：时序数据存储

**问：为什么不用 TimescaleDB（PostgreSQL 时序扩展）？**

答：3 个原因：① **写入吞吐**——TimescaleDB 用 PostgreSQL 内核 + 时序优化，写入约 20000 行/秒，IoTDB 50000 行/秒快 2.5 倍；② **列式压缩比**——TimescaleDB 仍用 PostgreSQL 行存 + 部分列式，压缩比 3:1，IoTDB 全列式 + Gorilla 编码压缩比 10:1；③ **分级归档原生支持**——IoTDB 原生 TTL + 分级存储 API，TimescaleDB 要用 hypertable + chunks 手动管理。**TimescaleDB 适合已用 PostgreSQL 的项目过渡**，IoTDB 适合从 0 起的新项目本期选 IoTDB。

**问：分级存储为什么要秒级/分钟级/小时级三级？**

答：3 个原因：① **场景差异化**——秒级用于实时监控/Andon（7 天够追溯），分钟级用于 SPC/OEE（30 天够月度报表），小时级用于趋势分析（1 年够年度对比）；② **数据量爆炸**——秒级 30 天 2.6 亿行/设备 × 96 设备 = 250 亿行，不分级查询必然慢；③ **数据特征变化**——秒级点位抖动大、分钟级聚合后稳定、小时级趋势明显，不同级别适用不同分析。**3 级是工程权衡**，2 级（秒+小时）颗粒度跳跃太大，4 级（秒+分+小时+天）天级数据量小不值得单独一级。

**问：IoTDB 的列式压缩为什么能 10:1？**

答：3 个机制：① **同类型数据聚簇**——每个 metric 单独一列，相邻点是同类型浮点数（如化成电压 3.65-4.20V），可应用类型感知编码；② **Delta 编码**——相邻点 delta 小（化成电压秒间 delta < 0.01V），存 delta 而非原值，浮点数变整数；③ **Gorilla 编码**——Facebook 提出，专门为时序浮点数设计，** XOR 编码 + 控制位节省**，浮点 8 字节 → 1-2 字节。3 个机制叠加，行存 80 字节/行 → 列存 8 字节/点，压缩比 10:1。

**问：冷数据归档到 S3 有什么好处？为什么不全留在 IoTDB？**

答：3 个原因：① **存储成本**——IoTDB 用本地磁盘，1 年 80 GB × 12 = 960 GB/年/设备，96 设备 = 92 TB，本地 SSD 成本爆炸；S3 对象存储 0.023 元/GB/月，92 TB 月成本 2100 元，**比本地 SSD 便宜 90%**；② **数据访问模式**——1 年前数据查询频率 < 0.1%/月，**热冷分离**是数据库标准实践，冷数据归档不影响热数据性能；③ **灾备**——S3 跨区域复制天然提供异地备份，本地磁盘故障数据丢失风险高。**冷数据归档是工程标配**，不是可选。

## 落地清单：IoTDB 分级存储工程化动作

1. **存储组划分**：按车间或设备组划分 storage group（root.cell_mes.coating / .formation / .winding），同组落同一 TsFile，跨组查询走并行。
2. **时间序列路径**：root.<group>.<device>.<metric> 四级路径，禁用扁平命名（root.v1, root.v2）。
3. **编码与压缩**：浮点用 Gorilla + Snappy，整数用 RLE + Snappy，禁用默认编码（性能差）。
4. **写入批量 + 异步**：BlockingQueue 缓冲 + 单线程批量 flush，5000 条/批，禁用单条 INSERT。
5. **分级归档定时任务**：每小时秒级→分钟级，每天分钟级→小时级，每月小时级→S3。
6. **查询自动路由**：按时间跨度自动选级别（< 7 天秒级、< 30 天分钟级、> 30 天小时级），禁用统一查秒级。
7. **冷数据 S3 归档**：1 年前数据导出 TsFile 上传 MinIO/S3，IoTDB 只保留 1 年热数据。
8. **黄金回归**：① 96 设备并发写入 50000 行/秒不堵；② 14 天范围查询 < 1 秒；③ 分级归档后秒级数据被删除但分钟级/小时级数据存在；④ 查询路由 7 天/30 天边界自动切换级别；⑤ 冷数据从 S3 拉回可正常解析。

下一章我们离开存储层进入前端，看 Vue3 看板怎么把 Andon 呼叫、SPC 控制图、OEE 三因子、化成预测全可视化，以及怎么做一个简易 APS 排产——这是 MES 的"脸面"，运营每天看的窗口。
