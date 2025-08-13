package com.bililee.demo.fluxapi.service.impl;

import com.bililee.demo.fluxapi.cache.SpecificDataCacheManager;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.service.SpecificDataService;
import com.bililee.demo.fluxapi.strategy.cache.CacheStrategyExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;



/**
 * 特定数据服务实现类
 * 
 * <p>使用优化后的策略模式框架，支持多种缓存策略：</p>
 * <ul>
 *   <li>NO_CACHE: 直接透传到远程服务</li>
 *   <li>PASSIVE: 被动缓存，先查缓存后远程调用</li>
 *   <li>ACTIVE: 主动缓存，缓存优先+异步刷新</li>
 * </ul>
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Service
public class SpecificDataServiceImpl implements SpecificDataService {

    @Autowired
    private CacheStrategyConfig cacheStrategyConfig;

    @Autowired
    private SpecificDataCacheManager cacheManager;

    @Autowired
    private CacheStrategyExecutor strategyExecutor;

    @Override
    public Mono<SpecificDataResponse> querySpecificData(SpecificDataRequest request) {
        return querySpecificData(request, null);
    }

    /**
     * 查询特定数据（支持Source-Id）
     * 
     * <p>使用策略模式框架，根据配置的缓存策略自动选择最优的处理方式</p>
     */
    public Mono<SpecificDataResponse> querySpecificData(SpecificDataRequest request, String sourceId) {
        String cacheKey = cacheManager.generateCacheKey(request, sourceId);
        String firstCode = extractFirstCode(request);
        String firstIndex = extractFirstIndex(request);
        
        log.info("开始处理数据查询请求 - sourceId: {}, cacheKey: {}, codes: {}", 
                sourceId, cacheKey, firstCode);

        // 1. 获取缓存策略配置
        CacheStrategyConfig.CacheStrategy strategy = cacheStrategyConfig.getCacheStrategy(
                firstCode, firstIndex, sourceId);
        CacheStrategyConfig.CacheRuleConfig rule = cacheStrategyConfig.getCacheRuleConfig(
                firstCode, firstIndex, sourceId);

        log.debug("应用缓存策略: {} - sourceId: {}", strategy, sourceId);

        // 2. 使用策略模式框架执行缓存策略
        return strategyExecutor.execute(
                strategy,                           // 策略类型
                request,                           // 请求对象
                sourceId,                          // 源ID
                cacheKey,                          // 缓存键
                rule,                              // 缓存规则
                "/v1/specific_data",               // API路径
                SpecificDataRequest.class,         // 请求类型
                SpecificDataResponse.class         // 响应类型
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
}