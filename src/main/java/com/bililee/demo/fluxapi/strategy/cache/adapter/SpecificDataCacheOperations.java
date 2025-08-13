package com.bililee.demo.fluxapi.strategy.cache.adapter;

import com.bililee.demo.fluxapi.cache.SpecificDataCacheManager;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.strategy.cache.CacheOperations;
import com.bililee.demo.fluxapi.strategy.cache.RemoteServiceCaller;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * SpecificData 缓存操作适配器
 * 
 * <p>将现有的 SpecificDataCacheManager 适配到新的策略模式框架中</p>
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Component
public class SpecificDataCacheOperations implements CacheOperations<SpecificDataRequest, SpecificDataResponse> {
    
    @Autowired
    private SpecificDataCacheManager cacheManager;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Override
    public Mono<SpecificDataResponse> getCachedData(String cacheKey, SpecificDataRequest request, String sourceId) {
        return cacheManager.getCachedData(cacheKey, request, sourceId);
    }
    
    @Override
    public void updateCache(String cacheKey, SpecificDataResponse data, Object cacheRule) {
        if (cacheRule instanceof CacheStrategyConfig.CacheRuleConfig rule) {
            cacheManager.updateCache(cacheKey, data, rule);
        } else {
            log.warn("无效的缓存规则类型: {}, 跳过缓存更新", cacheRule != null ? cacheRule.getClass() : "null");
        }
    }
    
    @Override
    public void asyncRefresh(String cacheKey, SpecificDataRequest request, String sourceId) {
        // 触发异步刷新 - 使用实际的远程服务调用
        try {
            RemoteServiceCaller<SpecificDataRequest, SpecificDataResponse> remoteServiceCaller = 
                    applicationContext.getBean(SpecificDataRemoteServiceCaller.class);
            
            cacheManager.triggerAsyncRefresh(cacheKey, request, sourceId, () -> {
                log.debug("异步刷新回调触发，执行远程调用 - cacheKey: {}, sourceId: {}", cacheKey, sourceId);
                return remoteServiceCaller.callRemoteService(request, sourceId)
                        .doOnError(error -> log.warn("异步刷新远程调用失败 - cacheKey: {}, error: {}", 
                                cacheKey, error.getMessage()))
                        .onErrorReturn(SpecificDataResponse.remoteServiceError());
            });
            log.debug("异步刷新触发 - cacheKey: {}, sourceId: {}", cacheKey, sourceId);
        } catch (Exception e) {
            log.error("异步刷新触发失败 - cacheKey: {}, sourceId: {}, error: {}", 
                    cacheKey, sourceId, e.getMessage());
        }
    }
}
