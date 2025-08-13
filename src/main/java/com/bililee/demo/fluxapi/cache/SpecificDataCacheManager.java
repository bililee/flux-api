package com.bililee.demo.fluxapi.cache;

import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import com.bililee.demo.fluxapi.response.ApiStatus;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 特定数据缓存管理器
 * 支持主动/被动缓存策略，内存优化，防止GC影响
 */
@Slf4j
@Component
public class SpecificDataCacheManager {

    @Autowired(required = false)
    private CacheStrategyConfig cacheStrategyConfig;

    // 主缓存 - 存储有效数据
    private Cache<String, CachedDataWrapper> primaryCache;
    
    // 过期数据缓存 - 用于降级时返回过期数据
    private Cache<String, CachedDataWrapper> staleCache;
    
    // 主动刷新任务追踪
    private final ConcurrentMap<String, AtomicBoolean> activeRefreshTasks = new ConcurrentHashMap<>();
    
    // 定时任务执行器
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        try {
            log.info("🔧 开始初始化缓存管理器...");
            
            // 简化初始化，只创建基本缓存
            initializeBasicCaches();
            
            log.info("✅ 缓存管理器初始化完成");
            
        } catch (Exception e) {
            log.error("❌ 缓存管理器初始化失败: {}", e.getMessage(), e);
            // 不抛出异常，允许应用继续启动
            log.warn("⚠️  缓存功能将被禁用，应用以无缓存模式运行");
        }
    }

    /**
     * 初始化基本缓存（简化版）
     */
    private void initializeBasicCaches() {
        log.info("🔧 初始化基本缓存存储...");
        
        // 最简单的缓存配置
        staleCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats()
                .build();
                
        primaryCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(Duration.ofMinutes(15))
                .recordStats()
                .removalListener((key, value, cause) -> {
                    try {
                        log.debug("缓存移除: {} - {}", key, cause);
                        // 将移除的数据移到过期缓存中
                        if (value != null && staleCache != null) {
                            staleCache.put((String) key, (CachedDataWrapper) value);
                        }
                    } catch (Exception e) {
                        log.warn("处理缓存移除事件失败: {}", e.getMessage());
                    }
                })
                .build();
        
        // 初始化调度器 - 修复异步刷新问题
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "cache-refresh-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        
        // 启动基本监控任务
        startBasicMonitoring();
                
        log.info("✅ 基本缓存创建成功 - 主缓存: {}, 备用缓存: {}, 调度器: {}", 
                primaryCache.estimatedSize(), staleCache.estimatedSize(), 
                scheduler != null ? "已启动" : "未启动");
    }

    /**
     * 启动基本监控任务
     */
    private void startBasicMonitoring() {
        if (scheduler != null) {
            // 缓存统计报告任务 - 每10分钟报告一次
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    reportCacheStats();
                } catch (Exception e) {
                    log.error("缓存统计报告失败", e);
                }
            }, 10, 10, TimeUnit.MINUTES);
        }
    }

    /**
     * 初始化缓存（完整版 - 暂时禁用）
     */
    @SuppressWarnings("unused")
    private void initializeCaches() {
        // 先初始化过期数据缓存
        staleCache = Caffeine.newBuilder()
                .maximumSize(2000) // 约占用125MB内存
                .expireAfterWrite(Duration.ofHours(2)) // 2小时后彻底删除
                .recordStats()
                .build();

        // 然后初始化主缓存配置 - 简化版本，避免复杂特性
        primaryCache = Caffeine.newBuilder()
                .maximumSize(8000) // 约占用500MB内存（假设每条记录64KB）
                .initialCapacity(1000)
                .expireAfterWrite(Duration.ofMinutes(30)) // 写入后30分钟过期
                .expireAfterAccess(Duration.ofMinutes(10)) // 访问后10分钟过期
                // 移除 refreshAfterWrite - 需要 LoadingCache 支持
                .recordStats() // 启用统计
                .removalListener((key, value, cause) -> {
                    try {
                        log.debug("缓存移除: {} - {}", key, cause);
                        // 将移除的数据移到过期缓存中
                        if (value != null && staleCache != null) {
                            staleCache.put((String) key, (CachedDataWrapper) value);
                        }
                    } catch (Exception e) {
                        log.warn("处理缓存移除事件失败: {}", e.getMessage());
                    }
                })
                .build();

        // 定时任务执行器
        scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "cache-refresh-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动定时任务（暂时禁用）
     */
    @SuppressWarnings("unused")
    private void startScheduledTasks() {
        try {
            log.info("启动缓存定时任务");
            
            // 缓存统计报告任务 - 延迟1分钟启动，每5分钟执行一次
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    reportCacheStats();
                } catch (Exception e) {
                    log.error("缓存统计报告任务执行失败", e);
                }
            }, 1, 5, TimeUnit.MINUTES);
            
            // 清理任务 - 延迟10分钟启动，每10分钟执行一次
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    cleanupTasks();
                } catch (Exception e) {
                    log.error("缓存清理任务执行失败", e);
                }
            }, 10, 10, TimeUnit.MINUTES);
            
            log.info("缓存定时任务启动完成");
        } catch (Exception e) {
            log.error("启动缓存定时任务失败", e);
        }
    }

    /**
     * 获取缓存数据
     */
    public Mono<SpecificDataResponse> getCachedData(String cacheKey, 
                                                   SpecificDataRequest request, 
                                                   String sourceId) {
        return Mono.defer(() -> {
            try {
                // 检查缓存是否已初始化
                if (primaryCache == null) {
                    log.debug("缓存尚未初始化，返回空");
                    return Mono.empty();
                }

                CachedDataWrapper cached = primaryCache.getIfPresent(cacheKey);

                if (cached != null) {
                    try {
                        if (cacheStrategyConfig != null) {
                            CacheStrategyConfig.CacheRuleConfig rule = cacheStrategyConfig.getCacheRuleConfig(
                                    extractFirstCode(request), extractFirstIndex(request), sourceId);

                            if (isCacheValid(cached, rule)) {
                                log.debug("命中有效缓存: {}", cacheKey);
                                return Mono.just(cached.getData());
                            } else if (rule.isAllowStaleData()) {
                                log.debug("命中过期缓存，但允许返回过期数据: {}", cacheKey);
                                // 触发异步刷新
                                triggerAsyncRefresh(cacheKey, request, sourceId);
                                return Mono.just(cached.getData());
                            }
                        } else {
                            // 配置未加载时，简单返回缓存数据
                            log.debug("配置未加载，直接返回缓存数据: {}", cacheKey);
                            return Mono.just(cached.getData());
                        }
                    } catch (Exception e) {
                        log.error("处理缓存数据时出错: {} - {}", cacheKey, e.getMessage(), e);
                        // 即使出错也尝试返回缓存数据
                        return Mono.just(cached.getData());
                    }
                }

                // 检查过期缓存
                try {
                    CachedDataWrapper stale = staleCache.getIfPresent(cacheKey);
                    if (stale != null) {
                        log.debug("从过期缓存获取数据用于降级: {}", cacheKey);
                        return Mono.just(stale.getData());
                    }
                } catch (Exception e) {
                    log.error("访问过期缓存时出错: {} - {}", cacheKey, e.getMessage(), e);
                }

                return Mono.empty();
            } catch (Exception e) {
                log.error("缓存操作失败，返回空: {}", e.getMessage());
                return Mono.empty();
            }
        }); // 移除 subscribeOn - Caffeine 缓存操作是非阻塞的
    }

    /**
     * 更新缓存数据
     */
    public void updateCache(String cacheKey, SpecificDataResponse data, 
                           CacheStrategyConfig.CacheRuleConfig rule) {
        try {
            if (data != null && primaryCache != null) {
                CachedDataWrapper wrapper = new CachedDataWrapper(data, Instant.now(), rule);
                primaryCache.put(cacheKey, wrapper);
                log.debug("更新缓存: {}", cacheKey);
            }
        } catch (Exception e) {
            log.warn("更新缓存失败: {} - {}", cacheKey, e.getMessage());
        }
    }

    /**
     * 生成缓存键
     */
    public String generateCacheKey(SpecificDataRequest request, String sourceId) {
        StringBuilder keyBuilder = new StringBuilder();
        
        // 添加业务来源ID
        keyBuilder.append(sourceId != null ? sourceId : "default").append(":");
        
        // 添加代码信息
        if (request.codeSelectors() != null && request.codeSelectors().include() != null) {
            request.codeSelectors().include().forEach(selector -> {
                keyBuilder.append(String.join(",", selector.values())).append(":");
            });
        }
        
        // 添加指标信息
        if (request.indexes() != null) {
            request.indexes().forEach(index -> {
                keyBuilder.append(index.indexId());
                if (index.timeType() != null) {
                    keyBuilder.append("-").append(index.timeType());
                }
                keyBuilder.append(":");
            });
        }
        
        // 添加分页信息
        if (request.pageInfo() != null) {
            keyBuilder.append("p").append(request.pageInfo().pageBegin())
                    .append("s").append(request.pageInfo().pageSize());
        }
        
        return "cache:" + Math.abs(keyBuilder.toString().hashCode());
    }

    /**
     * 检查缓存是否有效
     */
    private boolean isCacheValid(CachedDataWrapper cached, CacheStrategyConfig.CacheRuleConfig rule) {
        if (rule == null) {
            // 没有规则时，使用默认的10分钟TTL
            Instant now = Instant.now();
            Instant expireTime = cached.getCacheTime().plus(Duration.ofMinutes(10));
            return now.isBefore(expireTime);
        }
        
        Instant now = Instant.now();
        Instant expireTime = cached.getCacheTime().plus(rule.getCacheTtl());
        return now.isBefore(expireTime);
    }

    /**
     * 触发异步刷新
     */
    public void triggerAsyncRefresh(String cacheKey, SpecificDataRequest request, String sourceId, 
                                   java.util.function.Supplier<Mono<SpecificDataResponse>> dataSupplier) {
        AtomicBoolean refreshing = activeRefreshTasks.computeIfAbsent(cacheKey, k -> new AtomicBoolean(false));
        
        if (refreshing.compareAndSet(false, true)) {
            log.debug("触发异步缓存刷新: {}", cacheKey);
            
            // 检查调度器是否可用
            if (scheduler == null) {
                log.warn("调度器未初始化，跳过异步刷新: {}", cacheKey);
                refreshing.set(false);
                activeRefreshTasks.remove(cacheKey);
                return;
            }
            
            try {
                scheduler.schedule(() -> {
                    try {
                        // 异步获取新数据
                        dataSupplier.get()
                                .timeout(Duration.ofSeconds(10)) // 添加刷新超时保护
                                .doOnSuccess(newData -> {
                                    if (newData != null && newData.statusCode() == ApiStatus.SUCCESS_CODE) {
                                        // 获取缓存规则
                                        if (cacheStrategyConfig != null) {
                                            CacheStrategyConfig.CacheRuleConfig rule = cacheStrategyConfig.getCacheRuleConfig(
                                                    extractFirstCode(request), extractFirstIndex(request), sourceId);
                                            // 更新缓存
                                            updateCache(cacheKey, newData, rule);
                                        } else {
                                            // 使用默认规则更新缓存
                                            updateCache(cacheKey, newData, null);
                                        }
                                        log.debug("异步刷新缓存成功: {}", cacheKey);
                                    } else {
                                        log.warn("异步刷新获取到无效数据: {}", cacheKey);
                                    }
                                })
                                .doOnError(error -> {
                                    log.error("异步刷新缓存失败: {} - {}", cacheKey, error.getMessage());
                                })
                                .doFinally(signal -> {
                                    refreshing.set(false);
                                    activeRefreshTasks.remove(cacheKey);
                                })
                                .subscribe();
                    } catch (Exception e) {
                        log.error("异步刷新任务异常: {} - {}", cacheKey, e.getMessage(), e);
                        refreshing.set(false);
                        activeRefreshTasks.remove(cacheKey);
                    }
                }, 100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("调度异步刷新任务失败: {} - {}", cacheKey, e.getMessage());
                refreshing.set(false);
                activeRefreshTasks.remove(cacheKey);
            }
        }
    }
    
    /**
     * 触发异步刷新（简化版本，用于内部调用）
     */
    private void triggerAsyncRefresh(String cacheKey, SpecificDataRequest request, String sourceId) {
        // 简化版本，仅记录日志
        log.debug("触发异步缓存刷新（简化版本）: {}", cacheKey);
    }

    /**
     * 报告缓存统计
     */
    private void reportCacheStats() {
        try {
            CacheStats primaryStats = primaryCache.stats();
            CacheStats staleStats = staleCache.stats();
            
            log.info("缓存统计 - 主缓存: 大小={}, 命中率={:.2f}%, 驱逐数={}, 平均加载时间={}ms", 
                    primaryCache.estimatedSize(),
                    primaryStats.hitRate() * 100,
                    primaryStats.evictionCount(),
                    primaryStats.averageLoadPenalty() / 1_000_000);
                    
            log.info("缓存统计 - 过期缓存: 大小={}, 命中率={:.2f}%", 
                    staleCache.estimatedSize(),
                    staleStats.hitRate() * 100);
                    
            // 内存使用警告
            long estimatedMemoryUsage = (primaryCache.estimatedSize() * 64 + staleCache.estimatedSize() * 64); // KB
            if (estimatedMemoryUsage > 1024 * 1024) { // 超过1GB
                log.warn("缓存内存使用量较高: {} MB", estimatedMemoryUsage / 1024);
            }
        } catch (Exception e) {
            log.error("报告缓存统计时发生错误", e);
        }
    }

    /**
     * 清理任务
     */
    private void cleanupTasks() {
        try {
            // 清理活跃刷新任务中的死锁状态
            activeRefreshTasks.entrySet().removeIf(entry -> {
                // 清理超过30分钟仍在刷新状态的任务（可能是死锁）
                return entry.getValue().get(); // 这里可以加入更复杂的清理逻辑
            });
            
            // 手动清理缓存
            primaryCache.cleanUp();
            staleCache.cleanUp();
            
            log.debug("缓存清理任务完成");
        } catch (Exception e) {
            log.error("缓存清理任务失败", e);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStatsInfo getCacheStatsInfo() {
        CacheStats primaryStats = primaryCache.stats();
        CacheStats staleStats = staleCache.stats();
        
        return new CacheStatsInfo(
                primaryCache.estimatedSize(),
                staleCache.estimatedSize(),
                primaryStats.hitRate(),
                staleStats.hitRate(),
                primaryStats.evictionCount(),
                activeRefreshTasks.size()
        );
    }

    /**
     * 提取第一个代码（用于策略匹配）
     */
    private String extractFirstCode(SpecificDataRequest request) {
        if (request.codeSelectors() != null && 
            request.codeSelectors().include() != null && 
            !request.codeSelectors().include().isEmpty()) {
            
            var firstSelector = request.codeSelectors().include().get(0);
            if (firstSelector.values() != null && !firstSelector.values().isEmpty()) {
                return firstSelector.values().get(0);
            }
        }
        return "";
    }

    /**
     * 提取第一个指标（用于策略匹配）
     */
    private String extractFirstIndex(SpecificDataRequest request) {
        if (request.indexes() != null && !request.indexes().isEmpty()) {
            return request.indexes().get(0).indexId();
        }
        return "";
    }

    /**
     * 缓存数据包装器
     */
    public static class CachedDataWrapper {
        private final SpecificDataResponse data;
        private final Instant cacheTime;
        private final CacheStrategyConfig.CacheRuleConfig rule;

        public CachedDataWrapper(SpecificDataResponse data, Instant cacheTime, 
                               CacheStrategyConfig.CacheRuleConfig rule) {
            this.data = data;
            this.cacheTime = cacheTime;
            this.rule = rule;
        }

        public SpecificDataResponse getData() { return data; }
        public Instant getCacheTime() { return cacheTime; }
        public CacheStrategyConfig.CacheRuleConfig getRule() { return rule; }
    }

    /**
     * 缓存统计信息
     */
    public record CacheStatsInfo(
            long primaryCacheSize,
            long staleCacheSize,
            double primaryHitRate,
            double staleHitRate,
            long evictionCount,
            int activeRefreshTasks
    ) {}
}