package com.bililee.demo.fluxapi.strategy.cache.impl;

import com.bililee.demo.fluxapi.strategy.cache.AbstractCacheStrategy;
import com.bililee.demo.fluxapi.strategy.cache.CacheStrategyContext;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 主动缓存策略 - 缓存优先，异步刷新
 * 
 * <p>此策略优先返回缓存数据（即使过期），同时在后台异步刷新缓存。
 * 适用于对响应时间要求极高，可以容忍轻微数据延迟的场景。</p>
 * 
 * @param <TRequest> 请求类型
 * @param <TResponse> 响应类型
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Component
public class ActiveCacheStrategy<TRequest, TResponse> extends AbstractCacheStrategy<TRequest, TResponse> {
    
    private static final String STRATEGY_NAME = "ACTIVE_CACHE";
    
    @Override
    protected Mono<TResponse> doExecute(CacheStrategyContext<TRequest, TResponse> context) {
        log.debug("执行主动缓存策略 - sourceId: {}, cacheKey: {}", 
                context.getSourceId(), context.getCacheKey());
        
        // 1. 尝试从缓存获取数据（包括过期数据）
        return cacheOperations.getCachedData(context.getCacheKey(), context.getRequest(), context.getSourceId())
                .map(cachedData -> {
                    // 缓存命中（可能是过期数据）
                    recordCacheHit(context);
                    log.debug("主动缓存命中 - sourceId: {}, cacheKey: {}", 
                            context.getSourceId(), context.getCacheKey());
                    
                    // 触发异步刷新
                    triggerAsyncRefresh(context);
                    
                    return cachedData;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 2. 缓存完全未命中，执行同步远程调用
                    recordCacheMiss(context);
                    log.debug("主动缓存未命中 - sourceId: {}, cacheKey: {}", 
                            context.getSourceId(), context.getCacheKey());
                    
                    return executeRemoteCallWithCache(context);
                }));
    }
    
    /**
     * 触发异步缓存刷新
     */
    private void triggerAsyncRefresh(CacheStrategyContext<TRequest, TResponse> context) {
        try {
            cacheOperations.asyncRefresh(context.getCacheKey(), context.getRequest(), context.getSourceId());
            log.debug("主动缓存异步刷新已触发 - sourceId: {}, cacheKey: {}", 
                    context.getSourceId(), context.getCacheKey());
        } catch (Exception e) {
            log.warn("主动缓存异步刷新触发失败 - sourceId: {}, cacheKey: {}, error: {}", 
                    context.getSourceId(), context.getCacheKey(), e.getMessage());
        }
    }
    
    /**
     * 执行远程调用并更新缓存
     */
    private Mono<TResponse> executeRemoteCallWithCache(CacheStrategyContext<TRequest, TResponse> context) {
        return remoteServiceCaller.callRemoteService(context.getRequest(), context.getSourceId())
                .doOnSuccess(response -> {
                    // 更新缓存
                    if (response != null) {
                        cacheOperations.updateCache(context.getCacheKey(), response, context.getCacheRule());
                        log.debug("主动缓存更新成功 - sourceId: {}, cacheKey: {}", 
                                context.getSourceId(), context.getCacheKey());
                    }
                })
                .doOnError(error -> {
                    log.error("主动缓存远程调用失败 - sourceId: {}, cacheKey: {}, error: {}", 
                            context.getSourceId(), context.getCacheKey(), error.getMessage());
                });
    }
    
    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }
    
    @Override
    public boolean supports(Object strategyType) {
        if (strategyType instanceof CacheStrategyConfig.CacheStrategy) {
            return strategyType == CacheStrategyConfig.CacheStrategy.ACTIVE;
        }
        return STRATEGY_NAME.equals(String.valueOf(strategyType)) || 
               "ACTIVE".equals(String.valueOf(strategyType));
    }
}
