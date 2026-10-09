package com.cellmes.archive.service;

import com.cellmes.archive.TimeSeriesStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 分层归档（ch11）：秒级原始数据 → 分钟均值（_1min）→ 小时均值（_1h）。
 * 原始明细只保留短窗口，长期趋势查聚合层 —— 这是 IoTDB/InfluxDB 的通用玩法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TieredArchivingService {

    private final TimeSeriesStore store;

    /** 每 5 分钟自动归档一次（教学骨架：内存数据量小，幂等覆盖）。 */
    @Scheduled(fixedDelay = 300_000)
    public void scheduledArchive() {
        archiveAll(Instant.now().minus(2, ChronoUnit.HOURS), Instant.now());
    }

    /** 对全部已知序列做一轮秒→分归档（演示/手动触发入口）。 */
    public Map<String, Integer> archiveAll(Instant from, Instant to) {
        Map<String, Integer> result = new TreeMap<>();
        for (String series : store.knownSeries()) {
            if (series.endsWith("_1min") || series.endsWith("_1h")) {
                continue;
            }
            int points = archive(series, series + "_1min", from, to, 60);
            if (points > 0) {
                result.put(series, points);
            }
        }
        log.info("[分层归档] 秒→分完成: {}", result);
        return result;
    }

    /** 单序列按窗口均值归档（tag 组原样保留）。 */
    public int archive(String sourceSeries, String targetSeries, Instant from, Instant to, int windowSeconds) {
        int count = 0;
        // 按 tag 组归档：先查原始（tags=null 拿不到全部 tag 组，教学骨架遍历已知 series 键）
        for (String known : store.knownSeries()) {
            if (!known.equals(sourceSeries) && !known.startsWith(sourceSeries + "|")) {
                continue;
            }
            Map<String, String> tags = parseTags(known);
            List<TimeSeriesStore.Point> points = store.query(sourceSeries, tags, from, to);
            if (points.isEmpty()) {
                continue;
            }
            long windowMillis = windowSeconds * 1000L;
            Instant winStart = points.getFirst().ts().truncatedTo(ChronoUnit.SECONDS);
            double sum = 0;
            int n = 0;
            for (TimeSeriesStore.Point p : points) {
                Instant pWin = p.ts().truncatedTo(ChronoUnit.SECONDS);
                long delta = pWin.toEpochMilli() - winStart.toEpochMilli();
                if (delta / windowMillis > 0) {
                    // 窗口关闭：写均值，开新窗口
                    store.write(targetSeries, tags, winStart, sum / n);
                    count++;
                    winStart = pWin;
                    sum = 0;
                    n = 0;
                }
                sum += p.value();
                n++;
            }
            if (n > 0) {
                store.write(targetSeries, tags, winStart, sum / n);
                count++;
            }
        }
        return count;
    }

    /** 从存储键还原 tag 组（series|k=v|k=v）。 */
    private Map<String, String> parseTags(String key) {
        Map<String, String> tags = new TreeMap<>();
        String[] parts = key.split("\\|");
        for (int i = 1; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq > 0) {
                tags.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
            }
        }
        return tags;
    }
}
