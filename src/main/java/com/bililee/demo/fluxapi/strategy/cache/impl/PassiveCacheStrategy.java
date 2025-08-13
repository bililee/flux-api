package com.bililee.demo.fluxapi.strategy.cache.impl;

import com.bililee.demo.fluxapi.strategy.cache.AbstractCacheStrategy;
import com.bililee.demo.fluxapi.strategy.cache.CacheStrategyContext;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 被动缓存策略 - 先查缓存，未命中则远程调用
 * 
 * <p>此策略优先从缓存中获取数据，只有在缓存未命中时才调用远程服务。
 * 远程调用成功后会更新缓存，适用于对性能要求较高的场景。</p>
 * 
 * @param <TRequest> 请求类型
 * @param <TResponse> 响应类型
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Component
public class PassiveCacheStrategy<TRequest, TResponse> extends AbstractCacheStrategy<TRequest, TResponse> {
    
    private static final String STRATEGY_NAME = "PASSIVE_CACHE";
    
    @Override
    protected Mono<TResponse> doExecute(CacheStrategyContext<TRequest, TResponse> context) {
        log.debug("执行被动缓存策略 - sourceId: {}, cacheKey: {}", 
                context.getSourceId(), context.getCacheKey());
        
        // 1. 尝试从缓存获取数据
        return cacheOperations.getCachedData(context.getCacheKey(), context.getRequest(), context.getSourceId())
                .map(cachedData -> {
                    // 缓存命中
                    recordCacheHit(context);
                    log.debug("被动缓存命中 - sourceId: {}, cacheKey: {}", 
                            context.getSourceId(), context.getCacheKey());
                    return cachedData;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 2. 缓存未命中，执行远程调用
                    recordCacheMiss(context);
                    log.debug("被动缓存未命中 - sourceId: {}, cacheKey: {}", 
                            context.getSourceId(), context.getCacheKey());
                    
                    return executeRemoteCallWithCache(context);
                }));
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
                        log.debug("被动缓存更新成功 - sourceId: {}, cacheKey: {}", 
                                context.getSourceId(), context.getCacheKey());
                    }
                })
                .doOnError(error -> {
                    log.error("被动缓存远程调用失败 - sourceId: {}, cacheKey: {}, error: {}", 
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
            return strategyType == CacheStrategyConfig.CacheStrategy.PASSIVE;
        }
        return STRATEGY_NAME.equals(String.valueOf(strategyType)) || 
               "PASSIVE".equals(String.valueOf(strategyType));
    }
}
