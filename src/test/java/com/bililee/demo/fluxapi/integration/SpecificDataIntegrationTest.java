package com.bililee.demo.fluxapi.integration;

import com.bililee.demo.fluxapi.FluxApiApplication;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpecificData 集成测试
 * 测试完整的端到端流程：HTTP请求 -> Controller -> Service -> Cache -> Remote Client
 */
@SpringBootTest(
        classes = FluxApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "logging.level.com.bililee.demo.fluxapi=DEBUG"
        }
)
@DisplayName("特定数据集成测试")
class SpecificDataIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockWebServer mockRemoteServer;
    private SpecificDataRequest testRequest;
    private SpecificDataResponse testResponse;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
        // 启动Mock服务器
        mockRemoteServer = new MockWebServer();
        mockRemoteServer.start();
        
        // 配置远程服务地址指向Mock服务器
        String baseUrl = mockRemoteServer.url("/").toString();
        registry.add("remote.service.baseUrl", () -> baseUrl);
        registry.add("remote.service.endpoint", () -> "/v1/specific_data");
        registry.add("remote.service.timeout", () -> "PT10S");
        registry.add("remote.service.maxRetries", () -> "3");
        registry.add("remote.service.retryDelay", () -> "PT500MS");
        
        // 配置缓存策略为测试模式
        registry.add("cache.strategy.default", () -> "PASSIVE");
        registry.add("cache.strategy.ttl", () -> "PT30S");
        registry.add("cache.strategy.allowStaleData", () -> "true");
    }

    @BeforeEach
    void setUp() {
        testRequest = createTestRequest();
        testResponse = createTestResponse();
        
        // 配置WebTestClient超时
        webTestClient = webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockRemoteServer != null) {
            mockRemoteServer.shutdown();
        }
    }

    // ===================== 端到端成功流程测试 =====================

    @Nested
    @DisplayName("端到端成功流程测试")
    class EndToEndSuccessTest {

        @Test
        @DisplayName("完整的成功请求流程应该正常工作")
        void completeSuccessFlow_ShouldWork() throws Exception {
            // Given - Mock远程服务返回成功响应
            String responseJson = objectMapper.writeValueAsString(testResponse);
            mockRemoteServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseJson));

            // When
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "integration-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    // Then
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> {
                        assertThat(response.statusCode()).isEqualTo(0);
                        assertThat(response.statusMsg()).isEqualTo("success");
                        assertThat(response.data()).isNotNull();
                        assertThat(response.data().total()).isEqualTo(2);
                        assertThat(response.data().data()).hasSize(2);
                    });

            // 验证远程服务被调用
            RecordedRequest recordedRequest = mockRemoteServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/v1/specific_data");
        }

        @Test
        @DisplayName("缓存功能应该正常工作")
        void cacheFeature_ShouldWork() throws Exception {
            // Given - Mock远程服务返回成功响应
            String responseJson = objectMapper.writeValueAsString(testResponse);
            mockRemoteServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseJson));

            // When - 第一次请求
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "cache-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> assertThat(response.statusCode()).isEqualTo(0));

            // When - 第二次相同请求（应该使用缓存）
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "cache-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> assertThat(response.statusCode()).isEqualTo(0));

            // Then - 验证远程服务只被调用一次
            assertThat(mockRemoteServer.getRequestCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("不同Source-Id应该使用不同的缓存策略")
        void differentSourceId_ShouldUseDifferentCacheStrategy() throws Exception {
            // Given
            String responseJson = objectMapper.writeValueAsString(testResponse);
            mockRemoteServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseJson));
            mockRemoteServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseJson));

            // When - 使用不同的Source-Id发起相同请求
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "source1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    .expectStatus().isOk();

            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "source2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    .expectStatus().isOk();

            // Then - 应该分别调用远程服务
            assertThat(mockRemoteServer.getRequestCount()).isEqualTo(2);
        }
    }

    // ===================== 远程服务异常处理集成测试 =====================

    @Nested
    @DisplayName("远程服务异常处理集成测试")
    class RemoteServiceErrorIntegrationTest {

        @Test
        @DisplayName("远程服务超时应该触发降级处理")
        void remoteServiceTimeout_ShouldTriggerFallback() throws Exception {
            // Given - Mock远程服务无响应（模拟超时）
            mockRemoteServer.enqueue(new MockResponse()
                    .setBodyDelay(15, java.util.concurrent.TimeUnit.SECONDS)); // 超过配置的10秒超时

            // When
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "timeout-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    // Then - 应该返回降级响应
                    .expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> {
                        assertThat(response.statusCode()).isEqualTo(500);
                        assertThat(response.statusMsg()).contains("服务内部错误");
                    });
        }

        @Test
        @DisplayName("远程服务5xx错误应该触发重试和降级")
        void remoteService5xxError_ShouldTriggerRetryAndFallback() throws Exception {
            // Given - Mock远程服务连续返回500错误
            mockRemoteServer.enqueue(new MockResponse().setResponseCode(500));
            mockRemoteServer.enqueue(new MockResponse().setResponseCode(500));
            mockRemoteServer.enqueue(new MockResponse().setResponseCode(500));
            mockRemoteServer.enqueue(new MockResponse().setResponseCode(500));

            // When
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "5xx-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    // Then
                    .expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> {
                        assertThat(response.statusCode()).isEqualTo(500);
                        assertThat(response.statusMsg()).contains("服务内部错误");
                    });

            // 验证进行了重试（1次原始请求 + 3次重试 = 4次总请求）
            assertThat(mockRemoteServer.getRequestCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("远程服务4xx错误应该不重试并直接返回错误")
        void remoteService4xxError_ShouldNotRetryAndReturnError() throws Exception {
            // Given - Mock远程服务返回400错误
            mockRemoteServer.enqueue(new MockResponse().setResponseCode(400));

            // When
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "4xx-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    // Then
                    .expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> {
                        assertThat(response.statusCode()).isEqualTo(500);
                        assertThat(response.statusMsg()).contains("服务内部错误");
                    });

            // 验证没有重试（只有1次请求）
            assertThat(mockRemoteServer.getRequestCount()).isEqualTo(1);
        }
    }

    // ===================== 请求参数验证集成测试 =====================

    @Nested
    @DisplayName("请求参数验证集成测试")
    class RequestValidationIntegrationTest {

        @Test
        @DisplayName("无效请求参数应该返回400错误")
        void invalidRequestParameters_ShouldReturn400() {
            // Given - 创建无效请求
            String invalidRequest = """
                {
                    "code_selectors": {
                        "include": []
                    },
                    "indexes": [],
                    "page_info": {
                        "page_begin": -1,
                        "page_size": 0
                    }
                }""";

            // When
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "validation-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(invalidRequest)
                    .exchange()
                    // Then
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("缺少必需字段应该返回400错误")
        void missingRequiredFields_ShouldReturn400() {
            // Given - 缺少code_selectors的请求
            String incompleteRequest = """
                {
                    "indexes": [
                        {
                            "index_id": "price"
                        }
                    ],
                    "page_info": {
                        "page_begin": 0,
                        "page_size": 20
                    }
                }""";

            // When
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "missing-fields-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(incompleteRequest)
                    .exchange()
                    // Then
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("无效JSON格式应该返回400错误")
        void invalidJsonFormat_ShouldReturn400() {
            // When
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "invalid-json-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("invalid json content")
                    .exchange()
                    // Then
                    .expectStatus().isBadRequest();
        }
    }

    // ===================== 并发请求集成测试 =====================

    @Nested
    @DisplayName("并发请求集成测试")
    class ConcurrentRequestIntegrationTest {

        @Test
        @DisplayName("并发相同请求应该被正确去重")
        void concurrentSameRequests_ShouldBeDeduplicatedCorrectly() throws Exception {
            // Given - Mock远程服务延迟响应以确保并发
            String responseJson = objectMapper.writeValueAsString(testResponse);
            mockRemoteServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseJson)
                    .setBodyDelay(1, java.util.concurrent.TimeUnit.SECONDS));

            // When - 同时发起多个相同请求
            var request1 = webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "concurrent-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange();

            var request2 = webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "concurrent-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange();

            var request3 = webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "concurrent-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange();

            // Then - 所有请求都应该成功
            request1.expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> assertThat(response.statusCode()).isEqualTo(0));

            request2.expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> assertThat(response.statusCode()).isEqualTo(0));

            request3.expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> assertThat(response.statusCode()).isEqualTo(0));

            // 验证远程服务只被调用一次（请求去重生效）
            assertThat(mockRemoteServer.getRequestCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("并发不同请求应该分别处理")
        void concurrentDifferentRequests_ShouldBeHandledSeparately() throws Exception {
            // Given
            SpecificDataRequest differentRequest = createDifferentRequest();
            SpecificDataResponse differentResponse = createDifferentResponse();
            
            String responseJson1 = objectMapper.writeValueAsString(testResponse);
            String responseJson2 = objectMapper.writeValueAsString(differentResponse);
            
            mockRemoteServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseJson1));
            mockRemoteServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseJson2));

            // When - 同时发起不同请求
            var request1 = webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "different-request-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange();

            var request2 = webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "different-request-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(differentRequest)
                    .exchange();

            // Then
            request1.expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> assertThat(response.data().data().get(0).code()).isEqualTo("000001"));

            request2.expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> assertThat(response.data().data().get(0).code()).isEqualTo("123456"));

            // 验证远程服务被调用两次
            assertThat(mockRemoteServer.getRequestCount()).isEqualTo(2);
        }
    }

    // ===================== 降级策略集成测试 =====================

    @Nested
    @DisplayName("降级策略集成测试")
    class FallbackStrategyIntegrationTest {

        @Test
        @DisplayName("有过期缓存时应该返回过期数据作为降级")
        void withStaleCache_ShouldReturnStaleDataAsFallback() throws Exception {
            // Given - 先让一个成功请求创建缓存
            String responseJson = objectMapper.writeValueAsString(testResponse);
            mockRemoteServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseJson));

            // 第一次请求创建缓存
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "fallback-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    .expectStatus().isOk();

            // 等待缓存过期（需要根据实际配置调整）
            Thread.sleep(2000);

            // 配置远程服务失败
            mockRemoteServer.enqueue(new MockResponse().setResponseCode(500));

            // When - 第二次请求（远程服务失败，应该返回过期缓存）
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "fallback-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    // Then - 应该返回缓存的数据
                    .expectStatus().isOk()
                    .expectBody(SpecificDataResponse.class)
                    .value(response -> {
                        // 即使远程服务失败，也应该返回缓存的成功数据
                        assertThat(response.statusCode()).isEqualTo(0);
                        assertThat(response.data()).isNotNull();
                    });
        }
    }

    // ===================== HTTP协议层集成测试 =====================

    @Nested
    @DisplayName("HTTP协议层集成测试")
    class HttpProtocolIntegrationTest {

        @Test
        @DisplayName("缺少Content-Type应该返回错误")
        void missingContentType_ShouldReturnError() throws Exception {
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "content-type-test")
                    .bodyValue(objectMapper.writeValueAsString(testRequest))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("不支持的HTTP方法应该返回405错误")
        void unsupportedHttpMethod_ShouldReturn405() {
            webTestClient.get()
                    .uri("/v1/specific_data")
                    .header("Source-Id", "method-test")
                    .exchange()
                    .expectStatus().isEqualTo(405);
        }

        @Test
        @DisplayName("不存在的路径应该返回404错误")
        void nonExistentPath_ShouldReturn404() {
            webTestClient.post()
                    .uri("/v1/nonexistent")
                    .header("Source-Id", "path-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(testRequest)
                    .exchange()
                    .expectStatus().isNotFound();
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
                                        .build(),
                                SpecificDataResponse.DataItem.builder()
                                        .code("000002")
                                        .values(List.of(
                                                SpecificDataResponse.ValueItem.builder()
                                                        .idx(0)
                                                        .value("12.8")
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build())
                .build();
    }

    private SpecificDataResponse createDifferentResponse() {
        return SpecificDataResponse.builder()
                .statusCode(0)
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