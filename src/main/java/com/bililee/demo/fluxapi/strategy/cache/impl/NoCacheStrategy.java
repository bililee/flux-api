package com.bililee.demo.fluxapi.strategy.cache.impl;

import com.bililee.demo.fluxapi.strategy.cache.AbstractCacheStrategy;
import com.bililee.demo.fluxapi.strategy.cache.CacheStrategyContext;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 不缓存策略 - 直接透传到远程服务
 * 
 * <p>此策略直接将请求透传到远程服务，不进行任何缓存操作。
 * 适用于对数据实时性要求极高的场景。</p>
 * 
 * @param <TRequest> 请求类型
 * @param <TResponse> 响应类型
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Component
public class NoCacheStrategy<TRequest, TResponse> extends AbstractCacheStrategy<TRequest, TResponse> {
    
    private static final String STRATEGY_NAME = "NO_CACHE";
    
    @Override
    protected Mono<TResponse> doExecute(CacheStrategyContext<TRequest, TResponse> context) {
        log.debug("执行直接透传策略 - sourceId: {}", context.getSourceId());
        
        // 记录缓存未命中（因为不使用缓存）
        recordCacheMiss(context);
        
        // 直接调用远程服务
        return remoteServiceCaller.callRemoteService(context.getRequest(), context.getSourceId())
                .doOnSuccess(response -> {
                    log.info("直接透传完成 - sourceId: {}", context.getSourceId());
                })
                .doOnError(error -> {
                    log.error("直接透传失败 - sourceId: {}, error: {}", 
                            context.getSourceId(), error.getMessage());
                });
    }
    
    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }
    
    @Override
    public boolean supports(Object strategyType) {
        if (strategyType instanceof CacheStrategyConfig.CacheStrategy) {
            return strategyType == CacheStrategyConfig.CacheStrategy.NO_CACHE;
        }
        return STRATEGY_NAME.equals(String.valueOf(strategyType));
    }
}
