package com.bililee.demo.fluxapi.strategy.cache.adapter;

import com.bililee.demo.fluxapi.monitoring.CacheMonitoringService;
import com.bililee.demo.fluxapi.strategy.cache.MonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SpecificData 监控服务适配器
 * 
 * <p>将现有的 CacheMonitoringService 适配到新的策略模式框架中</p>
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Component
public class SpecificDataMonitoringService implements MonitoringService {
    
    @Autowired
    private CacheMonitoringService monitoringService;
    
    @Override
    public void recordApiResponseTime(String sourceId, String apiPath, long duration) {
        monitoringService.recordApiResponseTime(sourceId, apiPath, duration);
    }
    
    @Override
    public void recordBusinessError(String sourceId, String errorType, String errorDetail) {
        monitoringService.recordBusinessError(sourceId, errorType, errorDetail);
    }
    
    @Override
    public void recordCacheHit(String sourceId, String strategy) {
        monitoringService.recordCacheHit(sourceId, strategy);
    }
    
    @Override
    public void recordCacheMiss(String sourceId, String strategy) {
        monitoringService.recordCacheMiss(sourceId, strategy);
    }
}
