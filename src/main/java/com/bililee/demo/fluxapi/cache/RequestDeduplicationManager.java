package com.bililee.demo.fluxapi.cache;

import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 请求去重管理器
 * 实现相同请求的合并处理和结果广播
 */
@Slf4j
@Component
public class RequestDeduplicationManager {

    /**
     * 等待中的请求映射
     * key: requestKey, value: 完成信号的Sink (Boolean表示首个请求是否成功完成)
     */
    private final ConcurrentHashMap<String, Sinks.One<Boolean>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 请求统计
     */
    private final ConcurrentMap<String, RequestStats> requestStats = new ConcurrentHashMap<>();

    /**
     * 执行去重请求
     * 如果是首个请求，则执行actualSupplier；否则等待首个请求完成后重新执行缓存策略
     *
     * @param request 请求对象
     * @param sourceId 业务来源ID
     * @param actualSupplier 实际执行逻辑的提供者（缓存策略）
     * @return 响应结果
     */
    public Mono<SpecificDataResponse> executeDeduplicatedRequest(
            SpecificDataRequest request, 
            String sourceId, 
            Supplier<Mono<SpecificDataResponse>> actualSupplier) {

        String requestKey = generateRequestKey(request, sourceId);
        
        // 检查是否已有相同请求正在处理
        Sinks.One<Boolean> existingSink = pendingRequests.get(requestKey);
        
        if (existingSink != null) {
            // 有相同请求正在处理，等待首个请求完成后重新执行缓存策略
            log.debug("请求去重 - 等待首个请求完成后从缓存获取: {}", requestKey);
            incrementWaitingCount(requestKey);
            
            return existingSink.asMono()
                    .timeout(Duration.ofSeconds(10)) // 等待首个请求完成
                    .then(Mono.defer(() -> {
                        // 首个请求完成后，重新执行缓存策略（应该命中缓存）
                        log.debug("请求去重 - 首个请求已完成，重新执行缓存策略: {}", requestKey);
                        return actualSupplier.get();
                    }))
                    .doOnSuccess(response -> {
                        log.debug("请求去重 - 从缓存获取数据成功: {}", requestKey);
                        decrementWaitingCount(requestKey);
                    })
                    .doOnError(error -> {
                        if (error instanceof java.util.concurrent.TimeoutException) {
                            log.warn("请求去重 - 等待超时，将移除阻塞的首个请求: {}", requestKey);
                            // 清理可能阻塞的请求
                            pendingRequests.remove(requestKey);
                        } else {
                            log.warn("请求去重 - 等待失败: {} - {}", requestKey, error.getMessage());
                        }
                        decrementWaitingCount(requestKey);
                    });
        }
        
        // 首个请求，创建新的Sink并执行实际逻辑
        Sinks.One<Boolean> sink = Sinks.one();
        Sinks.One<Boolean> previousSink = pendingRequests.putIfAbsent(requestKey, sink);
        
        if (previousSink != null) {
            // 并发情况下，其他线程已经创建了Sink，等待首个请求完成后重新执行缓存策略
            log.debug("请求去重 - 并发情况下等待首个请求完成: {}", requestKey);
            incrementWaitingCount(requestKey);
            
            return previousSink.asMono()
                    .timeout(Duration.ofSeconds(10))
                    .then(Mono.defer(() -> {
                        // 首个请求完成后，重新执行缓存策略
                        log.debug("请求去重 - 并发情况下重新执行缓存策略: {}", requestKey);
                        return actualSupplier.get();
                    }))
                    .doOnSuccess(response -> decrementWaitingCount(requestKey))
                    .doOnError(error -> {
                        if (error instanceof java.util.concurrent.TimeoutException) {
                            log.warn("请求去重 - 并发等待超时，清理阻塞请求: {}", requestKey);
                            pendingRequests.remove(requestKey);
                        }
                        decrementWaitingCount(requestKey);
                    });
        }
        
        // 当前线程负责执行实际请求
        log.debug("请求去重 - 首个请求开始执行: {}", requestKey);
        incrementProcessingCount(requestKey);
        
        return actualSupplier.get()
                .timeout(Duration.ofSeconds(9)) // 为首个请求添加超时保护，略大于弹性服务的8秒
                .doOnSuccess(response -> {
                    log.debug("请求去重 - 首个请求执行成功，通知等待请求: {}", requestKey);
                    sink.tryEmitValue(true); // 通知等待的请求可以执行了
                    pendingRequests.remove(requestKey);
                    updateSuccessStats(requestKey);
                })
                .doOnError(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        log.error("请求去重 - 首个请求执行超时，通知等待请求失败: {}", requestKey);
                    } else {
                        log.warn("请求去重 - 首个请求执行失败，通知等待请求失败: {} - {}", requestKey, error.getMessage());
                    }
                    sink.tryEmitError(error); // 首个请求失败，等待的请求也失败
                    pendingRequests.remove(requestKey);
                    updateErrorStats(requestKey);
                })
                .doFinally(signal -> {
                    decrementProcessingCount(requestKey);
                });
    }

    /**
     * 生成请求唯一标识
     */
    private String generateRequestKey(SpecificDataRequest request, String sourceId) {
        // 使用请求的关键字段生成唯一键
        StringBuilder keyBuilder = new StringBuilder();
        
        // 添加业务来源ID
        keyBuilder.append("source:").append(sourceId != null ? sourceId : "default").append("|");
        
        // 添加代码选择器
        if (request.codeSelectors() != null && request.codeSelectors().include() != null) {
            request.codeSelectors().include().forEach(selector -> {
                keyBuilder.append("codes:").append(String.join(",", selector.values())).append("|");
            });
        }
        
        // 添加指标列表
        if (request.indexes() != null) {
            request.indexes().forEach(index -> {
                keyBuilder.append("idx:").append(index.indexId());
                if (index.timeType() != null) {
                    keyBuilder.append(":").append(index.timeType());
                }
                if (index.timestamp() != null) {
                    keyBuilder.append(":").append(index.timestamp());
                }
                keyBuilder.append("|");
            });
        }
        
        // 添加分页信息（某些情况下不同分页可能需要不同处理）
        if (request.pageInfo() != null) {
            keyBuilder.append("page:").append(request.pageInfo().pageBegin())
                    .append(":").append(request.pageInfo().pageSize()).append("|");
        }
        
        String key = keyBuilder.toString();
        
        // 生成哈希值以减少键长度
        return "req_" + Math.abs(key.hashCode());
    }

    /**
     * 增加等待计数
     */
    private void incrementWaitingCount(String requestKey) {
        requestStats.computeIfAbsent(requestKey, k -> new RequestStats()).incrementWaiting();
    }

    /**
     * 减少等待计数
     */
    private void decrementWaitingCount(String requestKey) {
        RequestStats stats = requestStats.get(requestKey);
        if (stats != null) {
            stats.decrementWaiting();
        }
    }

    /**
     * 增加处理计数
     */
    private void incrementProcessingCount(String requestKey) {
        requestStats.computeIfAbsent(requestKey, k -> new RequestStats()).incrementProcessing();
    }

    /**
     * 减少处理计数
     */
    private void decrementProcessingCount(String requestKey) {
        RequestStats stats = requestStats.get(requestKey);
        if (stats != null) {
            stats.decrementProcessing();
        }
    }

    /**
     * 更新成功统计
     */
    private void updateSuccessStats(String requestKey) {
        RequestStats stats = requestStats.get(requestKey);
        if (stats != null) {
            stats.incrementSuccess();
            // 清理长时间未使用的统计
            cleanupStatsIfNeeded(requestKey, stats);
        }
    }

    /**
     * 更新错误统计
     */
    private void updateErrorStats(String requestKey) {
        RequestStats stats = requestStats.get(requestKey);
        if (stats != null) {
            stats.incrementError();
            cleanupStatsIfNeeded(requestKey, stats);
        }
    }

    /**
     * 清理统计信息
     */
    private void cleanupStatsIfNeeded(String requestKey, RequestStats stats) {
        // 如果没有等待和处理中的请求，且统计信息较旧，则清理
        if (stats.getWaitingCount() == 0 && stats.getProcessingCount() == 0) {
            long timeSinceLastUpdate = System.currentTimeMillis() - stats.getLastUpdateTime();
            if (timeSinceLastUpdate > Duration.ofMinutes(5).toMillis()) {
                requestStats.remove(requestKey);
                log.debug("清理请求统计信息: {}", requestKey);
            }
        }
    }

    /**
     * 获取当前等待队列大小
     */
    public int getPendingRequestsCount() {
        return pendingRequests.size();
    }

    /**
     * 获取请求统计信息
     */
    public RequestStats getRequestStats(String requestKey) {
        return requestStats.get(requestKey);
    }

    /**
     * 获取所有请求统计
     */
    public ConcurrentMap<String, RequestStats> getAllRequestStats() {
        return new ConcurrentHashMap<>(requestStats);
    }

    /**
     * 请求统计信息
     */
    public static class RequestStats {
        private volatile int waitingCount = 0;
        private volatile int processingCount = 0;
        private volatile long successCount = 0;
        private volatile long errorCount = 0;
        private volatile long lastUpdateTime = System.currentTimeMillis();

        public synchronized void incrementWaiting() {
            waitingCount++;
            lastUpdateTime = System.currentTimeMillis();
        }

        public synchronized void decrementWaiting() {
            waitingCount = Math.max(0, waitingCount - 1);
            lastUpdateTime = System.currentTimeMillis();
        }

        public synchronized void incrementProcessing() {
            processingCount++;
            lastUpdateTime = System.currentTimeMillis();
        }

        public synchronized void decrementProcessing() {
            processingCount = Math.max(0, processingCount - 1);
            lastUpdateTime = System.currentTimeMillis();
        }

        public synchronized void incrementSuccess() {
            successCount++;
            lastUpdateTime = System.currentTimeMillis();
        }

        public synchronized void incrementError() {
            errorCount++;
            lastUpdateTime = System.currentTimeMillis();
        }

        // Getters
        public int getWaitingCount() { return waitingCount; }
        public int getProcessingCount() { return processingCount; }
        public long getSuccessCount() { return successCount; }
        public long getErrorCount() { return errorCount; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        
        public double getSuccessRate() {
            long total = successCount + errorCount;
            return total == 0 ? 0.0 : (double) successCount / total;
        }
    }
}