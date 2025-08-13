package com.bililee.demo.fluxapi.strategy.cache;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * 缓存策略抽象基类
 * 
 * <p>提供缓存策略的通用功能，包括监控、日志记录、性能统计等</p>
 * 
 * @param <TRequest> 请求类型
 * @param <TResponse> 响应类型
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractCacheStrategy<TRequest, TResponse> implements CacheStrategy<TRequest, TResponse> {
    
    // 注意：这些依赖需要通过具体实现类注入，而不是在抽象类中注入
    // 因为泛型擦除的问题，Spring无法正确注入泛型接口
    protected CacheOperations<TRequest, TResponse> cacheOperations;
    protected RemoteServiceCaller<TRequest, TResponse> remoteServiceCaller;
    protected MonitoringService monitoringService;
    
    /**
     * 设置依赖项（由子类在构造时调用）
     */
    protected void setDependencies(
            CacheOperations<TRequest, TResponse> cacheOperations,
            RemoteServiceCaller<TRequest, TResponse> remoteServiceCaller,
            MonitoringService monitoringService) {
        this.cacheOperations = cacheOperations;
        this.remoteServiceCaller = remoteServiceCaller;
        this.monitoringService = monitoringService;
    }
    
    @Override
    public final Mono<TResponse> execute(CacheStrategyContext<TRequest, TResponse> context) {
        log.debug("开始执行缓存策略: {} - sourceId: {}, cacheKey: {}", 
                getStrategyName(), context.getSourceId(), context.getCacheKey());
        
        return doExecute(context)
                .doOnSuccess(response -> recordSuccess(context, response))
                .doOnError(error -> recordError(context, error))
                .doFinally(signalType -> recordCompletion(context, signalType));
    }
    
    /**
     * 子类实现具体的策略逻辑
     */
    protected abstract Mono<TResponse> doExecute(CacheStrategyContext<TRequest, TResponse> context);
    
    /**
     * 记录成功执行
     */
    protected void recordSuccess(CacheStrategyContext<TRequest, TResponse> context, TResponse response) {
        long duration = Duration.between(context.getStartTime(), Instant.now()).toMillis();
        monitoringService.recordApiResponseTime(context.getSourceId(), context.getApiPath(), duration);
        
        log.info("策略执行成功: {} - sourceId: {}, duration: {}ms", 
                getStrategyName(), context.getSourceId(), duration);
    }
    
    /**
     * 记录错误执行
     */
    protected void recordError(CacheStrategyContext<TRequest, TResponse> context, Throwable error) {
        long duration = Duration.between(context.getStartTime(), Instant.now()).toMillis();
        monitoringService.recordBusinessError(context.getSourceId(), 
                getStrategyName().toLowerCase() + "_failed", error.getClass().getSimpleName());
        
        log.error("策略执行失败: {} - sourceId: {}, duration: {}ms, error: {}", 
                getStrategyName(), context.getSourceId(), duration, error.getMessage());
    }
    
    /**
     * 记录执行完成
     */
    protected void recordCompletion(CacheStrategyContext<TRequest, TResponse> context, 
                                   reactor.core.publisher.SignalType signalType) {
        log.debug("策略执行完成: {} - sourceId: {}, signalType: {}", 
                getStrategyName(), context.getSourceId(), signalType);
    }
    
    /**
     * 记录缓存命中
     */
    protected void recordCacheHit(CacheStrategyContext<TRequest, TResponse> context) {
        monitoringService.recordCacheHit(context.getSourceId(), getStrategyName());
        log.debug("缓存命中: {} - sourceId: {}, cacheKey: {}", 
                getStrategyName(), context.getSourceId(), context.getCacheKey());
    }
    
    /**
     * 记录缓存未命中
     */
    protected void recordCacheMiss(CacheStrategyContext<TRequest, TResponse> context) {
        monitoringService.recordCacheMiss(context.getSourceId(), getStrategyName());
        log.debug("缓存未命中: {} - sourceId: {}, cacheKey: {}", 
                getStrategyName(), context.getSourceId(), context.getCacheKey());
    }
    
    /**
     * 提取第一个代码（用于策略匹配）
     */
    protected String extractFirstCode(TRequest request) {
        // 子类可以重写此方法以适应不同的请求类型
        return "default";
    }
    
    /**
     * 提取第一个索引（用于策略匹配）
     */
    protected String extractFirstIndex(TRequest request) {
        // 子类可以重写此方法以适应不同的请求类型
        return "default";
    }
}


