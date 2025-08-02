package com.bililee.demo.fluxapi.controller;

import com.bililee.demo.fluxapi.cache.RequestDeduplicationManager;
import com.bililee.demo.fluxapi.cache.SpecificDataCacheManager;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import com.bililee.demo.fluxapi.config.source.ConfigSource;
import com.bililee.demo.fluxapi.monitoring.CacheMonitoringService;
import com.bililee.demo.fluxapi.monitoring.impl.ConsoleMetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控端点控制器
 * 提供缓存状态、配置信息、系统指标的查询接口
 */
@Slf4j
@RestController
@RequestMapping("/monitor")
public class MonitoringController {

    @Autowired
    private SpecificDataCacheManager cacheManager;

    @Autowired
    private RequestDeduplicationManager deduplicationManager;

    @Autowired
    private CacheStrategyConfig cacheStrategyConfig;

    @Autowired
    private ConfigSource configSource;

    @Autowired
    private CacheMonitoringService monitoringService;

    @Autowired(required = false)
    private ConsoleMetricsCollector consoleMetricsCollector;

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return Mono.fromCallable(() -> {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            
            // 配置源健康状态
            boolean configHealthy = configSource.healthCheck().block();
            health.put("config_source", Map.of(
                    "type", configSource.getType().getName(),
                    "healthy", configHealthy
            ));
            
            // 缓存状态
            SpecificDataCacheManager.CacheStatsInfo cacheStats = cacheManager.getCacheStatsInfo();
            health.put("cache", Map.of(
                    "primary_size", cacheStats.primaryCacheSize(),
                    "stale_size", cacheStats.staleCacheSize(),
                    "hit_rate", String.format("%.2f%%", cacheStats.primaryHitRate() * 100)
            ));
            
            // 系统资源
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            health.put("system", Map.of(
                    "memory_used_mb", usedMemory / 1024 / 1024,
                    "memory_max_mb", maxMemory / 1024 / 1024,
                    "memory_usage_percent", String.format("%.1f%%", (double) usedMemory / maxMemory * 100)
            ));
            
            return ResponseEntity.ok(health);
        });
    }

    /**
     * 缓存统计端点
     */
    @GetMapping("/cache/stats")
    public Mono<ResponseEntity<Map<String, Object>>> cacheStats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            // 缓存管理器统计
            SpecificDataCacheManager.CacheStatsInfo cacheStats = cacheManager.getCacheStatsInfo();
            stats.put("cache_manager", Map.of(
                    "primary_cache_size", cacheStats.primaryCacheSize(),
                    "stale_cache_size", cacheStats.staleCacheSize(),
                    "primary_hit_rate", cacheStats.primaryHitRate(),
                    "stale_hit_rate", cacheStats.staleHitRate(),
                    "eviction_count", cacheStats.evictionCount(),
                    "active_refresh_tasks", cacheStats.activeRefreshTasks()
            ));
            
            // 请求去重统计
            int pendingRequests = deduplicationManager.getPendingRequestsCount();
            stats.put("request_deduplication", Map.of(
                    "pending_requests", pendingRequests,
                    "all_stats", deduplicationManager.getAllRequestStats()
            ));
            
            return ResponseEntity.ok(stats);
        });
    }

    /**
     * 配置信息端点
     */
    @GetMapping("/config")
    public Mono<ResponseEntity<Map<String, Object>>> configInfo() {
        return Mono.fromCallable(() -> {
            Map<String, Object> config = new HashMap<>();
            
            config.put("config_source_type", configSource.getType().getName());
            config.put("cache_rules_count", cacheStrategyConfig.getRules().size());
            config.put("default_strategy", cacheStrategyConfig.getDefaultRule().getStrategy());
            
            return ResponseEntity.ok(config);
        });
    }

    /**
     * 监控指标端点
     */
    @GetMapping("/metrics")
    public Mono<ResponseEntity<Map<String, Object>>> metrics() {
        return Mono.fromCallable(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            // 更新实时指标
            monitoringService.updateRealTimeMetrics();
            
            if (consoleMetricsCollector != null) {
                metrics = consoleMetricsCollector.getMetricsSnapshot();
            } else {
                metrics.put("message", "监控指标收集器未启用");
            }
            
            return ResponseEntity.ok(metrics);
        });
    }

    /**
     * 生成监控报告端点
     */
    @GetMapping("/report")
    public Mono<ResponseEntity<Map<String, Object>>> generateReport() {
        return Mono.fromCallable(() -> {
            // 生成详细的监控报告
            monitoringService.generateMonitoringReport();
            
            Map<String, Object> report = new HashMap<>();
            report.put("message", "监控报告已生成，请查看日志");
            report.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(report);
        });
    }

    /**
     * 系统信息端点
     */
    @GetMapping("/system")
    public Mono<ResponseEntity<Map<String, Object>>> systemInfo() {
        return Mono.fromCallable(() -> {
            Map<String, Object> system = new HashMap<>();
            
            Runtime runtime = Runtime.getRuntime();
            system.put("processors", runtime.availableProcessors());
            system.put("total_memory_mb", runtime.totalMemory() / 1024 / 1024);
            system.put("free_memory_mb", runtime.freeMemory() / 1024 / 1024);
            system.put("max_memory_mb", runtime.maxMemory() / 1024 / 1024);
            
            system.put("java_version", System.getProperty("java.version"));
            system.put("os_name", System.getProperty("os.name"));
            system.put("os_arch", System.getProperty("os.arch"));
            
            return ResponseEntity.ok(system);
        });
    }
}