---
title: ch04 OPC UA + Modbus + SECS 采集层：涂布机/化成柜/卷绕机协议适配
slug: cell-mes-04-collect
---

## 事故现场：三家厂商、三种协议、一个化成柜占了 6 倍带宽

电芯厂 A 的设备清单按协议分三类：

<table>
<thead><tr>
<th>工序</th>
<th>设备</th>
<th>厂商</th>
<th>协议</th>
<th>点位数</th>
</tr></thead>
<tbody>
<tr>
<td>涂布</td>
<td>涂布机 BC-001/002</td>
<td>日本 Bole</td>
<td>OPC UA</td>
<td>380</td>
</tr>
<tr>
<td>辊压</td>
<td>辊压机 RP-001</td>
<td>海外某厂商</td>
<td>OPC UA</td>
<td>260</td>
</tr>
<tr>
<td>分切</td>
<td>分切机 SL-001</td>
<td>国产</td>
<td>Modbus TCP</td>
<td>180</td>
</tr>
<tr>
<td>卷绕</td>
<td>卷绕机 WND-001~008</td>
<td>日本某厂商</td>
<td>SECS/GEM HSMS</td>
<td>240/台 × 8</td>
</tr>
<tr>
<td>注液</td>
<td>注液机 FL-001</td>
<td>国产</td>
<td>Modbus TCP</td>
<td>220</td>
</tr>
<tr>
<td>化成</td>
<td>化成柜 F1-001~096</td>
<td>国产新威</td>
<td>Modbus TCP + 私有协议</td>
<td>96 柜 × 30 = 2880</td>
</tr>
<tr>
<td>分容</td>
<td>分容柜 FC-001~096</td>
<td>同上</td>
<td>Modbus TCP</td>
<td>2880</td>
</tr>
</tbody>
</table>

初版采集层架构简单粗暴——每个协议一个 Collector 线程池，所有设备共享。上线第一周内三起问题：

**问题 1：化成柜占用 6 倍带宽**。化成柜 96 台 × 30 点位 = 2880 个点位，按 1 秒一次轮询，单台 Modbus 轮询 30 个点位 ≈ 100ms，96 台并发轮询占用 9.6 个 Modbus 长连接，**采集进程 TCP 连接数 280 个**（化成+分容占了 5400 个点位轮询）。其他工艺的点位查询被排队，**辊压机辊压压力超阈值报警延迟了 12 秒**——辊压线在这 12 秒里压了一卷废品。

**问题 2：OPC UA 重连风暴打挂采集**。涂布机厂商 Bole 的 OPC UA 服务端每周日凌晨做重启维护，96 台化成柜的 Modbus 没事，但 2 台涂布机的 OPC UA 同步断连，Milo SDK 默认指数退避重连，**重连风暴下采集进程 200 个 worker 线程全卡在 `synchronized` 重连锁上**，整个采集层假死 5 分钟。

**问题 3：SECS/GEM 会话过载**。卷绕机 8 台 × 240 点位 × 1 秒轮询，每秒 1920 个 SECS Message，但 SECS/GEM 是半双工 HSMS 协议，单链路控制字上限是 100 T1（10s）超时一遇，**单链路被点位轮询打满 60% 链路利用率**，链路真正的控制信号（如远程指令下发）排队等待，**派工单下发到卷绕机的延迟超过 8 秒**。

## 排查：三种协议的本质差异

排查发现，问题根因是**用同一种轮询模型处理三种本质不同的协议**。这三种协议在工业控制层的语义截然不同：

<table>
<thead><tr>
<th>维度</th>
<th>OPC UA</th>
<th>Modbus TCP</th>
<th>SECS/GEM</th>
</tr></thead>
<tbody>
<tr>
<td>抽象层级</td>
<td>信息模型（对象树）</td>
<td>寄存器表</td>
<td>半双工消息 + 状态机</td>
</tr>
<tr>
<td>数据模型</td>
<td>节点 ID（NodeID）</td>
<td>地址（4 区：Coil/Discrete/Input/Holding）</td>
<td>Stream + Function + Variable</td>
</tr>
<tr>
<td>传输</td>
<td>TCP 长连接 + 二进制</td>
<td>TCP + 二进制帧</td>
<td>TCP + HSMS 控制字</td>
</tr>
<tr>
<td>通信模式</td>
<td>订阅（Subscription）</td>
<td>主从轮询</td>
<td>主从 + 事件驱动</td>
</tr>
<tr>
<td>实时性</td>
<td>100ms 级（订阅推送）</td>
<td>100ms-1s（轮询）</td>
<td>秒级（HSMS 半双工）</td>
</tr>
<tr>
<td>重连复杂度</td>
<td>复杂（subscription 状态恢复）</td>
<td>简单</td>
<td>中等（GEM 状态恢复）</td>
</tr>
<tr>
<td>点位获取</td>
<td>客户端订阅，服务端推</td>
<td>客户端主动读</td>
<td>客户端 S1F1/S1F2 等</td>
</tr>
<tr>
<td>带宽效率</td>
<td>高（推送增量）</td>
<td>低（轮询全量）</td>
<td>低（每点位一个 message）</td>
</tr>
</tbody>
</table>

**关键洞察**：化成柜走 Modbus 轮询是带宽杀手——每秒 100ms × 30 点位 = 3 秒单台，96 台要 288 秒/秒走完一轮，等于永远轮询不完。**应该改成事件驱动**——化成柜只在状态变化（充电→放电、电压突变）时主动上报，MES 用 Modbus 的 0x17 多点位写功能实现"反向通知订阅"，单台带宽降到 1/30。

OPC UA 应该用 Subscription 而不是 Read 服务——订阅让服务端按数据变化推送，不变化的点位不传输，涂布机 380 个点位里实际变化的只有 40 个，**带宽节省 90%**。

SECS/GEM 不能用纯点位轮询——SECS 协议设计本来是消息流（事件 + 控制指令），不该被点位轮询占满链路。**点位应该走 SVID 流（Stream 1）的事件触发**，而非每秒一次的 S1F3/S1F4 主动读。

## 底层原理：三种协议的工业语义

### OPC UA 信息模型与 Subscription

OPC UA 不只是"协议"，是**信息模型**——它把设备抽象成一棵对象树（ ObjectType / VariableType ），每个节点有 NodeID、BrowseName、Value。涂布机的 OPC UA 信息模型示例：

```
Root
└── Objects
    └── Coater_001 (ObjectType: CoaterType)
        ├── Status (Variable, NodeID=ns=2;s=Status)
        │   ├── RunMode (Int32)
        │   ├── AlarmCode (UInt16)
        │   └── WebSpeed (Float, m/min)
        ├── Materials
        │   └── Foil (ObjectType)
        │       └── Tension (Float, N)
        └── ProcessValues
            ├── Thickness (Float, μm)  ← 关键参数
            └── CoatingWeight (Float, g/m²)
```

**Subscription 模型**：客户端创建一个 Subscription（订阅组），把关心的 Variable 加入 MonitoredItem，指定 samplingInterval（服务端采样间隔）和 publishingInterval（推送间隔）。服务端在数据变化时主动推 `DataChangeNotification`，未变化的点位不传输。

```java
// Milo SDK 订阅代码骨架
UaClient client = UaClient.create(endpointUrl)
    .setIdentityProvider(new AnonymousProvider())
    .build();
client.connectFuture().get();

// 创建订阅
UaSubscription sub = client.getSubscriptionManager()
    .createSubscription(500.0,  // publishingInterval 500ms
        PublishingMode.ResponsesSent, 30, true, true, 0, 0).get();

// 添加监控项
List<MonitoredItemRequest> items = List.of(
    MonitoredItemRequest.newBuilder()
        .setNodeId(new NodeId(2, "Coater.001.Status.WebSpeed"))
        .setSamplingInterval(200.0)  // 服务端 200ms 采样
        .setMonitoringMode(MonitoringMode.Reporting)
        .build(),
    MonitoredItemRequest.newBuilder()
        .setNodeId(new NodeId(2, "Coater.001.ProcessValues.Thickness"))
        .setSamplingInterval(500.0)
        .build()
);
sub.createMonitoredItems(items, new DataChangeNotification() {
    @Override public void onDataChange(List<MonitoredItem> items) {
        for (MonitoredItem it : items) {
            // 变化的点位推 IoTDB
            iotdbService.write("root.cellmes.coat.bc001." + it.getNodeId(),
                it.getValue().getValue());
        }
    }
});
```

### Modbus TCP 协议帧与多点位读

Modbus TCP 是主从协议，客户端发请求帧，服务端响应。一帧可以批量读多个 Holding Register（功能码 0x03）：

```
请求帧:
  Transaction ID (2字节)
  Protocol ID (2字节, =0)
  Length (2字节)
  Unit ID (1字节, 设备地址)
  Function Code (1字节, 0x03=Read Holding Registers)
  Starting Address (2字节)
  Quantity (2字节, 1~125)

响应帧:
  ... (Header 同上)
  Byte Count (1字节)
  Register Values (N × 2字节)
```

**优化技巧**：把同一设备的相邻寄存器批量读，**单帧最多 125 个寄存器**，单次 RTT（局域网 5ms）读 125 个点位，远比单点位轮询快。化成柜 30 个点位位置相邻，**单帧读完**，耗时 5ms 而非 30 × 5ms = 150ms。

**事件驱动**：Modbus 本身无订阅，但可以通过 0x17（读写多寄存器）反向"写通知位"通知服务端：客户端周期写 `LastQueryTime` 寄存器，服务端在状态变化时主动写 `StatusChangeFlag` 寄存器，客户端读 flag 决定是否拉数据。这叫"标志位轮询"，单帧 1 个寄存器，5ms 一轮。

### SECS/GEM 半双工与状态机

SECS/GEM 是半导体/电池行业标准，TCP + HSMS（High-Speed Message Services）半双工：

- **控制消息**：Select/LinkTest/Deselect（链路管理）
- **数据消息**：Stream + Function（如 S1F1 = Are You There Request, S1F2 = Are You There Data）
- **事务**：每个 Primary Message 必须等对应的 Reply Message（T3 超时 60s）

SECS/GEM 设备有标准状态机（GEM State Model），MES 通过 S2F41（Host Command）远程控制状态切换：

```
State 1: Idle
  → S2F41 Start → State 2: Setup
State 2: Setup
  → S6F11 Event Executing → State 3: Execute
State 3: Execute
  → S6F11 Event Complete → State 4: Complete
```

**点位采集的正确姿势**：用 SVID（Status Variable ID）+ Event Report，而非 S1F3 主动读。设备进入 Execute 状态时主动推 S6F11 事件，携带 SVID 列表数据，MES 被动接收——这就是事件驱动。

## 正确姿势：三种协议的采集层设计

### 协议适配器抽象

```java
public interface DeviceProtocolAdapter {
    void connect(DeviceConfig cfg) throws ConnectException;
    void subscribePoints(List<PointDefinition> points, DataCallback callback);
    void writeControl(String pointKey, Object value);
    void disconnect();
}

public class OpcUaAdapter implements DeviceProtocolAdapter { /* Milo */ }
public class ModbusAdapter implements DeviceProtocolAdapter { /* j2mod */ }
public class SecsGemAdapter implements DeviceProtocolAdapter { /* OpenSECS */ }
```

### 涂布机 OPC UA：Subscription 优先

```java
public class OpcUaAdapter implements DeviceProtocolAdapter {
    private UaClient client;
    private Map<String, UaSubscription> subs = new ConcurrentHashMap<>();

    @Override
    public void subscribePoints(List<PointDefinition> points, DataCallback cb) {
        // 按 samplingInterval 分组订阅（同间隔合并）
        Map<Double, List<PointDefinition>> byInterval = points.stream()
            .collect(Collectors.groupingBy(PointDefinition::getSamplingInterval));
        for (var e : byInterval.entrySet()) {
            UaSubscription sub = createSubscription(e.getKey(), e.getValue(), cb);
            subs.put(e.getValue().get(0).getSourceId(), sub);
        }
    }

    private UaSubscription createSubscription(double interval,
            List<PointDefinition> pts, DataCallback cb) {
        UaSubscription sub = client.getSubscriptionManager()
            .createSubscription(interval, ...).get();
        List<MonitoredItemRequest> items = pts.stream()
            .map(p -> MonitoredItemRequest.newBuilder()
                .setNodeId(parseNodeId(p.getAddress()))
                .setSamplingInterval(p.getSamplingInterval())
                .build())
            .collect(Collectors.toList());
        sub.createMonitoredItems(items, notification -> {
            for (MonitoredItem it : notification.getMonitoredItems()) {
                cb.onChange(it.getNodeId().getIdentifier().toString(),
                    it.getValue().getValue());
            }
        });
        return sub;
    }
}
```

### 化成柜 Modbus：批量读 + 标志位轮询

```java
public class ModbusAdapter implements DeviceProtocolAdapter {
    private ModbusMaster master;
    private ScheduledExecutorService scheduler;

    @Override
    public void subscribePoints(List<PointDefinition> points, DataCallback cb) {
        // 1. 按设备+地址相邻性分组，每组一帧批量读
        List<List<PointDefinition>> groups = groupAdjacent(points);
        // 2. 周期批量读（普通点位）
        scheduler.scheduleAtFixedRate(() -> {
            for (List<PointDefinition> g : groups) {
                int startAddr = g.get(0).getAddress();
                int count = g.size();
                int[] regs = master.readHoldingRegisters(unitId, startAddr, count);
                for (int i = 0; i < g.size(); i++) {
                    cb.onChange(g.get(i).getKey(), regs[i]);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        // 3. 关键事件点位（如 StatusChangeFlag）走高频标志位轮询
        scheduler.scheduleAtFixedRate(() -> {
            int flag = master.readHoldingRegisters(unitId, STATUS_FLAG_ADDR, 1)[0];
            if (flag != lastFlag) {
                lastFlag = flag;
                // 状态变化时拉一次全量
                int[] all = master.readHoldingRegisters(unitId, 0, 30);
                cb.onEvent("STATUS_CHANGE", all);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);  // 100ms 高频
    }
}
```

### 卷绕机 SECS/GEM：事件驱动 + 控制信号优先

```java
public class SecsGemAdapter implements DeviceProtocolAdapter {
    private SecsSession session;

    @Override
    public void subscribePoints(List<PointDefinition> points, DataCallback cb) {
        // 1. 启用事件报告 S2F33/S2F35（启用报告）
        session.send(new S2F33(ENABLE_ALL_REPORTS)).awaitReply(60, SECONDS);
        // 2. 注册事件回调（S6F11 事件报告）
        session.registerCallback(S6F11.class, event -> {
            for (SVIDValue v : event.getSvidValues()) {
                cb.onChange(v.getSvid(), v.getValue());
            }
        });
        // 3. 链路保活（LinkTest 60s）
        scheduler.scheduleAtFixedRate(() -> session.sendLinkTest(), 60, 60, SECONDS);
        // 关键：禁用 S1F3 主动读！点位全靠 S6F11 事件驱动
    }

    @Override
    public void writeControl(String pointKey, Object value) {
        // 控制指令优先级最高，立即发不排队
        if ("START".equals(pointKey)) {
            session.sendPriority(new S2F41("START")).awaitReply(10, SECONDS);
        }
    }
}
```

**关键设计**：控制指令（S2F41 Host Command）走 `sendPriority`，跳过点位轮询队列——事故 3 的根因防线。

## 数据说话：协议改造后带宽对比

<table>
<thead><tr>
<th>设备</th>
<th>协议</th>
<th>改造前带宽</th>
<th>改造后带宽</th>
<th>改善</th>
</tr></thead>
<tbody>
<tr>
<td>涂布机 380 点</td>
<td>OPC UA</td>
<td>760 点位/s（轮询）</td>
<td>38 点位/s（订阅推送）</td>
<td>95%</td>
</tr>
<tr>
<td>辊压机 260 点</td>
<td>OPC UA</td>
<td>520 点位/s</td>
<td>26 点位/s</td>
<td>95%</td>
</tr>
<tr>
<td>卷绕机 240 点 × 8</td>
<td>SECS/GEM</td>
<td>1920 msg/s</td>
<td>200 msg/s（事件）</td>
<td>89%</td>
</tr>
<tr>
<td>化成柜 96 × 30</td>
<td>Modbus</td>
<td>2880 点位 × 200ms 轮询</td>
<td>96 标志位 100ms + 事件</td>
<td>87%</td>
</tr>
<tr>
<td>全部 OPC UA TCP 连接</td>
<td>-</td>
<td>280 个</td>
<td>7 个（订阅合并）</td>
<td>97.5%</td>
</tr>
<tr>
<td>辊压压力超阈值报警延迟</td>
<td>-</td>
<td>12s</td>
<td>250ms</td>
<td>48x</td>
</tr>
<tr>
<td>派工单下发到卷绕机延迟</td>
<td>-</td>
<td>8s</td>
<td>600ms</td>
<td>13x</td>
</tr>
<tr>
<td>采集进程 TCP 连接数</td>
<td>-</td>
<td>280</td>
<td>12</td>
<td>23x</td>
</tr>
</tbody>
</table>

## 面试怎么答：MES 采集层三个高频问题

**问：MES 采集层为什么不能所有设备用同一种轮询模型？**

答：① **协议本质不同**——OPC UA 是订阅推送，Modbus 是主从轮询，SECS/GEM 是事件+状态机；② **带宽效率差三个数量级**——OPC UA 订阅比轮询省 95%，SECS 事件比轮询省 89%；③ **实时性差异**——OPC UA 推送 100ms，Modbus 轮询 1s，SECS 事件秒级；④ **控制指令优先级**——SECS/GEM 控制指令和点位轮询共用一条链路时，点位轮询会阻塞控制指令，必须用优先级队列。同一种模型套三种协议等于丢了三种协议的优势。

**问：OPC UA Subscription 与 Read 服务的本质区别？**

答：① **触发方向**——Read 是客户端主动拉，Subscription 是服务端推；② **数据时机**——Read 周期返回全量（包含未变化的），Subscription 只返回变化的 MonitoredItem；③ **带宽利用**——380 个点位里实际变化的只有 40 个，Subscription 省 90% 带宽；④ **网络往返**——Read 每点位一次 RTT，Subscription 一次推送包含多个变化项；⑤ **服务端复杂度**——Read 简单，Subscription 要维护订阅状态机（重连后状态恢复）。

**问：Modbus TCP 怎么实现"事件驱动"？本身没订阅机制啊。**

答：Modbus 协议本身确实没有订阅，但可以用"标志位轮询"模拟事件驱动——① 客户端 100ms 高频读单个 `StatusChangeFlag` 寄存器（单帧 5ms，CPU 几乎为零）；② flag 变化时立即拉一次全量点位；③ 服务端在状态变化时主动写 flag 寄存器（用 Modbus 0x10 多寄存器写）。化成柜 96 台 × 30 点位的轮询带宽从 2880 点位/s 降到 96 标志位/s + 偶发事件全量拉，**87% 带宽节省**。

## 落地清单：采集层工程化动作

1. **协议适配器抽象**：DeviceProtocolAdapter 接口 + 三个实现（OpcUa/Modbus/SecsGem），未来加新协议（如 Profinet）走新实现类。
2. **OPC UA 必用 Subscription**：禁用 Read 服务做点位轮询；按 samplingInterval 分组合并订阅。
3. **Modbus 必批量读**：同一设备的相邻寄存器一帧 0x03 批量读，禁单点位轮询；关键事件用 0x10 多寄存器写通知位 + 100ms 标志位轮询。
4. **SECS/GEM 必用事件报告**：S2F33/S2F35 启用报告 + S6F11 事件回调；禁用 S1F3 主动读点位；控制指令 S2F41 走 sendPriority。
5. **连接池化**：每个设备一个连接，OPC UA Subscription 共用客户端连接（多设备一连接）；Modbus 必须一设备一连接（设备端不支持并发）。
6. **断线重连指数退避+抖动**：基础 1s，最大 60s，随机抖动 ±20%——避免重连风暴（96 台设备同步重连）。
7. **Kafka 推送模式**：每个点位变更一个事件 + 批量打包（500ms 窗口或 500 条事件打包发）——Kafka 吞吐优化。
8. **黄金回归**：① OPC UA 服务端模拟器（如 NodeOPCUA）+ 订阅延迟基准；② Modbus 模拟器 + 100 设备并发轮询压测；③ SECS/GEM 链路利用率断言 < 30%。

下一章我们离开采集层回到业务层，看工艺路线与 BOM 在派工时怎么级联展开、替代料怎么选择——这是事故 1 改派时化成曲线错配的根因防线。
