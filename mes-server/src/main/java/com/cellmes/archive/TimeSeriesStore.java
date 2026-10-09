package com.cellmes.archive;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 时序存储抽象（ch11）：面向 IoTDB 的端口（Port/Adapter）。
 * 教学骨架用内存实现；上 IoTDB 时新增 IotDbTimeSeriesStore 实现，上层零改动。
 */
public interface TimeSeriesStore {

    void write(String series, Map<String, String> tags, Instant ts, double value);

    List<Point> query(String series, Map<String, String> tags, Instant from, Instant to);

    /** 已知序列名（供分层归档任务遍历）。 */
    java.util.Set<String> knownSeries();

    record Point(Instant ts, double value) {
    }
}
