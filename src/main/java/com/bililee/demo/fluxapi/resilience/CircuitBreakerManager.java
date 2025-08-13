package com.bililee.demo.fluxapi.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 断路器管理器
 * 支持多实例断路器，基于Source-Id进行隔离
 * 实现快速失败机制，确保3秒内响应
 */
@Slf4j
@Component
public class CircuitBreakerManager {

    // 每个Source-Id对应一个断路器实例
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    // 断路器配置
    private final CircuitBreakerConfig defaultConfig = new CircuitBreakerConfig();

    @PostConstruct
    public void init() {
        log.info("断路器管理器初始化完成，默认配置: {}", defaultConfig);
    }

    /**
     * 执行断路器保护的调用
     */
    public <T> Mono<T> executeCall(String sourceId, String operation, 
                                   java.util.function.Supplier<Mono<T>> supplier) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(sourceId);
        return circuitBreaker.executeCall(operation, supplier);
    }

    /**
     * 获取或创建断路器实例
     */
    private CircuitBreaker getOrCreateCircuitBreaker(String sourceId) {
        return circuitBreakers.computeIfAbsent(sourceId, 
                key -> new CircuitBreaker(key, defaultConfig));
    }

    /**
     * 获取断路器状态
     */
    public CircuitBreakerState getCircuitBreakerState(String sourceId) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(sourceId);
        return circuitBreaker != null ? circuitBreaker.getState() : CircuitBreakerState.CLOSED;
    }

    /**
     * 手动重置断路器（用于运维）
     */
    public void resetCircuitBreaker(String sourceId) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(sourceId);
        if (circuitBreaker != null) {
            circuitBreaker.reset();
            log.info("断路器已手动重置: {}", sourceId);
        }
    }

    /**
     * 断路器状态枚举
     */
    public enum CircuitBreakerState {
        /**
         * 关闭状态 - 正常工作
         */
        CLOSED,
        
        /**
         * 打开状态 - 快速失败
         */
        OPEN,
        
        /**
         * 半开状态 - 试探性恢复
         */
        HALF_OPEN
    }

    /**
     * 断路器配置
     */
    public static class CircuitBreakerConfig {
        /**
         * 失败率阈值（百分比）
         */
        private final int failureRateThreshold = 50;
        
        /**
         * 慢调用持续时间阈值
         */
        private final Duration slowCallDurationThreshold = Duration.ofSeconds(2);
        
        /**
         * 最小调用次数（统计窗口内）
         */
        private final int minimumNumberOfCalls = 5;
        
        /**
         * 断路器打开持续时间
         */
        private final Duration waitDurationInOpenState = Duration.ofSeconds(10);
        
        /**
         * 半开状态下的测试调用次数
         */
        private final int permittedNumberOfCallsInHalfOpenState = 3;

        public int getFailureRateThreshold() { return failureRateThreshold; }
        public Duration getSlowCallDurationThreshold() { return slowCallDurationThreshold; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public int getPermittedNumberOfCallsInHalfOpenState() { return permittedNumberOfCallsInHalfOpenState; }

        @Override
        public String toString() {
            return String.format("CircuitBreakerConfig{failureRate=%d%%, slowCallThreshold=%s, minCalls=%d}", 
                    failureRateThreshold, slowCallDurationThreshold, minimumNumberOfCalls);
        }
    }

    /**
     * 断路器实现
     */
    private static class CircuitBreaker {
        private final String sourceId;
        private final CircuitBreakerConfig config;
        private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
        
        // 统计数据
        private final AtomicInteger totalCalls = new AtomicInteger(0);
        private final AtomicInteger failedCalls = new AtomicInteger(0);
        private final AtomicInteger slowCalls = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private final AtomicInteger halfOpenCalls = new AtomicInteger(0);

        public CircuitBreaker(String sourceId, CircuitBreakerConfig config) {
            this.sourceId = sourceId;
            this.config = config;
        }

        public <T> Mono<T> executeCall(String operation, java.util.function.Supplier<Mono<T>> supplier) {
            CircuitBreakerState currentState = state.get();
            
            switch (currentState) {
                case OPEN:
                    return handleOpenState();
                case HALF_OPEN:
                    return handleHalfOpenState(operation, supplier);
                case CLOSED:
                default:
                    return handleClosedState(operation, supplier);
            }
        }

        private <T> Mono<T> handleOpenState() {
            // 检查是否应该转换到半开状态
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
            if (timeSinceLastFailure >= config.getWaitDurationInOpenState().toMillis()) {
                if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    log.info("断路器状态转换: OPEN -> HALF_OPEN - {}", sourceId);
                    halfOpenCalls.set(0);
                }
                return Mono.error(new CircuitBreakerOpenException("断路器处于半开状态，正在恢复中"));
            }
            
            log.debug("断路器处于开启状态，拒绝调用 - {}", sourceId);
            return Mono.error(new CircuitBreakerOpenException("断路器已开启，服务暂时不可用"));
        }

        private <T> Mono<T> handleHalfOpenState(String operation, java.util.function.Supplier<Mono<T>> supplier) {
            int currentHalfOpenCalls = halfOpenCalls.get();
            if (currentHalfOpenCalls >= config.getPermittedNumberOfCallsInHalfOpenState()) {
                log.debug("半开状态调用次数已达上限，拒绝调用 - {}", sourceId);
                return Mono.error(new CircuitBreakerOpenException("断路器半开状态调用次数已达上限"));
            }

            halfOpenCalls.incrementAndGet();
            return executeWithMetrics(operation, supplier)
                    .doOnSuccess(result -> {
                        // 成功时转换到关闭状态
                        if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
                            log.info("断路器状态转换: HALF_OPEN -> CLOSED - {}", sourceId);
                            resetMetrics();
                        }
                    })
                    .doOnError(error -> {
                        // 失败时转换到开启状态
                        if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN)) {
                            log.warn("断路器状态转换: HALF_OPEN -> OPEN - {} - 错误: {}", sourceId, error.getMessage());
                            lastFailureTime.set(System.currentTimeMillis());
                        }
                    });
        }

        private <T> Mono<T> handleClosedState(String operation, java.util.function.Supplier<Mono<T>> supplier) {
            return executeWithMetrics(operation, supplier)
                    .doFinally(signal -> {
                        // 检查是否应该打开断路器
                        checkAndUpdateState();
                    });
        }

        private <T> Mono<T> executeWithMetrics(String operation, java.util.function.Supplier<Mono<T>> supplier) {
            Instant startTime = Instant.now();
            totalCalls.incrementAndGet();

            return supplier.get()
                    .doOnSuccess(result -> {
                        Duration duration = Duration.between(startTime, Instant.now());
                        if (duration.compareTo(config.getSlowCallDurationThreshold()) > 0) {
                            slowCalls.incrementAndGet();
                            log.debug("慢调用检测 - {} - 耗时: {}ms", sourceId, duration.toMillis());
                        }
                    })
                    .doOnError(error -> {
                        failedCalls.incrementAndGet();
                        lastFailureTime.set(System.currentTimeMillis());
                        Duration duration = Duration.between(startTime, Instant.now());
                        log.debug("调用失败 - {} - 耗时: {}ms - 错误: {}", sourceId, duration.toMillis(), error.getMessage());
                    });
        }

        private void checkAndUpdateState() {
            int total = totalCalls.get();
            if (total < config.getMinimumNumberOfCalls()) {
                return; // 样本数量不足
            }

            int failed = failedCalls.get();
            int slow = slowCalls.get();
            
            // 计算失败率（包含慢调用）
            int failureCount = failed + slow;
            double failureRate = (double) failureCount / total * 100;

            if (failureRate >= config.getFailureRateThreshold()) {
                if (state.compareAndSet(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN)) {
                    log.warn("断路器状态转换: CLOSED -> OPEN - {} - 失败率: {:.1f}% (阈值: {}%)", 
                            sourceId, failureRate, config.getFailureRateThreshold());
                    lastFailureTime.set(System.currentTimeMillis());
                }
            }

            // 重置统计数据（滑动窗口）
            if (total >= 100) { // 避免统计数据无限增长
                resetMetrics();
            }
        }

        private void resetMetrics() {
            totalCalls.set(0);
            failedCalls.set(0);
            slowCalls.set(0);
        }

        public void reset() {
            state.set(CircuitBreakerState.CLOSED);
            resetMetrics();
            halfOpenCalls.set(0);
        }

        public CircuitBreakerState getState() {
            return state.get();
        }
    }

    /**
     * 断路器开启异常
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
