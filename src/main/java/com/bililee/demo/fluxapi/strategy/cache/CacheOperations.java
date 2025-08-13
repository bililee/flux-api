package com.bililee.demo.fluxapi.strategy.cache;

import reactor.core.publisher.Mono;

/**
 * 缓存操作接口
 * 
 * <p>定义了缓存策略中需要的基本缓存操作</p>
 * 
 * @param <TRequest> 请求类型
 * @param <TResponse> 响应类型
 * 
 * @author bililee
 * @since 1.0.0
 */
public interface CacheOperations<TRequest, TResponse> {
    
    /**
     * 获取缓存数据
     * 
     * @param cacheKey 缓存键
     * @param request 原始请求
     * @param sourceId 源标识ID
     * @return 缓存的响应数据
     */
    Mono<TResponse> getCachedData(String cacheKey, TRequest request, String sourceId);
    
    /**
     * 更新缓存
     * 
     * @param cacheKey 缓存键
     * @param data 响应数据
     * @param cacheRule 缓存规则
     */
    void updateCache(String cacheKey, TResponse data, Object cacheRule);
    
    /**
     * 异步刷新缓存
     * 
     * @param cacheKey 缓存键
     * @param request 原始请求
     * @param sourceId 源标识ID
     */
    void asyncRefresh(String cacheKey, TRequest request, String sourceId);
}
