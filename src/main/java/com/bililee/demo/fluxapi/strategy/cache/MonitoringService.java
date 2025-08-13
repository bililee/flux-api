package com.bililee.demo.fluxapi.strategy.cache;

/**
 * 监控服务接口
 * 
 * <p>定义了缓存策略中需要的监控操作</p>
 * 
 * @author bililee
 * @since 1.0.0
 */
public interface MonitoringService {
    
    /**
     * 记录API响应时间
     * 
     * @param sourceId 源标识ID
     * @param apiPath API路径
     * @param duration 响应时间（毫秒）
     */
    void recordApiResponseTime(String sourceId, String apiPath, long duration);
    
    /**
     * 记录业务错误
     * 
     * @param sourceId 源标识ID
     * @param errorType 错误类型
     * @param errorDetail 错误详情
     */
    void recordBusinessError(String sourceId, String errorType, String errorDetail);
    
    /**
     * 记录缓存命中
     * 
     * @param sourceId 源标识ID
     * @param strategy 策略名称
     */
    void recordCacheHit(String sourceId, String strategy);
    
    /**
     * 记录缓存未命中
     * 
     * @param sourceId 源标识ID
     * @param strategy 策略名称
     */
    void recordCacheMiss(String sourceId, String strategy);
}
