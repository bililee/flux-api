package com.bililee.demo.fluxapi.strategy.cache;

import com.bililee.demo.fluxapi.cache.RequestDeduplicationManager;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * 缓存策略执行器
 * 
 * <p>提供统一的策略执行入口，负责策略选择、上下文构建和执行监控。</p>
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Component
public class CacheStrategyExecutor {
    
    @Autowired
    private CacheStrategyFactory strategyFactory;
    
    @Autowired
    private RequestDeduplicationManager deduplicationManager;
    
    /**
     * 执行缓存策略
     * 
     * @param strategyType 策略类型
     * @param request 请求对象
     * @param sourceId 源标识ID
     * @param cacheKey 缓存键
     * @param cacheRule 缓存规则
     * @param apiPath API路径
     * @param extraParams 扩展参数
     * @param requestType 请求类型
     * @param responseType 响应类型
     * @return 响应数据的 Mono 流
     */
    public <TRequest, TResponse> Mono<TResponse> execute(
            Object strategyType,
            TRequest request,
            String sourceId,
            String cacheKey,
            Object cacheRule,
            String apiPath,
            Map<String, Object> extraParams,
            Class<TRequest> requestType,
            Class<TResponse> responseType) {
        
        try {
            // 1. 获取策略实例
            CacheStrategy<TRequest, TResponse> strategy = strategyFactory.getStrategy(
                    strategyType, requestType, responseType);
            
            // 2. 构建执行上下文
            CacheStrategyContext<TRequest, TResponse> context = CacheStrategyContext.<TRequest, TResponse>builder()
                    .request(request)
                    .sourceId(sourceId)
                    .cacheKey(cacheKey)
                    .cacheRule(cacheRule)
                    .startTime(Instant.now())
                    .apiPath(apiPath)
                    .strategyName(strategy.getStrategyName())
                    .extraParams(extraParams)
                    .build();
            
            // 验证上下文
            context.validate();
            
            log.debug("开始执行缓存策略: {} - sourceId: {}", strategy.getStrategyName(), sourceId);
            
            // 3. 执行策略（对于 SpecificData 请求使用去重管理器）
            if (request instanceof SpecificDataRequest specificDataRequest) {
                return (Mono<TResponse>) deduplicationManager.executeDeduplicatedRequest(
                        specificDataRequest, sourceId,
                        () -> (Mono<SpecificDataResponse>) strategy.execute(context)
                );
            } else {
                // 对于其他类型的请求，直接执行策略
                return strategy.execute(context);
            }
            
        } catch (Exception e) {
            log.error("缓存策略执行失败 - strategyType: {}, sourceId: {}, error: {}", 
                    strategyType, sourceId, e.getMessage(), e);
            return Mono.error(e);
        }
    }
    
    /**
     * 简化的执行方法（无扩展参数）
     */
    public <TRequest, TResponse> Mono<TResponse> execute(
            Object strategyType,
            TRequest request,
            String sourceId,
            String cacheKey,
            Object cacheRule,
            String apiPath,
            Class<TRequest> requestType,
            Class<TResponse> responseType) {
        
        return execute(strategyType, request, sourceId, cacheKey, cacheRule, 
                      apiPath, null, requestType, responseType);
    }
    
    /**
     * 检查策略是否可用
     */
    public boolean isStrategyAvailable(Object strategyType) {
        return strategyFactory.supportsStrategy(strategyType);
    }
    
    /**
     * 获取所有可用策略
     */
    public java.util.List<String> getAvailableStrategies() {
        return strategyFactory.getAvailableStrategyNames();
    }
}
