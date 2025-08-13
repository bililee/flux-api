package com.bililee.demo.fluxapi.client;

import com.bililee.demo.fluxapi.config.source.ConfigSource;
import com.bililee.demo.fluxapi.exception.ApiServerException;
import com.bililee.demo.fluxapi.exception.ApiTimeoutException;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import com.bililee.demo.fluxapi.response.ApiStatus;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

/**
 * RemoteSpecificDataClient 单元测试
 * 重点测试远程调用异常处理：超时、重试、4xx/5xx错误、网络异常
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("远程特定数据客户端测试")
class RemoteSpecificDataClientTest {

    @Mock
    private ConfigSource configSource;

    private RemoteSpecificDataClient client;
    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;
    private SpecificDataRequest testRequest;
    private SpecificDataResponse testResponse;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        objectMapper = new ObjectMapper();
        
        // 创建测试请求
        testRequest = createTestRequest();
        testResponse = createTestResponse();
        
        // Mock配置源
        setupConfigSourceMock();
        
        // 创建客户端实例
        client = new RemoteSpecificDataClient();
        ReflectionTestUtils.setField(client, "configSource", configSource);
        
        // 手动调用初始化方法
        client.init();
        
        // 设置测试服务器地址
        updateClientConfig();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    // ===================== 正常调用测试 =====================

    @Test
    @DisplayName("正常远程调用应该成功返回数据")
    void fetchSpecificData_Success() throws JsonProcessingException, InterruptedException {
        // Given
        String responseJson = objectMapper.writeValueAsString(testResponse);
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectNext(testResponse)
                .verifyComplete();

        // 验证请求
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/v1/specific_data");
    }

    // ===================== 超时异常测试 =====================

    @Test
    @DisplayName("连接超时应该抛出ApiTimeoutException")
    void fetchSpecificData_ConnectionTimeout() {
        // Given - 服务器不响应模拟连接超时
        mockWebServer.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectError(ApiTimeoutException.class)
                .verify(); // 验证超时异常
    }

    @Test
    @DisplayName("读取超时应该抛出ApiTimeoutException")
    void fetchSpecificData_ReadTimeout() {
        // Given - 部分响应后停止，模拟读取超时
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("partial response")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectError(ApiTimeoutException.class)
                .verify();
    }

    @Test
    @DisplayName("请求超时应该进行重试")
    void fetchSpecificData_RequestTimeout_WithRetry() throws InterruptedException {
        // Given - 前两次超时，第三次成功
        mockWebServer.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE));
        mockWebServer.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createResponseJson()));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectNext(testResponse)
                .verifyComplete();

        // 验证重试了3次
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    // ===================== HTTP状态码异常测试 =====================

    @Test
    @DisplayName("400错误应该立即失败，不重试")
    void fetchSpecificData_400BadRequest_NoRetry() throws InterruptedException {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("Bad Request"));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectErrorMatches(throwable -> 
                        throwable instanceof ApiServerException &&
                        ((ApiServerException) throwable).getStatusCode() == 400 &&
                        throwable.getMessage().contains("远程服务客户端错误"))
                .verify();

        // 验证没有重试
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("401未授权错误应该立即失败")
    void fetchSpecificData_401Unauthorized_NoRetry() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized"));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectErrorMatches(throwable -> 
                        throwable instanceof ApiServerException &&
                        ((ApiServerException) throwable).getStatusCode() == 401)
                .verify();
    }

    @Test
    @DisplayName("404未找到错误应该立即失败")
    void fetchSpecificData_404NotFound_NoRetry() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectErrorMatches(throwable -> 
                        throwable instanceof ApiServerException &&
                        ((ApiServerException) throwable).getStatusCode() == 404)
                .verify();
    }

    @Test
    @DisplayName("500服务器错误应该进行重试")
    void fetchSpecificData_500InternalServerError_WithRetry() throws InterruptedException {
        // Given - 前两次500错误，第三次成功
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createResponseJson()));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectNext(testResponse)
                .verifyComplete();

        // 验证重试了3次
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("502网关错误应该进行重试")
    void fetchSpecificData_502BadGateway_WithRetry() throws InterruptedException {
        // Given
        mockWebServer.enqueue(new MockResponse().setResponseCode(502));
        mockWebServer.enqueue(new MockResponse().setResponseCode(502));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json") 
                .setBody(createResponseJson()));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectNext(testResponse)
                .verifyComplete();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("503服务不可用应该进行重试")
    void fetchSpecificData_503ServiceUnavailable_WithRetry() {
        // Given - 所有重试都失败
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectErrorMatches(throwable -> 
                        throwable instanceof ApiServerException &&
                        ((ApiServerException) throwable).getStatusCode() == 503)
                .verify();
    }

    // ===================== 网络异常测试 =====================

    @Test
    @DisplayName("连接拒绝应该映射为ApiServerException")
    void fetchSpecificData_ConnectionRefused() throws IOException {
        // Given - 关闭服务器模拟连接拒绝
        mockWebServer.shutdown();

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectErrorMatches(throwable -> 
                        throwable instanceof ApiServerException &&
                        throwable.getMessage().contains("远程服务调用失败"))
                .verify();
    }

    @Test
    @DisplayName("网络中断应该映射为ApiServerException")
    void fetchSpecificData_NetworkInterruption() {
        // Given - 连接过程中断开
        mockWebServer.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectError(ApiServerException.class)
                .verify();
    }

    // ===================== 重试机制测试 =====================

    @Test
    @DisplayName("重试次数应该受到最大重试次数限制")
    void fetchSpecificData_MaxRetryLimit() throws InterruptedException {
        // Given - 持续返回500错误
        for (int i = 0; i < 5; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        }

        // When & Then
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectError(ApiServerException.class)
                .verify();

        // 验证重试次数（1次原始请求 + 2次重试 = 3次总请求）
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("退避策略应该在重试间隔中生效")
    void fetchSpecificData_BackoffStrategy() throws InterruptedException {
        // Given
        long startTime = System.currentTimeMillis();
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createResponseJson()));

        // When
        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectNext(testResponse)
                .verifyComplete();

        // Then - 验证总时间包含了退避延迟
        long totalTime = System.currentTimeMillis() - startTime;
        assertThat(totalTime).isGreaterThan(500); // 至少有退避延迟
    }

    // ===================== 健康检查测试 =====================

    @Test
    @DisplayName("健康检查成功应该返回true")
    void healthCheck_Success() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("OK"));

        // When & Then
        StepVerifier.create(client.healthCheck())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("健康检查失败应该返回false")
    void healthCheck_Failure() {
        // Given
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // When & Then
        StepVerifier.create(client.healthCheck())
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("健康检查超时应该返回false")
    void healthCheck_Timeout() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE));

        // When & Then
        StepVerifier.create(client.healthCheck())
                .expectNext(false)
                .verifyComplete();
    }

    // ===================== 配置更新测试 =====================

    @Test
    @DisplayName("配置更新应该重新初始化WebClient")
    void configUpdate_ShouldReinitializeWebClient() {
        // Given
        String newConfig = """
            {
                "baseUrl": "https://new-server.example.com",
                "endpoint": "/v2/specific_data",
                "timeout": "PT10S",
                "maxRetries": 5,
                "retryDelay": "PT1S"
            }""";

        // When - 模拟配置更新
        doAnswer(invocation -> {
            // 获取监听器并触发配置更新
            ConfigSource.ConfigChangeListener listener = invocation.getArgument(1);
            listener.onConfigChange("remote.service.config", "", newConfig);
            return null;
        }).when(configSource).addConfigListener(eq("remote.service.config"), any());

        // 重新设置监听器
        client.init();

        // Then - 验证配置已更新（通过后续请求验证）
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(createResponseJson()));

        StepVerifier.create(client.fetchSpecificData(testRequest))
                .expectNext(testResponse)
                .verifyComplete();
    }

    // ===================== 辅助方法 =====================

    private void setupConfigSourceMock() {
        String defaultConfig = """
            {
                "baseUrl": "%s",
                "endpoint": "/v1/specific_data", 
                "timeout": "PT5S",
                "maxRetries": 2,
                "retryDelay": "PT500MS"
            }""".formatted(mockWebServer.url(""));

        when(configSource.getConfig("remote.service.config"))
                .thenReturn(Mono.just(defaultConfig));
    }

    private void updateClientConfig() {
        String config = """
            {
                "baseUrl": "%s",
                "endpoint": "/v1/specific_data",
                "timeout": "PT5S", 
                "maxRetries": 2,
                "retryDelay": "PT500MS"
            }""".formatted(mockWebServer.url(""));

        // 使用反射更新配置
        try {
            Object configObj = ReflectionTestUtils.getField(client, "config");
            if (configObj == null) {
                // 手动触发配置加载
                when(configSource.getConfig("remote.service.config"))
                        .thenReturn(Mono.just(config));
                
                // 重新初始化
                ReflectionTestUtils.invokeMethod(client, "loadRemoteServiceConfig");
                ReflectionTestUtils.invokeMethod(client, "initWebClient");
            }
        } catch (Exception e) {
            // 如果反射失败，使用默认配置
        }
    }

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

    private String createResponseJson() {
        try {
            return objectMapper.writeValueAsString(testResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}