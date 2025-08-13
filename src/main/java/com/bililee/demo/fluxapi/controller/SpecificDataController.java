package com.bililee.demo.fluxapi.controller;

import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.service.impl.SpecificDataServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 特定数据控制器
 * 提供 v1/specific_data 接口，支持基于Source-Id的业务缓存策略
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class SpecificDataController {

    @Autowired
    private SpecificDataServiceImpl specificDataService;
    
    // 简单熔断器 - 防止级联失败
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>(Instant.MIN);
    private final AtomicInteger successCount = new AtomicInteger(0);
    
    // 熔断器配置
    private static final int FAILURE_THRESHOLD = 5; // 连续失败5次触发熔断
    private static final Duration CIRCUIT_OPEN_DURATION = Duration.ofSeconds(30); // 熔断持续30秒
    private static final int SUCCESS_THRESHOLD = 3; // 连续成功3次恢复正常

    /**
     * 查询特定数据
     * POST /v1/specific_data
     *
     * @param request 查询请求
     * @param sourceId 业务来源标识（从请求头获取）
     * @return 查询结果
     */
    @PostMapping("/specific_data")
    public Mono<ResponseEntity<SpecificDataResponse>> querySpecificData(
            @RequestBody SpecificDataRequest request,
            @RequestHeader(value = "Source-Id", required = false) String sourceId) {
        try {
            // 如果没有提供Source-Id，使用默认值
            String actualSourceId = sourceId != null ? sourceId : "default";
            
            // 检查熔断器状态
            if (isCircuitOpen()) {
                log.warn("熔断器开启，拒绝请求 - sourceId: {}", actualSourceId);
                return Mono.just(ResponseEntity.ok(SpecificDataResponse.serviceUnavailable()));
            }
            
            log.info("收到特定数据查询请求 - sourceId: {}, 代码数量: {}", 
                    actualSourceId, 
                    request.codeSelectors().include().stream()
                            .mapToInt(selector -> selector.values().size()).sum());

            return specificDataService.querySpecificData(request, actualSourceId)
                    .timeout(Duration.ofSeconds(12)) // 在控制器层面添加整体超时保护，略大于请求去重的9秒
                    .map(response -> {
                        log.info("特定数据查询成功 - sourceId: {}, 返回数据量: {}", 
                                actualSourceId, response.data().total());
                        recordSuccess(); // 记录成功
                        return ResponseEntity.ok(response);
                    })
                    .doOnError(error -> {
                        log.error("特定数据查询失败 - sourceId: {}, error: {}", 
                                actualSourceId, error.getMessage(), error);
                        recordFailure(); // 记录失败
                    })
                    .onErrorReturn(ResponseEntity.ok(SpecificDataResponse.internalServerError()));

        } catch (Exception ex) {
            log.error("处理特定数据查询请求时发生异常 - sourceId: {}, error: {}", 
                    sourceId, ex.getMessage(), ex);
            
            recordFailure(); // 记录失败
            SpecificDataResponse errorResponse = SpecificDataResponse.internalServerError();
            
            return Mono.just(ResponseEntity.ok(errorResponse));
        }
    }
    
    /**
     * 检查熔断器是否开启
     */
    private boolean isCircuitOpen() {
        int currentFailures = failureCount.get();
        Instant lastFailure = lastFailureTime.get();
        
        // 如果失败次数未达到阈值，熔断器关闭
        if (currentFailures < FAILURE_THRESHOLD) {
            return false;
        }
        
        // 如果熔断器开启时间超过设定时间，进入半开状态
        if (Duration.between(lastFailure, Instant.now()).compareTo(CIRCUIT_OPEN_DURATION) > 0) {
            log.info("熔断器进入半开状态，允许尝试请求");
            return false; // 半开状态，允许请求通过
        }
        
        return true; // 熔断器开启
    }
    
    /**
     * 记录成功
     */
    private void recordSuccess() {
        int currentSuccesses = successCount.incrementAndGet();
        
        // 如果连续成功次数达到阈值，重置失败计数
        if (currentSuccesses >= SUCCESS_THRESHOLD) {
            failureCount.set(0);
            successCount.set(0);
            log.info("熔断器恢复正常，重置失败计数");
        }
    }
    
    /**
     * 记录失败
     */
    private void recordFailure() {
        successCount.set(0); // 重置成功计数
        int currentFailures = failureCount.incrementAndGet();
        lastFailureTime.set(Instant.now());
        
        if (currentFailures >= FAILURE_THRESHOLD) {
            log.warn("熔断器开启，当前失败次数: {}", currentFailures);
        }
    }
}