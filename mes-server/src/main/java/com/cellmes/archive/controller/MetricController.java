package com.cellmes.archive.controller;

import com.cellmes.archive.service.MetricQueryService;
import com.cellmes.archive.service.MetricWriteService;
import com.cellmes.archive.service.TieredArchivingService;
import com.cellmes.common.api.R;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 指标接入（ch11）：写入 / 分层归档 / 跨层查询。 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricController {

    private final MetricWriteService writeService;
    private final MetricQueryService queryService;
    private final TieredArchivingService archivingService;

    @Data
    public static class WriteRequest {
        private String series;
        private Map<String, String> tags;
        /** 缺省=now */
        private String ts;
        private Double value;
    }

    @PostMapping("/write")
    public R<Map<String, Object>> write(@RequestBody WriteRequest req) {
        Instant ts = req.getTs() != null ? Instant.parse(req.getTs()) : Instant.now();
        writeService.submit(req.getSeries(), req.getTags(), ts, req.getValue());
        return R.ok(Map.of("queued", true, "flushedTotal", writeService.flushedCount()));
    }

    @GetMapping("/query")
    public R<MetricQueryService.QueryResult> query(@RequestParam String series,
                                                   @RequestParam(required = false) String tags,
                                                   @RequestParam String from,
                                                   @RequestParam String to) {
        Instant f = Instant.parse(from);
        Instant t = Instant.parse(to);
        queryService.validateRange(f, t);
        Map<String, String> tagMap = parseTags(tags);
        return R.ok(queryService.query(series, tagMap, f, t));
    }

    /** 手动触发一轮秒→分归档。 */
    @PostMapping("/archive")
    public R<Map<String, Integer>> archive(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return R.ok(archivingService.archiveAll(f, t));
    }

    private Map<String, String> parseTags(String tags) {
        Map<String, String> map = new HashMap<>();
        if (tags == null || tags.isBlank()) {
            return map;
        }
        for (String pair : tags.split(",")) {
            int eq = pair.indexOf(':');
            if (eq > 0) {
                map.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return map;
    }
}
