package com.bililee.demo.fluxapi.resilience;

import com.bililee.demo.fluxapi.monitoring.CacheMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;

/**
 * 弹性调用服务
 * 整合断路器、线程池隔离、超时、重试等策略
 * 确保在3秒内完成响应或失败
 */
@Slf4j
@Service
public class ResilienceService {

    @Autowired
    private CircuitBreakerManager circuitBreakerManager;

    @Autowired
    private ThreadPoolManager threadPoolManager;

    @Autowired
    private CacheMonitoringService monitoringService;

    /**
     * 执行弹性远程调用
     * 集成断路器、线程池隔离、超时、重试策略
     */
    public <T> Mono<T> executeResilientCall(String sourceId, String operation,
                                           java.util.function.Supplier<Mono<T>> remoteCall) {
        Instant startTime = Instant.now();
        
        log.debug("开始弹性调用 - sourceId: {}, operation: {}", sourceId, operation);

        return circuitBreakerManager.executeCall(sourceId, operation, () ->
                threadPoolManager.executeOnRemoteCallPool(() ->
                        executeWithTimeoutAndRetry(sourceId, operation, remoteCall, startTime)
                )
        )
        .doOnSuccess(result -> {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            monitoringService.recordRemoteCall(sourceId, duration, true);
            log.debug("弹性调用成功 - sourceId: {}, operation: {}, duration: {}ms", 
                    sourceId, operation, duration);
        })
        .doOnError(error -> {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            monitoringService.recordRemoteCall(sourceId, duration, false);
            
            if (error instanceof CircuitBreakerManager.CircuitBreakerOpenException) {
                monitoringService.recordBusinessError(sourceId, "circuit_breaker_open", error.getClass().getSimpleName());
                log.warn("弹性调用失败-断路器开启 - sourceId: {}, operation: {}, duration: {}ms", 
                        sourceId, operation, duration);
            } else {
                monitoringService.recordBusinessError(sourceId, "remote_call_failed", error.getClass().getSimpleName());
                log.error("弹性调用失败 - sourceId: {}, operation: {}, duration: {}ms, error: {}", 
                        sourceId, operation, duration, error.getMessage());
            }
        });
    }

    /**
     * 带超时和重试的执行逻辑
     */
    private <T> Mono<T> executeWithTimeoutAndRetry(String sourceId, String operation,
                                                  java.util.function.Supplier<Mono<T>> remoteCall,
                                                  Instant startTime) {
        
        return remoteCall.get()
                .timeout(Duration.ofSeconds(5)) // 单次调用5秒超时
                .retryWhen(createRetrySpec(sourceId, operation, startTime))
                .timeout(Duration.ofSeconds(8)) // 总超时8秒，给请求去重留足够时间
                .doOnError(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        log.warn("远程调用超时 - sourceId: {}, operation: {}", sourceId, operation);
                    }
                });
    }

    /**
     * 创建重试规格
     */
    private Retry createRetrySpec(String sourceId, String operation, Instant startTime) {
        return Retry.backoff(2, Duration.ofMillis(100)) // 最多重试2次，退避100ms起
                .maxBackoff(Duration.ofMillis(500)) // 最大退避500ms
                .filter(createRetryPredicate(sourceId, operation, startTime))
                .doBeforeRetry(retrySignal -> {
                    log.debug("准备重试 - sourceId: {}, operation: {}, 重试次数: {}, 错误: {}", 
                            sourceId, operation, retrySignal.totalRetries() + 1, 
                            retrySignal.failure().getMessage());
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.warn("重试次数已耗尽 - sourceId: {}, operation: {}, 总重试次数: {}", 
                            sourceId, operation, retrySignal.totalRetries());
                    return retrySignal.failure();
                });
    }

    /**
     * 创建重试判断条件
     */
    private Predicate<Throwable> createRetryPredicate(String sourceId, String operation, Instant startTime) {
        return throwable -> {
            // 检查总执行时间，超过7秒不再重试
            long totalDuration = Duration.between(startTime, Instant.now()).toMillis();
            if (totalDuration > 7000) {
                log.debug("总执行时间超过7秒，停止重试 - sourceId: {}, operation: {}", sourceId, operation);
                return false;
            }

            // 断路器开启时不重试
            if (throwable instanceof CircuitBreakerManager.CircuitBreakerOpenException) {
                log.debug("断路器开启，停止重试 - sourceId: {}, operation: {}", sourceId, operation);
                return false;
            }

            // 超时异常可以重试
            if (throwable instanceof java.util.concurrent.TimeoutException) {
                log.debug("超时异常，允许重试 - sourceId: {}, operation: {}", sourceId, operation);
                return true;
            }

            // 网络相关异常可以重试
            if (throwable instanceof java.net.ConnectException ||
                throwable instanceof java.net.SocketTimeoutException ||
                throwable instanceof java.io.IOException) {
                log.debug("网络异常，允许重试 - sourceId: {}, operation: {}, 异常类型: {}", 
                        sourceId, operation, throwable.getClass().getSimpleName());
                return true;
            }

            // WebClient响应异常的处理
            if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                org.springframework.web.reactive.function.client.WebClientResponseException webEx = 
                        (org.springframework.web.reactive.function.client.WebClientResponseException) throwable;
                
                // 5xx错误可以重试
                if (webEx.getStatusCode().is5xxServerError()) {
                    log.debug("5xx服务器错误，允许重试 - sourceId: {}, operation: {}, 状态码: {}", 
                            sourceId, operation, webEx.getStatusCode().value());
                    return true;
                }
                
                // 4xx错误不重试
                if (webEx.getStatusCode().is4xxClientError()) {
                    log.debug("4xx客户端错误，不重试 - sourceId: {}, operation: {}, 状态码: {}", 
                            sourceId, operation, webEx.getStatusCode().value());
                    return false;
                }
            }

            // 其他异常不重试
            log.debug("其他异常，不重试 - sourceId: {}, operation: {}, 异常类型: {}", 
                    sourceId, operation, throwable.getClass().getSimpleName());
            return false;
        };
    }

    /**
     * 健康检查调用（简化版，用于探测服务状态）
     */
    public Mono<Boolean> healthCheck(String sourceId, java.util.function.Supplier<Mono<Boolean>> healthCheckCall) {
        return circuitBreakerManager.executeCall(sourceId, "health_check", () ->
                threadPoolManager.executeOnRemoteCallPool(() ->
                        healthCheckCall.get()
                                .timeout(Duration.ofSeconds(1)) // 健康检查1秒超时
                                .onErrorReturn(false)
                )
        )
        .onErrorReturn(false)
        .doOnNext(healthy -> {
            log.debug("健康检查结果 - sourceId: {}, 健康状态: {}", sourceId, healthy);
        });
    }

    /**
     * 获取断路器状态
     */
    public CircuitBreakerManager.CircuitBreakerState getCircuitBreakerState(String sourceId) {
        return circuitBreakerManager.getCircuitBreakerState(sourceId);
    }

    /**
     * 获取线程池统计信息
     */
    public ThreadPoolManager.ThreadPoolStats getThreadPoolStats() {
        return threadPoolManager.getThreadPoolStats();
    }

    /**
     * 手动重置断路器（运维接口）
     */
    public void resetCircuitBreaker(String sourceId) {
        circuitBreakerManager.resetCircuitBreaker(sourceId);
        log.info("手动重置断路器 - sourceId: {}", sourceId);
    }
}
