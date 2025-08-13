package com.bililee.demo.fluxapi.strategy.cache;

import reactor.core.publisher.Mono;

/**
 * 缓存策略接口
 * 
 * <p>定义了缓存策略的统一行为规范，支持泛型以适应不同的请求/响应类型</p>
 * 
 * @param <TRequest> 请求类型
 * @param <TResponse> 响应类型
 * 
 * @author bililee
 * @since 1.0.0
 */
public interface CacheStrategy<TRequest, TResponse> {
    
    /**
     * 执行缓存策略
     * 
     * @param context 策略执行上下文，包含请求、缓存配置等信息
     * @return 响应数据的 Mono 流
     */
    Mono<TResponse> execute(CacheStrategyContext<TRequest, TResponse> context);
    
    /**
     * 获取策略名称
     * 
     * @return 策略名称，用于日志和监控
     */
    String getStrategyName();
    
    /**
     * 判断是否支持指定的策略类型
     * 
     * @param strategyType 策略类型枚举或字符串
     * @return 如果支持该策略类型则返回true
     */
    boolean supports(Object strategyType);
}
