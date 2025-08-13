package com.bililee.demo.fluxapi.strategy.cache.adapter;

import com.bililee.demo.fluxapi.client.RemoteSpecificDataClient;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.strategy.cache.RemoteServiceCaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * SpecificData 远程服务调用适配器
 * 
 * <p>将现有的 RemoteSpecificDataClient 适配到新的策略模式框架中</p>
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Component
public class SpecificDataRemoteServiceCaller implements RemoteServiceCaller<SpecificDataRequest, SpecificDataResponse> {
    
    @Autowired
    private RemoteSpecificDataClient remoteClient;
    
    @Override
    public Mono<SpecificDataResponse> callRemoteService(SpecificDataRequest request, String sourceId) {
        log.debug("调用远程服务 - sourceId: {}, request: {}", sourceId, request);
        
        return remoteClient.fetchSpecificData(request, sourceId)
                .timeout(Duration.ofSeconds(8)) // 策略层面的超时保护，与弹性服务保持一致
                .doOnSuccess(response -> {
                    log.debug("远程服务调用成功 - sourceId: {}, statusCode: {}", 
                            sourceId, response != null ? response.statusCode() : "null");
                })
                .doOnError(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        log.error("策略层远程服务调用超时 - sourceId: {}, 超时时间: 8秒", sourceId);
                    } else {
                        log.error("远程服务调用失败 - sourceId: {}, error: {}", 
                                sourceId, error.getMessage());
                    }
                })
                .onErrorReturn(SpecificDataResponse.timeoutError()); // 超时时返回明确的超时错误响应
    }
}
