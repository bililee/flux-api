package com.bililee.demo.fluxapi.monitoring;

import com.bililee.demo.fluxapi.cache.RequestDeduplicationManager;
import com.bililee.demo.fluxapi.cache.SpecificDataCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 缓存监控服务
 * 负责收集和上报缓存相关的监控指标
 */
@Slf4j
@Service
public class CacheMonitoringService {

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private SpecificDataCacheManager cacheManager;

    @Autowired
    private RequestDeduplicationManager deduplicationManager;

    /**
     * 记录缓存命中
     */
    public void recordCacheHit(String sourceId, String cacheStrategy) {
        Map<String, String> tags = Map.of(
                "source_id", sourceId,
                "strategy", cacheStrategy,
                "result", "hit"
        );
        metricsCollector.incrementCounter("cache.access", tags);
        log.debug("记录缓存命中: sourceId={}, strategy={}", sourceId, cacheStrategy);
    }

    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss(String sourceId, String cacheStrategy) {
        Map<String, String> tags = Map.of(
                "source_id", sourceId,
                "strategy", cacheStrategy,
                "result", "miss"
        );
        metricsCollector.incrementCounter("cache.access", tags);
        log.debug("记录缓存未命中: sourceId={}, strategy={}", sourceId, cacheStrategy);
    }

    /**
     * 记录远程服务调用
     */
    public void recordRemoteCall(String sourceId, long durationMs, boolean success) {
        Map<String, String> tags = Map.of(
                "source_id", sourceId,
                "result", success ? "success" : "error"
        );
        
        metricsCollector.incrementCounter("remote.call", tags);
        metricsCollector.recordTimer("remote.call.duration", durationMs, tags);
        
        log.debug("记录远程调用: sourceId={}, duration={}ms, success={}", sourceId, durationMs, success);
    }

    /**
     * 记录请求去重
     */
    public void recordRequestDeduplication(String sourceId, boolean isDeduplicated) {
        Map<String, String> tags = Map.of(
                "source_id", sourceId,
                "deduplicated", String.valueOf(isDeduplicated)
        );
        metricsCollector.incrementCounter("request.deduplication", tags);
        
        log.debug("记录请求去重: sourceId={}, deduplicated={}", sourceId, isDeduplicated);
    }

    /**
     * 记录请求等待时间
     */
    public void recordRequestWaitTime(String sourceId, long waitTimeMs) {
        Map<String, String> tags = Map.of(
                "source_id", sourceId
        );
        metricsCollector.recordTimer("request.wait.duration", waitTimeMs, tags);
        
        log.debug("记录请求等待时间: sourceId={}, waitTime={}ms", sourceId, waitTimeMs);
    }

    /**
     * 记录缓存刷新
     */
    public void recordCacheRefresh(String sourceId, String refreshType, boolean success) {
        Map<String, String> tags = Map.of(
                "source_id", sourceId,
                "refresh_type", refreshType, // active, passive
                "result", success ? "success" : "error"
        );
        metricsCollector.incrementCounter("cache.refresh", tags);
        
        log.debug("记录缓存刷新: sourceId={}, type={}, success={}", sourceId, refreshType, success);
    }

    /**
     * 记录降级操作
     */
    public void recordFallback(String sourceId, String fallbackType) {
        Map<String, String> tags = Map.of(
                "source_id", sourceId,
                "fallback_type", fallbackType // stale_cache, default_value, error_response
        );
        metricsCollector.incrementCounter("fallback.triggered", tags);
        
        log.info("记录降级操作: sourceId={}, type={}", sourceId, fallbackType);
    }

    /**
     * 记录API响应时间
     */
    public void recordApiResponseTime(String sourceId, String endpoint, long durationMs) {
        Map<String, String> tags = Map.of(
                "source_id", sourceId,
                "endpoint", endpoint
        );
        metricsCollector.recordTimer("api.response.duration", durationMs, tags);
        
        log.debug("记录API响应时间: sourceId={}, endpoint={}, duration={}ms", sourceId, endpoint, durationMs);
    }

    /**
     * 记录业务错误
     */
    public void recordBusinessError(String sourceId, String errorType, String errorCode) {
        Map<String, String> tags = Map.of(
                "source_id", sourceId,
                "error_type", errorType,
                "error_code", errorCode
        );
        metricsCollector.incrementCounter("business.error", tags);
        
        log.warn("记录业务错误: sourceId={}, type={}, code={}", sourceId, errorType, errorCode);
    }

    /**
     * 更新实时指标
     */
    public void updateRealTimeMetrics() {
        try {
            // 更新缓存统计指标
            SpecificDataCacheManager.CacheStatsInfo cacheStats = cacheManager.getCacheStatsInfo();
            
            metricsCollector.recordGauge("cache.primary.size", cacheStats.primaryCacheSize(), Map.of());
            metricsCollector.recordGauge("cache.stale.size", cacheStats.staleCacheSize(), Map.of());
            metricsCollector.recordGauge("cache.primary.hit_rate", cacheStats.primaryHitRate(), Map.of());
            metricsCollector.recordGauge("cache.stale.hit_rate", cacheStats.staleHitRate(), Map.of());
            metricsCollector.recordGauge("cache.eviction.count", cacheStats.evictionCount(), Map.of());
            metricsCollector.recordGauge("cache.active_refresh.count", cacheStats.activeRefreshTasks(), Map.of());
            
            // 更新请求去重统计
            int pendingRequests = deduplicationManager.getPendingRequestsCount();
            metricsCollector.recordGauge("request.pending.count", pendingRequests, Map.of());
            
            // 更新系统资源指标
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            metricsCollector.recordGauge("system.memory.used", usedMemory / 1024 / 1024, Map.of("unit", "MB"));
            metricsCollector.recordGauge("system.memory.usage_percent", memoryUsagePercent, Map.of());
            
            log.debug("实时指标更新完成");
        } catch (Exception e) {
            log.error("更新实时指标失败", e);
        }
    }

    /**
     * 生成监控报告
     */
    public void generateMonitoringReport() {
        try {
            log.info("=== 缓存监控报告 ===");
            
            // 缓存统计
            SpecificDataCacheManager.CacheStatsInfo cacheStats = cacheManager.getCacheStatsInfo();
            log.info("主缓存大小: {}, 命中率: {:.2f}%", 
                    cacheStats.primaryCacheSize(), cacheStats.primaryHitRate() * 100);
            log.info("过期缓存大小: {}, 命中率: {:.2f}%", 
                    cacheStats.staleCacheSize(), cacheStats.staleHitRate() * 100);
            log.info("缓存驱逐次数: {}", cacheStats.evictionCount());
            log.info("活跃刷新任务: {}", cacheStats.activeRefreshTasks());
            
            // 请求去重统计
            int pendingRequests = deduplicationManager.getPendingRequestsCount();
            log.info("等待队列大小: {}", pendingRequests);
            
            // 系统资源
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            log.info("内存使用: {} MB / {} MB ({:.1f}%)", 
                    usedMemory / 1024 / 1024, 
                    maxMemory / 1024 / 1024,
                    (double) usedMemory / maxMemory * 100);
            
            log.info("=== 报告结束 ===");
            
            // 刷新指标到监控系统
            metricsCollector.flush();
        } catch (Exception e) {
            log.error("生成监控报告失败", e);
        }
    }
}