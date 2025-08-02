package com.bililee.demo.fluxapi.service.impl;

import com.bililee.demo.fluxapi.cache.RequestDeduplicationManager;
import com.bililee.demo.fluxapi.cache.SpecificDataCacheManager;
import com.bililee.demo.fluxapi.client.RemoteSpecificDataClient;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.monitoring.CacheMonitoringService;
import com.bililee.demo.fluxapi.service.SpecificDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * 特定数据服务实现类
 * 智能缓存层，支持主动/被动缓存、请求去重、降级策略
 */
@Slf4j
@Service
public class SpecificDataServiceImpl implements SpecificDataService {

    @Autowired
    private CacheStrategyConfig cacheStrategyConfig;

    @Autowired
    private SpecificDataCacheManager cacheManager;

    @Autowired
    private RequestDeduplicationManager deduplicationManager;

    @Autowired
    private RemoteSpecificDataClient remoteClient;

    @Autowired
    private CacheMonitoringService monitoringService;

    @Override
    public Mono<SpecificDataResponse> querySpecificData(SpecificDataRequest request) {
        return querySpecificData(request, null);
    }

    /**
     * 查询特定数据（支持Source-Id）
     */
    public Mono<SpecificDataResponse> querySpecificData(SpecificDataRequest request, String sourceId) {
        Instant startTime = Instant.now();
        String cacheKey = cacheManager.generateCacheKey(request, sourceId);
        String firstCode = extractFirstCode(request);
        String firstIndex = extractFirstIndex(request);
        
        log.info("开始处理数据查询请求 - sourceId: {}, cacheKey: {}, codes: {}", 
                sourceId, cacheKey, firstCode);

        // 1. 获取缓存策略
        CacheStrategyConfig.CacheStrategy strategy = cacheStrategyConfig.getCacheStrategy(
                firstCode, firstIndex, sourceId);
        CacheStrategyConfig.CacheRuleConfig rule = cacheStrategyConfig.getCacheRuleConfig(
                firstCode, firstIndex, sourceId);

        log.debug("应用缓存策略: {} - sourceId: {}", strategy, sourceId);

        return switch (strategy) {
            case NO_CACHE -> handleNoCacheStrategy(request, sourceId, startTime);
            case PASSIVE -> handlePassiveCacheStrategy(request, sourceId, cacheKey, rule, startTime);
            case ACTIVE -> handleActiveCacheStrategy(request, sourceId, cacheKey, rule, startTime);
        };
    }

    /**
     * 处理不缓存策略 - 直接透传
     */
    private Mono<SpecificDataResponse> handleNoCacheStrategy(
            SpecificDataRequest request, String sourceId, Instant startTime) {
        
        log.debug("执行直接透传策略 - sourceId: {}", sourceId);
        monitoringService.recordCacheMiss(sourceId, "NO_CACHE");

        return deduplicationManager.executeDeduplicatedRequest(
                request, sourceId,
                () -> callRemoteService(request, sourceId))
                .doOnSuccess(response -> {
                    long duration = Duration.between(startTime, Instant.now()).toMillis();
                    monitoringService.recordApiResponseTime(sourceId, "/v1/specific_data", duration);
                    log.info("直接透传完成 - sourceId: {}, duration: {}ms", sourceId, duration);
                })
                .doOnError(error -> {
                    long duration = Duration.between(startTime, Instant.now()).toMillis();
                    monitoringService.recordBusinessError(sourceId, "remote_call_failed", error.getClass().getSimpleName());
                    log.error("直接透传失败 - sourceId: {}, duration: {}ms, error: {}", 
                            sourceId, duration, error.getMessage());
                });
    }

    /**
     * 处理被动缓存策略 - 先查缓存，未命中则远程调用
     */
    private Mono<SpecificDataResponse> handlePassiveCacheStrategy(
            SpecificDataRequest request, String sourceId, String cacheKey,
            CacheStrategyConfig.CacheRuleConfig rule, Instant startTime) {
        
        log.debug("执行被动缓存策略 - sourceId: {}, cacheKey: {}", sourceId, cacheKey);

        return cacheManager.getCachedData(cacheKey, request, sourceId)
                .flatMap(cachedData -> {
                    if (cachedData != null) {
                        // 缓存命中
                        log.debug("被动缓存命中 - sourceId: {}, cacheKey: {}", sourceId, cacheKey);
                        monitoringService.recordCacheHit(sourceId, "PASSIVE");
                        
                        long duration = Duration.between(startTime, Instant.now()).toMillis();
                        monitoringService.recordApiResponseTime(sourceId, "/v1/specific_data", duration);
                        
                        return Mono.just(cachedData);
                    } else {
                        // 缓存未命中，执行远程调用
                        log.debug("被动缓存未命中 - sourceId: {}, cacheKey: {}", sourceId, cacheKey);
                        monitoringService.recordCacheMiss(sourceId, "PASSIVE");
                        
                        return executeRemoteCallWithCache(request, sourceId, cacheKey, rule, startTime);
                    }
                });
    }

    /**
     * 处理主动缓存策略 - 缓存优先，异步刷新
     */
    private Mono<SpecificDataResponse> handleActiveCacheStrategy(
            SpecificDataRequest request, String sourceId, String cacheKey,
            CacheStrategyConfig.CacheRuleConfig rule, Instant startTime) {
        
        log.debug("执行主动缓存策略 - sourceId: {}, cacheKey: {}", sourceId, cacheKey);

        return cacheManager.getCachedData(cacheKey, request, sourceId)
                .flatMap(cachedData -> {
                    if (cachedData != null) {
                        // 缓存命中
                        log.debug("主动缓存命中 - sourceId: {}, cacheKey: {}", sourceId, cacheKey);
                        monitoringService.recordCacheHit(sourceId, "ACTIVE");
                        
                        long duration = Duration.between(startTime, Instant.now()).toMillis();
                        monitoringService.recordApiResponseTime(sourceId, "/v1/specific_data", duration);
                        
                        return Mono.just(cachedData);
                    } else {
                        // 缓存未命中，需要同步获取数据
                        log.debug("主动缓存未命中，同步获取数据 - sourceId: {}, cacheKey: {}", sourceId, cacheKey);
                        monitoringService.recordCacheMiss(sourceId, "ACTIVE");
                        
                        return executeRemoteCallWithCache(request, sourceId, cacheKey, rule, startTime);
                    }
                });
    }

    /**
     * 执行远程调用并更新缓存
     */
    private Mono<SpecificDataResponse> executeRemoteCallWithCache(
            SpecificDataRequest request, String sourceId, String cacheKey,
            CacheStrategyConfig.CacheRuleConfig rule, Instant startTime) {
        
        return deduplicationManager.executeDeduplicatedRequest(
                request, sourceId,
                () -> callRemoteService(request, sourceId))
                .doOnSuccess(response -> {
                    // 更新缓存
                    if (response != null && response.statusCode() == 0) {
                        cacheManager.updateCache(cacheKey, response, rule);
                        monitoringService.recordCacheRefresh(sourceId, "passive", true);
                        log.debug("缓存更新成功 - sourceId: {}, cacheKey: {}", sourceId, cacheKey);
                    }
                    
                    long duration = Duration.between(startTime, Instant.now()).toMillis();
                    monitoringService.recordApiResponseTime(sourceId, "/v1/specific_data", duration);
                })
                .doOnError(error -> {
                    monitoringService.recordCacheRefresh(sourceId, "passive", false);
                    log.error("远程调用失败，尝试降级 - sourceId: {}, cacheKey: {}, error: {}", 
                            sourceId, cacheKey, error.getMessage());
                })
                .onErrorResume(error -> handleFallback(request, sourceId, cacheKey, error, startTime));
    }

    /**
     * 调用远程服务
     */
    private Mono<SpecificDataResponse> callRemoteService(SpecificDataRequest request, String sourceId) {
        Instant remoteStartTime = Instant.now();
        
        return remoteClient.fetchSpecificData(request)
                .timeout(Duration.ofSeconds(10)) // 10秒超时保护
                .doOnSuccess(response -> {
                    long duration = Duration.between(remoteStartTime, Instant.now()).toMillis();
                    monitoringService.recordRemoteCall(sourceId, duration, true);
                    log.debug("远程服务调用成功 - sourceId: {}, duration: {}ms", sourceId, duration);
                })
                .doOnError(error -> {
                    long duration = Duration.between(remoteStartTime, Instant.now()).toMillis();
                    monitoringService.recordRemoteCall(sourceId, duration, false);
                    log.error("远程服务调用失败 - sourceId: {}, duration: {}ms, error: {}", 
                            sourceId, duration, error.getMessage());
                });
    }

    /**
     * 处理降级逻辑
     */
    private Mono<SpecificDataResponse> handleFallback(
            SpecificDataRequest request, String sourceId, String cacheKey,
            Throwable error, Instant startTime) {
        
        log.warn("进入降级处理 - sourceId: {}, cacheKey: {}, error: {}", 
                sourceId, cacheKey, error.getMessage());

        // 1. 尝试返回过期缓存数据
        return cacheManager.getCachedData(cacheKey, request, sourceId)
                .flatMap(staleData -> {
                    if (staleData != null) {
                        log.info("降级: 返回过期缓存数据 - sourceId: {}, cacheKey: {}", sourceId, cacheKey);
                        monitoringService.recordFallback(sourceId, "stale_cache");
                        
                        long duration = Duration.between(startTime, Instant.now()).toMillis();
                        monitoringService.recordApiResponseTime(sourceId, "/v1/specific_data", duration);
                        
                        return Mono.just(staleData);
                    } else {
                        // 2. 返回默认错误响应
                        log.warn("降级: 返回错误响应 - sourceId: {}, cacheKey: {}", sourceId, cacheKey);
                        monitoringService.recordFallback(sourceId, "error_response");
                        
                        return Mono.just(SpecificDataResponse.error(
                                500, 
                                "服务暂时不可用，请稍后重试"));
                    }
                });
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
}