package com.bililee.demo.fluxapi.controller;

import com.bililee.demo.fluxapi.cache.RequestDeduplicationManager;
import com.bililee.demo.fluxapi.cache.SpecificDataCacheManager;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import com.bililee.demo.fluxapi.config.source.ConfigSource;
import com.bililee.demo.fluxapi.monitoring.CacheMonitoringService;
import com.bililee.demo.fluxapi.monitoring.impl.ConsoleMetricsCollector;
import com.bililee.demo.fluxapi.resilience.CircuitBreakerManager;
import com.bililee.demo.fluxapi.resilience.ResilienceService;
import com.bililee.demo.fluxapi.resilience.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Autowired(required = false)
    private ResilienceService resilienceService;

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

    /**
     * 断路器状态端点
     */
    @GetMapping("/circuit-breaker/{sourceId}")
    public Mono<ResponseEntity<CircuitBreakerStatusResponse>> getCircuitBreakerStatus(@PathVariable String sourceId) {
        return Mono.fromCallable(() -> {
            try {
                if (resilienceService != null) {
                    CircuitBreakerManager.CircuitBreakerState state = resilienceService.getCircuitBreakerState(sourceId);
                    CircuitBreakerStatusResponse response = new CircuitBreakerStatusResponse(sourceId, state.name());
                    return ResponseEntity.ok(response);
                } else {
                    CircuitBreakerStatusResponse response = new CircuitBreakerStatusResponse(sourceId, "SERVICE_UNAVAILABLE");
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                log.error("获取断路器状态失败: {}", e.getMessage(), e);
                CircuitBreakerStatusResponse response = new CircuitBreakerStatusResponse(sourceId, "ERROR");
                return ResponseEntity.ok(response);
            }
        });
    }

    /**
     * 线程池状态端点
     */
    @GetMapping("/thread-pool")
    public Mono<ResponseEntity<ThreadPoolManager.ThreadPoolStats>> getThreadPoolStats() {
        return Mono.fromCallable(() -> {
            try {
                if (resilienceService != null) {
                    ThreadPoolManager.ThreadPoolStats stats = resilienceService.getThreadPoolStats();
                    return ResponseEntity.ok(stats);
                } else {
                    ThreadPoolManager.ThreadPoolStats emptyStats = 
                            new ThreadPoolManager.ThreadPoolStats(0, 0, 0, 0, 0);
                    return ResponseEntity.ok(emptyStats);
                }
            } catch (Exception e) {
                log.error("获取线程池状态失败: {}", e.getMessage(), e);
                ThreadPoolManager.ThreadPoolStats errorStats = 
                        new ThreadPoolManager.ThreadPoolStats(0, 0, 0, 0, 0);
                return ResponseEntity.ok(errorStats);
            }
        });
    }

    /**
     * 重置断路器端点
     */
    @PostMapping("/circuit-breaker/{sourceId}/reset")
    public Mono<ResponseEntity<String>> resetCircuitBreaker(@PathVariable String sourceId) {
        return Mono.fromCallable(() -> {
            try {
                if (resilienceService != null) {
                    resilienceService.resetCircuitBreaker(sourceId);
                    log.info("断路器重置成功: {}", sourceId);
                    return ResponseEntity.ok("断路器重置成功: " + sourceId);
                } else {
                    return ResponseEntity.ok("弹性服务不可用");
                }
            } catch (Exception e) {
                log.error("断路器重置失败: {}, 错误: {}", sourceId, e.getMessage(), e);
                return ResponseEntity.ok("断路器重置失败: " + e.getMessage());
            }
        });
    }

    /**
     * 系统综合健康检查端点
     */
    @GetMapping("/health/comprehensive")
    public Mono<ResponseEntity<SystemHealthResponse>> getComprehensiveHealth(
            @RequestParam(value = "sourceId", defaultValue = "default") String sourceId) {
        return Mono.fromCallable(() -> {
            try {
                String circuitBreakerState = "UNKNOWN";
                ThreadPoolManager.ThreadPoolStats threadPoolStats = null;
                
                if (resilienceService != null) {
                    CircuitBreakerManager.CircuitBreakerState state = 
                            resilienceService.getCircuitBreakerState(sourceId);
                    circuitBreakerState = state.name();
                    threadPoolStats = resilienceService.getThreadPoolStats();
                }
                
                SpecificDataCacheManager.CacheStatsInfo cacheStats = 
                        cacheManager.getCacheStatsInfo();

                SystemHealthResponse response = new SystemHealthResponse(
                        sourceId,
                        circuitBreakerState,
                        threadPoolStats,
                        cacheStats,
                        System.currentTimeMillis()
                );

                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("获取系统健康状态失败: {}", e.getMessage(), e);
                SystemHealthResponse errorResponse = new SystemHealthResponse(
                        sourceId, "ERROR", null, null, System.currentTimeMillis()
                );
                return ResponseEntity.ok(errorResponse);
            }
        });
    }

    /**
     * 断路器状态响应
     */
    public record CircuitBreakerStatusResponse(String sourceId, String state) {}

    /**
     * 系统健康状态响应
     */
    public record SystemHealthResponse(
            String sourceId,
            String circuitBreakerState,
            ThreadPoolManager.ThreadPoolStats threadPoolStats,
            SpecificDataCacheManager.CacheStatsInfo cacheStats,
            long timestamp
    ) {}
}