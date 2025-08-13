package com.bililee.demo.fluxapi.strategy.cache.impl;

import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.strategy.cache.adapter.SpecificDataCacheOperations;
import com.bililee.demo.fluxapi.strategy.cache.adapter.SpecificDataRemoteServiceCaller;
import com.bililee.demo.fluxapi.strategy.cache.adapter.SpecificDataMonitoringService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SpecificData 专用缓存策略实现
 * 
 * <p>针对 SpecificData 业务场景优化的缓存策略实现</p>
 * 
 * @author bililee
 * @since 1.0.0
 */
public class SpecificDataCacheStrategies {
    
    /**
     * SpecificData 不缓存策略
     */
    @Slf4j
    @Component
    public static class SpecificDataNoCacheStrategy extends NoCacheStrategy<SpecificDataRequest, SpecificDataResponse> {
        
        @Autowired
        public SpecificDataNoCacheStrategy(
                SpecificDataCacheOperations cacheOperations,
                SpecificDataRemoteServiceCaller remoteServiceCaller,
                SpecificDataMonitoringService monitoringService) {
            setDependencies(cacheOperations, remoteServiceCaller, monitoringService);
        }
        
        @Override
        protected String extractFirstCode(SpecificDataRequest request) {
            return request.codeSelectors() != null && 
                   request.codeSelectors().include() != null && 
                   !request.codeSelectors().include().isEmpty() &&
                   request.codeSelectors().include().get(0).values() != null &&
                   !request.codeSelectors().include().get(0).values().isEmpty() ? 
                   request.codeSelectors().include().get(0).values().get(0) : "default";
        }
        
        @Override
        protected String extractFirstIndex(SpecificDataRequest request) {
            return request.indexes() != null && !request.indexes().isEmpty() ? 
                   request.indexes().get(0).indexId() : "default";
        }
    }
    
    /**
     * SpecificData 被动缓存策略
     */
    @Slf4j
    @Component
    public static class SpecificDataPassiveCacheStrategy extends PassiveCacheStrategy<SpecificDataRequest, SpecificDataResponse> {
        
        @Autowired
        public SpecificDataPassiveCacheStrategy(
                SpecificDataCacheOperations cacheOperations,
                SpecificDataRemoteServiceCaller remoteServiceCaller,
                SpecificDataMonitoringService monitoringService) {
            setDependencies(cacheOperations, remoteServiceCaller, monitoringService);
        }
        
        @Override
        protected String extractFirstCode(SpecificDataRequest request) {
            return request.codeSelectors() != null && 
                   request.codeSelectors().include() != null && 
                   !request.codeSelectors().include().isEmpty() &&
                   request.codeSelectors().include().get(0).values() != null &&
                   !request.codeSelectors().include().get(0).values().isEmpty() ? 
                   request.codeSelectors().include().get(0).values().get(0) : "default";
        }
        
        @Override
        protected String extractFirstIndex(SpecificDataRequest request) {
            return request.indexes() != null && !request.indexes().isEmpty() ? 
                   request.indexes().get(0).indexId() : "default";
        }
    }
    
    /**
     * SpecificData 主动缓存策略
     */
    @Slf4j
    @Component
    public static class SpecificDataActiveCacheStrategy extends ActiveCacheStrategy<SpecificDataRequest, SpecificDataResponse> {
        
        @Autowired
        public SpecificDataActiveCacheStrategy(
                SpecificDataCacheOperations cacheOperations,
                SpecificDataRemoteServiceCaller remoteServiceCaller,
                SpecificDataMonitoringService monitoringService) {
            setDependencies(cacheOperations, remoteServiceCaller, monitoringService);
        }
        
        @Override
        protected String extractFirstCode(SpecificDataRequest request) {
            return request.codeSelectors() != null && 
                   request.codeSelectors().include() != null && 
                   !request.codeSelectors().include().isEmpty() &&
                   request.codeSelectors().include().get(0).values() != null &&
                   !request.codeSelectors().include().get(0).values().isEmpty() ? 
                   request.codeSelectors().include().get(0).values().get(0) : "default";
        }
        
        @Override
        protected String extractFirstIndex(SpecificDataRequest request) {
            return request.indexes() != null && !request.indexes().isEmpty() ? 
                   request.indexes().get(0).indexId() : "default";
        }
    }
}
