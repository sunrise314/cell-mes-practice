package com.cellmes.archive.service;

import com.cellmes.archive.TimeSeriesStore;
import com.cellmes.common.domain.BizException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标写入服务（ch11）：单写线程 + 批量 flush。
 * 业务线程只入队（offer 失败立即报错 fail-closed），单线程批量消费 —— 吞吐优先且不阻塞产线报文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricWriteService {

    private static final int BATCH_SIZE = 500;
    private static final long FLUSH_INTERVAL_MS = 200;

    private final TimeSeriesStore store;

    private final BlockingQueue<WriteTask> queue = new LinkedBlockingQueue<>(10_000);
    private final AtomicLong flushed = new AtomicLong();
    private volatile boolean running = true;
    private Thread flusherThread;

    public record WriteTask(String series, Map<String, String> tags, Instant ts, double value) {
    }

    @PostConstruct
    public void start() {
        flusherThread = Thread.ofPlatform().name("metric-flusher").daemon(true).start(this::flushLoop);
        log.info("[指标写入] 单写线程已启动（批量 {} / {}ms）", BATCH_SIZE, FLUSH_INTERVAL_MS);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (flusherThread != null) {
            flusherThread.interrupt();
        }
    }

    /** 业务线程入口：队列满立即抛错（fail-closed），绝不悄悄丢数据。 */
    public void submit(String series, Map<String, String> tags, Instant ts, double value) {
        if (!queue.offer(new WriteTask(series, tags, ts, value))) {
            throw new BizException(503, "指标写入队列已满（fail-closed），请稍后重试");
        }
    }

    private void flushLoop() {
        List<WriteTask> batch = new ArrayList<>(BATCH_SIZE);
        while (running || !queue.isEmpty()) {
            try {
                WriteTask first = queue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, BATCH_SIZE - 1);
                for (WriteTask task : batch) {
                    store.write(task.series(), task.tags(), task.ts(), task.value());
                }
                flushed.addAndGet(batch.size());
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[指标写入] flush 失败，丢弃 {} 条", batch.size(), e);
                batch.clear();
            }
        }
    }

    public long flushedCount() {
        return flushed.get();
    }
}
