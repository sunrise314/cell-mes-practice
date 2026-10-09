package com.cellmes.archive;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 内存版时序存储：教学/冒烟用，按 (series + 排序后 tags) 建键。 */
@Component
public class InMemoryTimeSeriesStore implements TimeSeriesStore {

    private final Map<String, List<Point>> data = new ConcurrentHashMap<>();

    static String key(String series, Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return series;
        }
        StringBuilder sb = new StringBuilder(series);
        new TreeMap<>(tags).forEach((k, v) -> sb.append('|').append(k).append('=').append(v));
        return sb.toString();
    }

    @Override
    public void write(String series, Map<String, String> tags, Instant ts, double value) {
        data.computeIfAbsent(key(series, tags), k -> new CopyOnWriteArrayList<>())
                .add(new Point(ts, value));
    }

    @Override
    public List<Point> query(String series, Map<String, String> tags, Instant from, Instant to) {
        List<Point> points = new ArrayList<>(data.getOrDefault(key(series, tags), List.of()));
        points.removeIf(p -> p.ts().isBefore(from) || p.ts().isAfter(to));
        points.sort(Comparator.comparing(Point::ts));
        return points;
    }

    @Override
    public Set<String> knownSeries() {
        Set<String> series = new java.util.HashSet<>();
        data.keySet().forEach(k -> series.add(k.split("\\|")[0]));
        return series;
    }
}
