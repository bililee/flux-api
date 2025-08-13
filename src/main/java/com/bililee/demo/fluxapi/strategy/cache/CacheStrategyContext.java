package com.bililee.demo.fluxapi.strategy.cache;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 缓存策略执行上下文
 * 
 * <p>包含缓存策略执行过程中需要的所有参数和状态信息</p>
 * 
 * @param <TRequest> 请求类型
 * @param <TResponse> 响应类型
 * 
 * @author bililee
 * @since 1.0.0
 */
@Data
@Builder
public class CacheStrategyContext<TRequest, TResponse> {
    
    /** 原始请求对象 */
    private TRequest request;
    
    /** 源标识ID */
    private String sourceId;
    
    /** 缓存键 */
    private String cacheKey;
    
    /** 缓存规则配置 */
    private Object cacheRule;
    
    /** 请求开始时间 */
    private Instant startTime;
    
    /** 请求的API路径（用于监控） */
    private String apiPath;
    
    /** 策略名称（用于日志） */
    private String strategyName;
    
    /** 扩展参数 */
    private java.util.Map<String, Object> extraParams;
    
    /**
     * 获取扩展参数
     */
    public <T> T getExtraParam(String key, Class<T> type) {
        Object value = extraParams != null ? extraParams.get(key) : null;
        return type.isInstance(value) ? type.cast(value) : null;
    }
    
    /**
     * 设置扩展参数
     */
    public void setExtraParam(String key, Object value) {
        if (extraParams == null) {
            extraParams = new java.util.HashMap<>();
        }
        extraParams.put(key, value);
    }
    
    /**
     * 验证上下文的必要字段
     */
    public void validate() {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceId不能为空");
        }
        if (cacheKey == null || cacheKey.trim().isEmpty()) {
            throw new IllegalArgumentException("cacheKey不能为空");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime不能为空");
        }
        if (apiPath == null || apiPath.trim().isEmpty()) {
            throw new IllegalArgumentException("apiPath不能为空");
        }
        if (strategyName == null || strategyName.trim().isEmpty()) {
            throw new IllegalArgumentException("strategyName不能为空");
        }
    }
}
