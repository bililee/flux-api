package com.bililee.demo.fluxapi.strategy.cache;

import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;



/**
 * 策略模式使用示例
 * 
 * <p>展示如何使用新的缓存策略框架来处理不同的业务场景</p>
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Service
public class StrategyPatternUsageExample {
    
    @Autowired
    private CacheStrategyExecutor strategyExecutor;
    
    /**
     * 使用新策略模式处理 SpecificData 请求的示例
     */
    public Mono<SpecificDataResponse> handleSpecificDataRequest(
            SpecificDataRequest request, 
            String sourceId, 
            CacheStrategyConfig.CacheStrategy strategy) {
        
        // 1. 构建缓存键（这里简化处理）
        String cacheKey = generateCacheKey(request, sourceId);
        
        // 2. 获取缓存规则（这里简化处理）
        Object cacheRule = getCacheRule(request, sourceId);
        
        // 3. 使用策略执行器执行策略
        return strategyExecutor.execute(
                strategy,                           // 策略类型
                request,                           // 请求对象
                sourceId,                          // 源ID
                cacheKey,                          // 缓存键
                cacheRule,                         // 缓存规则
                "/v1/specific_data",               // API路径
                SpecificDataRequest.class,         // 请求类型
                SpecificDataResponse.class         // 响应类型
        );
    }
    
    /**
     * 扩展示例：处理新的业务接口（如用户数据）
     * 
     * <p>展示如何为新接口扩展策略模式</p>
     */
    public <TRequest, TResponse> Mono<TResponse> handleGenericRequest(
            Object strategyType,
            TRequest request,
            String sourceId,
            String apiPath,
            Class<TRequest> requestType,
            Class<TResponse> responseType) {
        
        // 1. 生成缓存键
        String cacheKey = generateGenericCacheKey(request, sourceId);
        
        // 2. 获取缓存规则
        Object cacheRule = getGenericCacheRule(requestType, sourceId);
        
        // 3. 执行策略
        return strategyExecutor.execute(
                strategyType,
                request,
                sourceId,
                cacheKey,
                cacheRule,
                apiPath,
                requestType,
                responseType
        );
    }
    
    /**
     * 批量处理示例：同时处理多个不同策略的请求
     */
    public Mono<java.util.List<SpecificDataResponse>> handleBatchRequests(
            java.util.List<BatchRequestItem> requests) {
        
        return reactor.core.publisher.Flux.fromIterable(requests)
                .flatMap(item -> handleSpecificDataRequest(
                        item.getRequest(), 
                        item.getSourceId(), 
                        item.getStrategy())
                        .onErrorResume(error -> {
                            log.error("批量请求处理失败 - sourceId: {}, error: {}", 
                                    item.getSourceId(), error.getMessage());
                            return Mono.just(SpecificDataResponse.internalServerError());
                        })
                )
                .collectList();
    }
    
    // 辅助方法
    private String generateCacheKey(SpecificDataRequest request, String sourceId) {
        // 简化的缓存键生成逻辑
        return String.format("specific_data:%s:%s", sourceId, request.hashCode());
    }
    
    private Object getCacheRule(SpecificDataRequest request, String sourceId) {
        // 简化的缓存规则获取逻辑
        return new Object(); // 实际应该返回 CacheRuleConfig
    }
    
    private <T> String generateGenericCacheKey(T request, String sourceId) {
        return String.format("generic:%s:%s:%s", 
                request.getClass().getSimpleName(), sourceId, request.hashCode());
    }
    
    private <T> Object getGenericCacheRule(Class<T> requestType, String sourceId) {
        // 根据请求类型和源ID获取对应的缓存规则
        return new Object();
    }
    
    /**
     * 批量请求项
     */
    public static class BatchRequestItem {
        private SpecificDataRequest request;
        private String sourceId;
        private CacheStrategyConfig.CacheStrategy strategy;
        
        // getters and setters
        public SpecificDataRequest getRequest() { return request; }
        public void setRequest(SpecificDataRequest request) { this.request = request; }
        
        public String getSourceId() { return sourceId; }
        public void setSourceId(String sourceId) { this.sourceId = sourceId; }
        
        public CacheStrategyConfig.CacheStrategy getStrategy() { return strategy; }
        public void setStrategy(CacheStrategyConfig.CacheStrategy strategy) { this.strategy = strategy; }
    }
}
