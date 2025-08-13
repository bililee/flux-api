package com.bililee.demo.fluxapi.strategy.cache;

import reactor.core.publisher.Mono;

/**
 * 远程服务调用接口
 * 
 * <p>定义了缓存策略中需要的远程服务调用操作</p>
 * 
 * @param <TRequest> 请求类型
 * @param <TResponse> 响应类型
 * 
 * @author bililee
 * @since 1.0.0
 */
public interface RemoteServiceCaller<TRequest, TResponse> {
    
    /**
     * 调用远程服务
     * 
     * @param request 请求对象
     * @param sourceId 源标识ID
     * @return 远程服务响应数据
     */
    Mono<TResponse> callRemoteService(TRequest request, String sourceId);
}
