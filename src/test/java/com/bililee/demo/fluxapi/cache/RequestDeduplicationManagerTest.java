package com.bililee.demo.fluxapi.cache;

import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import com.bililee.demo.fluxapi.response.ApiStatus;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequestDeduplicationManager 单元测试
 * 测试请求去重管理器的核心功能：请求合并、结果广播、并发处理等
 */
@DisplayName("请求去重管理器测试")
class RequestDeduplicationManagerTest {

    private RequestDeduplicationManager deduplicationManager;
    private SpecificDataRequest testRequest;
    private SpecificDataResponse testResponse;
    private final String testSourceId = "test-source";

    @BeforeEach
    void setUp() {
        deduplicationManager = new RequestDeduplicationManager();
        testRequest = createTestRequest();
        testResponse = createTestResponse();
    }

    // ===================== 基本去重功能测试 =====================

    @Nested
    @DisplayName("基本去重功能测试")
    class BasicDeduplicationTest {

        @Test
        @DisplayName("单个请求应该正常执行")
        void executeDeduplicatedRequest_SingleRequest_ExecutesNormally() {
            // Given
            AtomicInteger executionCount = new AtomicInteger(0);
            Supplier<Mono<SpecificDataResponse>> supplier = () -> {
                executionCount.incrementAndGet();
                return Mono.just(testResponse);
            };

            // When
            Mono<SpecificDataResponse> result = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier);

            // Then
            StepVerifier.create(result)
                    .expectNext(testResponse)
                    .verifyComplete();

            assertThat(executionCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("相同请求应该被去重")
        void executeDeduplicatedRequest_DuplicateRequests_Deduplicated() throws InterruptedException {
            // Given
            AtomicInteger executionCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(1);
            
            Supplier<Mono<SpecificDataResponse>> supplier = () -> {
                executionCount.incrementAndGet();
                return Mono.fromCallable(() -> {
                    try {
                        latch.await(5, TimeUnit.SECONDS); // 等待信号
                        return testResponse;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            };

            // When - 同时发起3个相同请求
            Mono<SpecificDataResponse> result1 = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier);
            Mono<SpecificDataResponse> result2 = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier);
            Mono<SpecificDataResponse> result3 = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier);

            // 释放信号让请求完成
            latch.countDown();

            // Then
            StepVerifier.create(Mono.zip(result1, result2, result3))
                    .expectNextMatches(tuple -> 
                            tuple.getT1().equals(testResponse) &&
                            tuple.getT2().equals(testResponse) &&
                            tuple.getT3().equals(testResponse))
                    .verifyComplete();

            // 验证只执行了一次
            assertThat(executionCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("不同请求应该分别执行")
        void executeDeduplicatedRequest_DifferentRequests_ExecutedSeparately() {
            // Given
            SpecificDataRequest differentRequest = createDifferentRequest();
            SpecificDataResponse differentResponse = createDifferentResponse();
            AtomicInteger executionCount = new AtomicInteger(0);

            Supplier<Mono<SpecificDataResponse>> supplier1 = () -> {
                executionCount.incrementAndGet();
                return Mono.just(testResponse);
            };

            Supplier<Mono<SpecificDataResponse>> supplier2 = () -> {
                executionCount.incrementAndGet();
                return Mono.just(differentResponse);
            };

            // When
            Mono<SpecificDataResponse> result1 = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier1);
            Mono<SpecificDataResponse> result2 = deduplicationManager.executeDeduplicatedRequest(
                    differentRequest, testSourceId, supplier2);

            // Then
            StepVerifier.create(Mono.zip(result1, result2))
                    .expectNextMatches(tuple -> 
                            tuple.getT1().equals(testResponse) &&
                            tuple.getT2().equals(differentResponse))
                    .verifyComplete();

            // 验证两个请求都执行了
            assertThat(executionCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("不同sourceId的相同请求应该分别执行")
        void executeDeduplicatedRequest_DifferentSourceId_ExecutedSeparately() {
            // Given
            String anotherSourceId = "another-source";
            AtomicInteger executionCount = new AtomicInteger(0);

            Supplier<Mono<SpecificDataResponse>> supplier = () -> {
                executionCount.incrementAndGet();
                return Mono.just(testResponse);
            };

            // When
            Mono<SpecificDataResponse> result1 = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier);
            Mono<SpecificDataResponse> result2 = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, anotherSourceId, supplier);

            // Then
            StepVerifier.create(Mono.zip(result1, result2))
                    .expectNextMatches(tuple -> 
                            tuple.getT1().equals(testResponse) &&
                            tuple.getT2().equals(testResponse))
                    .verifyComplete();

            // 验证两个请求都执行了（因为sourceId不同）
            assertThat(executionCount.get()).isEqualTo(2);
        }
    }

    // ===================== 错误处理测试 =====================

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTest {

        @Test
        @DisplayName("请求执行失败应该传播错误给所有等待的请求")
        void executeDeduplicatedRequest_ExecutionFailure_PropagatesErrorToAllWaiters() throws InterruptedException {
            // Given
            AtomicInteger executionCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException testError = new RuntimeException("执行失败");

            Supplier<Mono<SpecificDataResponse>> supplier = () -> {
                executionCount.incrementAndGet();
                return Mono.fromCallable(() -> {
                    try {
                        latch.await(5, TimeUnit.SECONDS);
                        throw testError;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            };

            // When - 同时发起3个相同请求
            Mono<SpecificDataResponse> result1 = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier);
            Mono<SpecificDataResponse> result2 = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier);
            Mono<SpecificDataResponse> result3 = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier);

            // 释放信号让请求失败
            latch.countDown();

            // Then - 所有请求都应该收到相同的错误
            StepVerifier.create(result1)
                    .expectError(RuntimeException.class)
                    .verify();

            StepVerifier.create(result2)
                    .expectError(RuntimeException.class)
                    .verify();

            StepVerifier.create(result3)
                    .expectError(RuntimeException.class)
                    .verify();

            // 验证只执行了一次
            assertThat(executionCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("请求超时应该正确处理")
        void executeDeduplicatedRequest_Timeout_HandledCorrectly() {
            // Given
            Supplier<Mono<SpecificDataResponse>> supplier = () -> 
                    Mono.delay(Duration.ofSeconds(60)) // 模拟超长时间执行
                            .then(Mono.just(testResponse));

            // When
            Mono<SpecificDataResponse> result = deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier);

            // Then - 应该在30秒内超时
            StepVerifier.create(result)
                    .expectError()
                    .verify(Duration.ofSeconds(35));
        }

        @Test
        @DisplayName("供应商为null应该抛出异常")
        void executeDeduplicatedRequest_NullSupplier_ThrowsException() {
            // When & Then
            StepVerifier.create(deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, null))
                    .expectError(NullPointerException.class)
                    .verify();
        }
    }

    // ===================== 并发安全测试 =====================

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencySafetyTest {

        @Test
        @DisplayName("高并发场景下应该正确去重")
        void executeDeduplicatedRequest_HighConcurrency_CorrectDeduplication() throws InterruptedException {
            // Given
            AtomicInteger executionCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch executionLatch = new CountDownLatch(1);
            
            Supplier<Mono<SpecificDataResponse>> supplier = () -> {
                executionCount.incrementAndGet();
                return Mono.fromCallable(() -> {
                    try {
                        startLatch.countDown(); // 通知开始执行
                        executionLatch.await(5, TimeUnit.SECONDS); // 等待执行完成信号
                        return testResponse;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            };

            // When - 启动100个并发请求
            int concurrencyLevel = 100;
            @SuppressWarnings("unchecked")
            Mono<SpecificDataResponse>[] results = new Mono[concurrencyLevel];
            
            for (int i = 0; i < concurrencyLevel; i++) {
                results[i] = deduplicationManager.executeDeduplicatedRequest(
                        testRequest, testSourceId, supplier);
            }

            // 等待首个请求开始执行
            startLatch.await(5, TimeUnit.SECONDS);
            // 释放执行信号
            executionLatch.countDown();

            // Then
            Mono<List<SpecificDataResponse>> combinedResult = Mono.zip(List.of(results), 
                    array -> List.of((SpecificDataResponse[]) array));

            StepVerifier.create(combinedResult)
                    .expectNextMatches(responseList -> {
                        // 验证所有响应都相同
                        return responseList.size() == concurrencyLevel &&
                               responseList.stream().allMatch(testResponse::equals);
                    })
                    .verifyComplete();

            // 验证只执行了一次
            assertThat(executionCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("并发执行不同请求应该互不影响")
        void executeDeduplicatedRequest_ConcurrentDifferentRequests_NoInterference() {
            // Given
            AtomicInteger execution1Count = new AtomicInteger(0);
            AtomicInteger execution2Count = new AtomicInteger(0);
            
            SpecificDataRequest request1 = testRequest;
            SpecificDataRequest request2 = createDifferentRequest();
            SpecificDataResponse response2 = createDifferentResponse();

            Supplier<Mono<SpecificDataResponse>> supplier1 = () -> {
                execution1Count.incrementAndGet();
                return Mono.just(testResponse).delayElement(Duration.ofMillis(100));
            };

            Supplier<Mono<SpecificDataResponse>> supplier2 = () -> {
                execution2Count.incrementAndGet();
                return Mono.just(response2).delayElement(Duration.ofMillis(100));
            };

            // When - 同时执行两种不同的请求，每种请求有多个实例
            Mono<SpecificDataResponse> result1a = deduplicationManager.executeDeduplicatedRequest(
                    request1, testSourceId, supplier1);
            Mono<SpecificDataResponse> result1b = deduplicationManager.executeDeduplicatedRequest(
                    request1, testSourceId, supplier1);
            Mono<SpecificDataResponse> result2a = deduplicationManager.executeDeduplicatedRequest(
                    request2, testSourceId, supplier2);
            Mono<SpecificDataResponse> result2b = deduplicationManager.executeDeduplicatedRequest(
                    request2, testSourceId, supplier2);

            // Then
            StepVerifier.create(Mono.zip(result1a, result1b, result2a, result2b))
                    .expectNextMatches(tuple -> 
                            tuple.getT1().equals(testResponse) &&
                            tuple.getT2().equals(testResponse) &&
                            tuple.getT3().equals(response2) &&
                            tuple.getT4().equals(response2))
                    .verifyComplete();

            // 验证每种请求只执行了一次
            assertThat(execution1Count.get()).isEqualTo(1);
            assertThat(execution2Count.get()).isEqualTo(1);
        }
    }



    // ===================== 请求键生成测试 =====================

    @Nested
    @DisplayName("请求键生成测试")
    class RequestKeyGenerationTest {

        @Test
        @DisplayName("相同请求应该生成相同的键")
        void generateRequestKey_SameRequest_SameKey() {
            // Given
            AtomicInteger keyComparisons = new AtomicInteger(0);

            // 通过去重管理器间接测试键生成（因为generateRequestKey是私有方法）
            Supplier<Mono<SpecificDataResponse>> supplier = () -> {
                keyComparisons.incrementAndGet();
                return Mono.just(testResponse);
            };

            // When - 发起两个相同请求
            deduplicationManager.executeDeduplicatedRequest(testRequest, testSourceId, supplier);
            deduplicationManager.executeDeduplicatedRequest(testRequest, testSourceId, supplier);

            // Then - 应该只执行一次（说明键相同，被去重了）
            assertThat(keyComparisons.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("不同分页参数应该生成不同的键")
        void generateRequestKey_DifferentPaging_DifferentKey() {
            // Given
            SpecificDataRequest requestPage1 = SpecificDataRequest.builder()
                    .codeSelectors(testRequest.codeSelectors())
                    .indexes(testRequest.indexes())
                    .pageInfo(SpecificDataRequest.PageInfo.builder()
                            .pageBegin(0)
                            .pageSize(20)
                            .build())
                    .build();

            SpecificDataRequest requestPage2 = SpecificDataRequest.builder()
                    .codeSelectors(testRequest.codeSelectors())
                    .indexes(testRequest.indexes())
                    .pageInfo(SpecificDataRequest.PageInfo.builder()
                            .pageBegin(20)
                            .pageSize(20)
                            .build())
                    .build();

            AtomicInteger executionCount = new AtomicInteger(0);
            Supplier<Mono<SpecificDataResponse>> supplier = () -> {
                executionCount.incrementAndGet();
                return Mono.just(testResponse);
            };

            // When
            deduplicationManager.executeDeduplicatedRequest(requestPage1, testSourceId, supplier);
            deduplicationManager.executeDeduplicatedRequest(requestPage2, testSourceId, supplier);

            // Then - 应该执行两次（说明键不同，没有被去重）
            assertThat(executionCount.get()).isEqualTo(2);
        }
    }

    // ===================== 内存管理测试 =====================

    @Nested
    @DisplayName("内存管理测试")
    class MemoryManagementTest {

        @Test
        @DisplayName("请求完成后应该清理相关资源")
        void executeDeduplicatedRequest_CompletesRequest_CleansUpResources() {
            // Given
            Supplier<Mono<SpecificDataResponse>> supplier = () -> Mono.just(testResponse);

            // When
            StepVerifier.create(deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, supplier))
                    .expectNext(testResponse)
                    .verifyComplete();

            // 再次执行相同请求，应该重新执行而不是等待（说明之前的资源已清理）
            AtomicInteger executionCount = new AtomicInteger(0);
            Supplier<Mono<SpecificDataResponse>> countingSupplier = () -> {
                executionCount.incrementAndGet();
                return Mono.just(testResponse);
            };

            StepVerifier.create(deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, countingSupplier))
                    .expectNext(testResponse)
                    .verifyComplete();

            // Then
            assertThat(executionCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("请求失败后应该清理相关资源")
        void executeDeduplicatedRequest_FailsRequest_CleansUpResources() {
            // Given
            RuntimeException testError = new RuntimeException("测试错误");
            Supplier<Mono<SpecificDataResponse>> failingSupplier = () -> Mono.error(testError);

            // When
            StepVerifier.create(deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, failingSupplier))
                    .expectError(RuntimeException.class)
                    .verify();

            // 再次执行相同请求，应该重新执行
            AtomicInteger executionCount = new AtomicInteger(0);
            Supplier<Mono<SpecificDataResponse>> countingSupplier = () -> {
                executionCount.incrementAndGet();
                return Mono.just(testResponse);
            };

            StepVerifier.create(deduplicationManager.executeDeduplicatedRequest(
                    testRequest, testSourceId, countingSupplier))
                    .expectNext(testResponse)
                    .verifyComplete();

            // Then
            assertThat(executionCount.get()).isEqualTo(1);
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

    private SpecificDataRequest createDifferentRequest() {
        return SpecificDataRequest.builder()
                .codeSelectors(SpecificDataRequest.CodeSelectors.builder()
                        .include(List.of(
                                SpecificDataRequest.CodeSelector.builder()
                                        .type("bond")
                                        .values(List.of("123456"))
                                        .build()
                        ))
                        .build())
                .indexes(List.of(
                        SpecificDataRequest.IndexRequest.builder()
                                .indexId("yield")
                                .timeType("daily")
                                .build()
                ))
                .pageInfo(SpecificDataRequest.PageInfo.builder()
                        .pageBegin(0)
                        .pageSize(10)
                        .build())
                .build();
    }

    private SpecificDataResponse createTestResponse() {
        return SpecificDataResponse.builder()
                .statusCode(ApiStatus.SUCCESS_CODE)
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

    private SpecificDataResponse createDifferentResponse() {
        return SpecificDataResponse.builder()
                .statusCode(ApiStatus.SUCCESS_CODE)
                .statusMsg("success")
                .data(SpecificDataResponse.SpecificDataResult.builder()
                        .total(1)
                        .indexes(List.of())
                        .data(List.of(
                                SpecificDataResponse.DataItem.builder()
                                        .code("123456")
                                        .values(List.of(
                                                SpecificDataResponse.ValueItem.builder()
                                                        .idx(0)
                                                        .value("3.5")
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build())
                .build();
    }
}