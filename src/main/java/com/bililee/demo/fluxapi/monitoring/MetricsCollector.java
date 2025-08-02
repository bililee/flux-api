package com.bililee.demo.fluxapi.monitoring;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 监控指标收集器抽象接口
 * 支持扩展到不同的监控系统
 */
public interface MetricsCollector {

    /**
     * 记录计数器指标
     */
    void incrementCounter(String name, Map<String, String> tags);

    /**
     * 记录计时器指标
     */
    void recordTimer(String name, long durationMs, Map<String, String> tags);

    /**
     * 记录仪表盘指标
     */
    void recordGauge(String name, double value, Map<String, String> tags);

    /**
     * 记录直方图指标
     */
    void recordHistogram(String name, double value, Map<String, String> tags);

    /**
     * 获取监控系统类型
     */
    MetricsType getType();

    /**
     * 健康检查
     */
    boolean isHealthy();

    /**
     * 批量提交指标（可选实现）
     */
    default void flush() {
        // 默认不需要批量提交
    }

    /**
     * 监控系统类型
     */
    enum MetricsType {
        PROMETHEUS("prometheus"),
        MICROMETER("micrometer"),
        CUSTOM("custom"),
        CONSOLE("console");

        private final String name;

        MetricsType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 指标数据模型
     */
    @Data
    class MetricData {
        private String name;
        private MetricType type;
        private double value;
        private Map<String, String> tags;
        private Instant timestamp;

        public enum MetricType {
            COUNTER, TIMER, GAUGE, HISTOGRAM
        }
    }
}