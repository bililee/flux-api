package com.bililee.demo.fluxapi.monitoring.impl;

import com.bililee.demo.fluxapi.monitoring.MetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 控制台监控指标收集器
 * 用于开发和测试环境，将指标输出到日志
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "monitoring.type", havingValue = "console", matchIfMissing = true)
public class ConsoleMetricsCollector implements MetricsCollector {

    // 简单的内存存储
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> gauges = new ConcurrentHashMap<>();
    private final Map<String, TimerStats> timers = new ConcurrentHashMap<>();

    @Override
    public void incrementCounter(String name, Map<String, String> tags) {
        String key = buildKey(name, tags);
        long newValue = counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        log.info("Counter [{}] = {} {}", name, newValue, formatTags(tags));
    }

    @Override
    public void recordTimer(String name, long durationMs, Map<String, String> tags) {
        String key = buildKey(name, tags);
        TimerStats stats = timers.computeIfAbsent(key, k -> new TimerStats());
        stats.record(durationMs);
        
        log.info("Timer [{}] = {}ms (count={}, avg={}ms, max={}ms) {}", 
                name, durationMs, stats.getCount(), stats.getAverage(), stats.getMax(), formatTags(tags));
    }

    @Override
    public void recordGauge(String name, double value, Map<String, String> tags) {
        String key = buildKey(name, tags);
        gauges.computeIfAbsent(key, k -> new DoubleAdder()).reset();
        gauges.get(key).add(value);
        
        log.info("Gauge [{}] = {} {}", name, value, formatTags(tags));
    }

    @Override
    public void recordHistogram(String name, double value, Map<String, String> tags) {
        // 简化实现，使用计时器统计逻辑
        String key = buildKey(name, tags);
        TimerStats stats = timers.computeIfAbsent(key, k -> new TimerStats());
        stats.record((long) value);
        
        log.info("Histogram [{}] = {} (count={}, avg={}, max={}) {}", 
                name, value, stats.getCount(), stats.getAverage(), stats.getMax(), formatTags(tags));
    }

    @Override
    public MetricsType getType() {
        return MetricsType.CONSOLE;
    }

    @Override
    public boolean isHealthy() {
        return true; // 控制台收集器总是健康的
    }

    @Override
    public void flush() {
        log.info("监控指标统计汇总:");
        log.info("计数器数量: {}", counters.size());
        log.info("仪表盘数量: {}", gauges.size());
        log.info("计时器数量: {}", timers.size());
    }

    /**
     * 构建指标键
     */
    private String buildKey(String name, Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return name;
        }
        
        StringBuilder key = new StringBuilder(name);
        tags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> key.append("|").append(entry.getKey()).append("=").append(entry.getValue()));
        
        return key.toString();
    }

    /**
     * 格式化标签
     */
    private String formatTags(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder("[");
        tags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (sb.length() > 1) sb.append(", ");
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                });
        sb.append("]");
        
        return sb.toString();
    }

    /**
     * 获取当前指标快照
     */
    public Map<String, Object> getMetricsSnapshot() {
        Map<String, Object> snapshot = new ConcurrentHashMap<>();
        
        // 添加计数器
        Map<String, Long> counterSnapshot = new ConcurrentHashMap<>();
        counters.forEach((key, value) -> counterSnapshot.put(key, value.get()));
        snapshot.put("counters", counterSnapshot);
        
        // 添加仪表盘
        Map<String, Double> gaugeSnapshot = new ConcurrentHashMap<>();
        gauges.forEach((key, value) -> gaugeSnapshot.put(key, value.doubleValue()));
        snapshot.put("gauges", gaugeSnapshot);
        
        // 添加计时器
        Map<String, Object> timerSnapshot = new ConcurrentHashMap<>();
        timers.forEach((key, stats) -> {
            Map<String, Object> statsMap = Map.of(
                    "count", stats.getCount(),
                    "total", stats.getTotal(),
                    "average", stats.getAverage(),
                    "max", stats.getMax()
            );
            timerSnapshot.put(key, statsMap);
        });
        snapshot.put("timers", timerSnapshot);
        
        return snapshot;
    }

    /**
     * 计时器统计
     */
    private static class TimerStats {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong total = new AtomicLong(0);
        private final AtomicLong max = new AtomicLong(0);

        public void record(long value) {
            count.incrementAndGet();
            total.addAndGet(value);
            max.updateAndGet(current -> Math.max(current, value));
        }

        public long getCount() {
            return count.get();
        }

        public long getTotal() {
            return total.get();
        }

        public double getAverage() {
            long currentCount = count.get();
            return currentCount == 0 ? 0.0 : (double) total.get() / currentCount;
        }

        public long getMax() {
            return max.get();
        }
    }
}