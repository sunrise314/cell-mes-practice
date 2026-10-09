package com.cellmes.archive.service;

import com.cellmes.archive.TimeSeriesStore;
import com.cellmes.common.domain.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 指标查询（ch11）：按时间跨度路由到秒/分/时层 —— 查询方无感分层。 */
@Service
@RequiredArgsConstructor
public class MetricQueryService {

    private final TimeSeriesStore store;

    public record QueryResult(String routedSeries, List<TimeSeriesStore.Point> points) {
    }

    public QueryResult query(String series, Map<String, String> tags, Instant from, Instant to) {
        long spanMinutes = Duration.between(from, to).toMinutes();
        // <=2h 查原始；<=48h 查分钟聚合；否则小时聚合（空则逐级回退）
        List<String> candidates = spanMinutes <= 120
                ? List.of(series)
                : spanMinutes <= 48 * 60
                        ? List.of(series + "_1min", series)
                        : List.of(series + "_1h", series + "_1min", series);
        for (String candidate : candidates) {
            List<TimeSeriesStore.Point> points = store.query(candidate, tags, from, to);
            if (!points.isEmpty()) {
                return new QueryResult(candidate, points);
            }
        }
        return new QueryResult(candidates.getFirst(), List.of());
    }

    public void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new BizException("时间范围非法：to 必须晚于 from");
        }
    }
}
