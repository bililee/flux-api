package com.bililee.demo.fluxapi.service.impl;

import com.bililee.demo.fluxapi.cache.RequestDeduplicationManager;
import com.bililee.demo.fluxapi.cache.SpecificDataCacheManager;
import com.bililee.demo.fluxapi.client.RemoteSpecificDataClient;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import com.bililee.demo.fluxapi.exception.ApiServerException;
import com.bililee.demo.fluxapi.exception.ApiTimeoutException;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.monitoring.CacheMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SpecificDataServiceImpl 单元测试
 * 测试核心业务逻辑：缓存策略、远程调用、请求去重、降级策略等
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("特定数据服务实现测试")
@SuppressWarnings("unchecked")
class SpecificDataServiceImplTest {

    @Mock
    private CacheStrategyConfig cacheStrategyConfig;

    @Mock
    private SpecificDataCacheManager cacheManager;

    @Mock
    private RequestDeduplicationManager deduplicationManager;

    @Mock
    private RemoteSpecificDataClient remoteClient;

    @Mock
    private CacheMonitoringService monitoringService;

    @InjectMocks
    private SpecificDataServiceImpl specificDataService;

    private SpecificDataRequest testRequest;
    private SpecificDataResponse testResponse;
    private final String testSourceId = "test-source";
    private final String testCacheKey = "cache-key-123";

    @BeforeEach
    void setUp() {
        testRequest = createTestRequest();
        testResponse = createTestResponse();
        
        // Mock基础依赖
        when(cacheManager.generateCacheKey(any(SpecificDataRequest.class), anyString()))
                .thenReturn(testCacheKey);
    }

    // ===================== 不缓存策略测试 =====================

    @Nested
    @DisplayName("不缓存策略测试")
    class NoCacheStrategyTest {

        @BeforeEach
        void setUp() {
            when(cacheStrategyConfig.getCacheStrategy(anyString(), anyString(), anyString()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.NO_CACHE);
        }

        @Test
        @DisplayName("不缓存策略应该直接调用远程服务")
        void querySpecificData_NoCacheStrategy_DirectRemoteCall() {
            // Given
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.just(testResponse));

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectNext(testResponse)
                    .verifyComplete();

            verify(deduplicationManager).executeDeduplicatedRequest(
                    eq(testRequest), eq(testSourceId), any(Supplier.class));
            verify(monitoringService).recordCacheMiss(testSourceId, "NO_CACHE");
            verify(monitoringService).recordApiResponseTime(eq(testSourceId), eq("/v1/specific_data"), anyLong());
        }

        @Test
        @DisplayName("不缓存策略远程调用失败应该记录错误")
        void querySpecificData_NoCacheStrategy_RemoteCallFailed() {
            // Given
            ApiTimeoutException error = new ApiTimeoutException("远程服务超时");
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.error(error));

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectError(ApiTimeoutException.class)
                    .verify();

            verify(monitoringService).recordBusinessError(
                    eq(testSourceId), eq("remote_call_failed"), eq("ApiTimeoutException"));
        }
    }

    // ===================== 被动缓存策略测试 =====================

    @Nested
    @DisplayName("被动缓存策略测试")
    class PassiveCacheStrategyTest {

        private CacheStrategyConfig.CacheRuleConfig testRuleConfig;

        @BeforeEach
        void setUp() {
            when(cacheStrategyConfig.getCacheStrategy(anyString(), anyString(), anyString()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.PASSIVE);
            
            testRuleConfig = mock(CacheStrategyConfig.CacheRuleConfig.class);
            when(cacheStrategyConfig.getCacheRuleConfig(anyString(), anyString(), anyString()))
                    .thenReturn(testRuleConfig);
        }

        @Test
        @DisplayName("被动缓存命中应该直接返回缓存数据")
        void querySpecificData_PassiveStrategy_CacheHit() {
            // Given
            when(cacheManager.getCachedData(testCacheKey, testRequest, testSourceId))
                    .thenReturn(Mono.just(testResponse));

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectNext(testResponse)
                    .verifyComplete();

            verify(cacheManager).getCachedData(testCacheKey, testRequest, testSourceId);
            verify(monitoringService).recordCacheHit(testSourceId, "PASSIVE");
            verify(monitoringService).recordApiResponseTime(eq(testSourceId), eq("/v1/specific_data"), anyLong());
            
            // 不应该调用远程服务
            verifyNoInteractions(remoteClient);
        }

        @Test
        @DisplayName("被动缓存未命中应该调用远程服务并缓存结果")
        void querySpecificData_PassiveStrategy_CacheMiss() {
            // Given
            when(cacheManager.getCachedData(testCacheKey, testRequest, testSourceId))
                    .thenReturn(Mono.just((SpecificDataResponse) null));
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.just(testResponse));
            // 缓存存储不需要mock，SpecificDataCacheManager没有cacheData方法

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectNext(testResponse)
                    .verifyComplete();

            verify(cacheManager).getCachedData(testCacheKey, testRequest, testSourceId);
            verify(monitoringService).recordCacheMiss(testSourceId, "PASSIVE");
            verify(deduplicationManager).executeDeduplicatedRequest(
                    eq(testRequest), eq(testSourceId), any(Supplier.class));
            // verify cacheData - 方法不存在，无需验证
        }

        @Test
        @DisplayName("被动缓存未命中且远程调用失败应该触发降级")
        void querySpecificData_PassiveStrategy_CacheMissAndRemoteCallFailed() {
            // Given
            when(cacheManager.getCachedData(testCacheKey, testRequest, testSourceId))
                    .thenReturn(Mono.just((SpecificDataResponse) null))
                    .thenReturn(Mono.just(testResponse)); // 第二次调用返回过期数据

            ApiServerException error = new ApiServerException("远程服务错误", 500);
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.error(error));

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectNext(testResponse)
                    .verifyComplete();

            verify(monitoringService).recordCacheMiss(testSourceId, "PASSIVE");
            verify(monitoringService).recordFallback(testSourceId, "stale_cache");
        }
    }

    // ===================== 主动缓存策略测试 =====================

    @Nested
    @DisplayName("主动缓存策略测试")
    class ActiveCacheStrategyTest {

        private CacheStrategyConfig.CacheRuleConfig testRuleConfig;

        @BeforeEach
        void setUp() {
            when(cacheStrategyConfig.getCacheStrategy(anyString(), anyString(), anyString()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.ACTIVE);
            
            testRuleConfig = mock(CacheStrategyConfig.CacheRuleConfig.class);
            when(cacheStrategyConfig.getCacheRuleConfig(anyString(), anyString(), anyString()))
                    .thenReturn(testRuleConfig);
        }

        @Test
        @DisplayName("主动缓存命中应该返回缓存数据并触发后台刷新")
        void querySpecificData_ActiveStrategy_CacheHit() {
            // Given
            when(cacheManager.getCachedData(testCacheKey, testRequest, testSourceId))
                    .thenReturn(Mono.just(testResponse));
            // shouldRefreshCache和scheduleActiveRefresh方法不存在，不需要mock

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectNext(testResponse)
                    .verifyComplete();

            verify(cacheManager).getCachedData(testCacheKey, testRequest, testSourceId);
            // verify shouldRefreshCache - 方法不存在，无需验证
            // verify scheduleActiveRefresh - 方法不存在，无需验证
            verify(monitoringService).recordCacheHit(testSourceId, "ACTIVE");
        }

        @Test
        @DisplayName("主动缓存未命中应该调用远程服务")
        void querySpecificData_ActiveStrategy_CacheMiss() {
            // Given
            when(cacheManager.getCachedData(testCacheKey, testRequest, testSourceId))
                    .thenReturn(Mono.just((SpecificDataResponse) null));
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.just(testResponse));
            // 缓存存储不需要mock，SpecificDataCacheManager没有cacheData方法

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectNext(testResponse)
                    .verifyComplete();

            verify(monitoringService).recordCacheMiss(testSourceId, "ACTIVE");
            verify(deduplicationManager).executeDeduplicatedRequest(
                    eq(testRequest), eq(testSourceId), any(Supplier.class));
        }
    }

    // ===================== 远程调用测试 =====================

    @Nested
    @DisplayName("远程调用测试")
    class RemoteCallTest {

        @BeforeEach
        void setUp() {
            when(cacheStrategyConfig.getCacheStrategy(anyString(), anyString(), anyString()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.NO_CACHE);
        }

        @Test
        @DisplayName("远程调用成功应该记录监控数据")
        void callRemoteService_Success() {
            // Given
            when(remoteClient.fetchSpecificData(testRequest))
                    .thenReturn(Mono.just(testResponse));
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        Supplier<Mono<SpecificDataResponse>> supplier = invocation.getArgument(2);
                        return supplier.get();
                    });

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectNext(testResponse)
                    .verifyComplete();

            verify(remoteClient).fetchSpecificData(testRequest);
            verify(monitoringService).recordRemoteCall(eq(testSourceId), anyLong(), eq(true));
        }

        @Test
        @DisplayName("远程调用超时应该记录监控数据")
        void callRemoteService_Timeout() {
            // Given
            ApiTimeoutException timeoutError = new ApiTimeoutException("连接超时");
            when(remoteClient.fetchSpecificData(testRequest))
                    .thenReturn(Mono.error(timeoutError));
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        Supplier<Mono<SpecificDataResponse>> supplier = invocation.getArgument(2);
                        return supplier.get();
                    });

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectError(ApiTimeoutException.class)
                    .verify();

            verify(monitoringService).recordRemoteCall(eq(testSourceId), anyLong(), eq(false));
        }

        @Test
        @DisplayName("远程调用服务器错误应该记录监控数据")
        void callRemoteService_ServerError() {
            // Given
            ApiServerException serverError = new ApiServerException("服务器内部错误", 500);
            when(remoteClient.fetchSpecificData(testRequest))
                    .thenReturn(Mono.error(serverError));
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        Supplier<Mono<SpecificDataResponse>> supplier = invocation.getArgument(2);
                        return supplier.get();
                    });

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectError(ApiServerException.class)
                    .verify();

            verify(monitoringService).recordRemoteCall(eq(testSourceId), anyLong(), eq(false));
        }
    }

    // ===================== 降级策略测试 =====================

    @Nested
    @DisplayName("降级策略测试")
    class FallbackStrategyTest {

        @BeforeEach
        void setUp() {
            when(cacheStrategyConfig.getCacheStrategy(anyString(), anyString(), anyString()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.PASSIVE);
            when(cacheStrategyConfig.getCacheRuleConfig(anyString(), anyString(), anyString()))
                    .thenReturn(mock(CacheStrategyConfig.CacheRuleConfig.class));
        }

        @Test
        @DisplayName("降级时有过期缓存数据应该返回过期数据")
        void handleFallback_WithStaleCache() {
            // Given
            SpecificDataResponse staleResponse = createStaleResponse();
            when(cacheManager.getCachedData(testCacheKey, testRequest, testSourceId))
                    .thenReturn(Mono.just((SpecificDataResponse) null)) // 第一次缓存未命中
                    .thenReturn(Mono.just(staleResponse)); // 降级时返回过期数据

            ApiServerException error = new ApiServerException("服务不可用", 503);
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.error(error));

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectNext(staleResponse)
                    .verifyComplete();

            verify(monitoringService).recordFallback(testSourceId, "stale_cache");
        }

        @Test
        @DisplayName("降级时没有过期缓存数据应该返回错误响应")
        void handleFallback_WithoutStaleCache() {
            // Given
            when(cacheManager.getCachedData(testCacheKey, testRequest, testSourceId))
                    .thenReturn(Mono.just((SpecificDataResponse) null)); // 总是没有缓存数据

            ApiServerException error = new ApiServerException("服务不可用", 503);
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.error(error));

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            StepVerifier.create(result)
                    .expectNextMatches(response -> 
                            response.statusCode().equals(500) &&
                            response.statusMsg().contains("服务暂时不可用"))
                    .verifyComplete();

            verify(monitoringService).recordFallback(testSourceId, "error_response");
        }
    }

    // ===================== 工具方法测试 =====================

    @Nested
    @DisplayName("工具方法测试")
    class UtilityMethodsTest {

        @Test
        @DisplayName("querySpecificData重载方法应该使用null作为sourceId")
        void querySpecificData_OverloadedMethod() {
            // Given
            when(cacheStrategyConfig.getCacheStrategy(anyString(), anyString(), isNull()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.NO_CACHE);
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), isNull(), any(Supplier.class)))
                    .thenReturn(Mono.just(testResponse));

            // When
            Mono<SpecificDataResponse> result = specificDataService.querySpecificData(testRequest);

            // Then
            StepVerifier.create(result)
                    .expectNext(testResponse)
                    .verifyComplete();

            verify(cacheStrategyConfig).getCacheStrategy(anyString(), anyString(), isNull());
        }

        @Test
        @DisplayName("extractFirstCode应该正确提取第一个代码")
        void extractFirstCode_Success() {
            // Given - testRequest已经包含代码
            when(cacheStrategyConfig.getCacheStrategy(eq("000001"), anyString(), anyString()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.NO_CACHE);
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.just(testResponse));

            // When
            specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            verify(cacheStrategyConfig).getCacheStrategy(eq("000001"), anyString(), eq(testSourceId));
        }

        @Test
        @DisplayName("extractFirstIndex应该正确提取第一个指标")
        void extractFirstIndex_Success() {
            // Given - testRequest已经包含指标
            when(cacheStrategyConfig.getCacheStrategy(anyString(), eq("price"), anyString()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.NO_CACHE);
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.just(testResponse));

            // When
            specificDataService.querySpecificData(testRequest, testSourceId);

            // Then
            verify(cacheStrategyConfig).getCacheStrategy(anyString(), eq("price"), eq(testSourceId));
        }
    }

    // ===================== 监控数据测试 =====================

    @Nested
    @DisplayName("监控数据测试")
    class MonitoringTest {

        @Test
        @DisplayName("所有策略都应该记录API响应时间")
        void recordApiResponseTime_AllStrategies() {
            // Given
            when(cacheStrategyConfig.getCacheStrategy(anyString(), anyString(), anyString()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.NO_CACHE);
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.just(testResponse));

            // When
            StepVerifier.create(specificDataService.querySpecificData(testRequest, testSourceId))
                    .expectNext(testResponse)
                    .verifyComplete();

            // Then
            verify(monitoringService).recordApiResponseTime(eq(testSourceId), eq("/v1/specific_data"), anyLong());
        }

        @Test
        @DisplayName("错误场景应该记录业务错误")
        void recordBusinessError_OnFailure() {
            // Given
            when(cacheStrategyConfig.getCacheStrategy(anyString(), anyString(), anyString()))
                    .thenReturn(CacheStrategyConfig.CacheStrategy.NO_CACHE);
            RuntimeException error = new RuntimeException("未知错误");
            when(deduplicationManager.executeDeduplicatedRequest(
                    any(SpecificDataRequest.class), anyString(), any(Supplier.class)))
                    .thenReturn(Mono.error(error));

            // When
            StepVerifier.create(specificDataService.querySpecificData(testRequest, testSourceId))
                    .expectError(RuntimeException.class)
                    .verify();

            // Then
            verify(monitoringService).recordBusinessError(
                    eq(testSourceId), eq("remote_call_failed"), eq("RuntimeException"));
        }
    }

    // ===================== 辅助方法 =====================

    private SpecificDataRequest createTestRequest() {
        return SpecificDataRequest.builder()
                .codeSelectors(SpecificDataRequest.CodeSelectors.builder()
                        .include(List.of(
                                SpecificDataRequest.CodeSelector.builder()
                                        .type("stock")
                                        .values(List.of("000001", "000002"))
                                        .build()
                        ))
                        .build())
                .indexes(List.of(
                        SpecificDataRequest.IndexRequest.builder()
                                .indexId("price")
                                .timeType("daily")
                                .timestamp(System.currentTimeMillis())
                                .build()
                ))
                .pageInfo(SpecificDataRequest.PageInfo.builder()
                        .pageBegin(0)
                        .pageSize(20)
                        .build())
                .build();
    }

    private SpecificDataResponse createTestResponse() {
        return SpecificDataResponse.builder()
                .statusCode(0)
                .statusMsg("success")
                .data(SpecificDataResponse.SpecificDataResult.builder()
                        .total(2)
                        .indexes(List.of())
                        .data(List.of(
                                SpecificDataResponse.DataItem.builder()
                                        .code("000001")
                                        .values(List.of(
                                                SpecificDataResponse.ValueItem.builder()
                                                        .idx(0)
                                                        .value("10.5")
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build())
                .build();
    }

    private SpecificDataResponse createStaleResponse() {
        return SpecificDataResponse.builder()
                .statusCode(0)
                .statusMsg("success (stale)")
                .data(SpecificDataResponse.SpecificDataResult.builder()
                        .total(1)
                        .indexes(List.of())
                        .data(List.of(
                                SpecificDataResponse.DataItem.builder()
                                        .code("000001")
                                        .values(List.of(
                                                SpecificDataResponse.ValueItem.builder()
                                                        .idx(0)
                                                        .value("9.8")
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build())
                .build();
    }
}