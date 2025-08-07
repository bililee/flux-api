package com.bililee.demo.fluxapi.controller;

import com.bililee.demo.fluxapi.exception.ApiServerException;
import com.bililee.demo.fluxapi.exception.ApiTimeoutException;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.service.impl.SpecificDataServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SpecificDataController 单元测试
 * 测试REST接口的请求响应处理，包括参数验证、Source-Id处理、异常处理等
 */
@WebFluxTest(SpecificDataController.class)
@DisplayName("特定数据控制器测试")
class SpecificDataControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SpecificDataServiceImpl specificDataService;

    @Autowired
    private ObjectMapper objectMapper;

    // ===================== 正常请求测试 =====================

    @Test
    @DisplayName("正常请求应该成功返回数据")
    void querySpecificData_Success() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        SpecificDataResponse response = createSuccessResponse();
        
        when(specificDataService.querySpecificData(any(SpecificDataRequest.class), eq("test-source")))
                .thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(SpecificDataResponse.class)
                .value(actualResponse -> {
                    assert actualResponse.statusCode().equals(0);
                    assert actualResponse.statusMsg().equals("success");
                    assert actualResponse.data().total().equals(2);
                });
    }

    @Test
    @DisplayName("缺少Source-Id头部应该使用默认值")
    void querySpecificData_MissingSourceId_UseDefault() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        SpecificDataResponse response = createSuccessResponse();
        
        when(specificDataService.querySpecificData(any(SpecificDataRequest.class), eq("default")))
                .thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SpecificDataResponse.class)
                .value(actualResponse -> {
                    assert actualResponse.statusCode().equals(0);
                });
    }

    @Test
    @DisplayName("空Source-Id头部应该使用默认值")
    void querySpecificData_EmptySourceId_UseDefault() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        SpecificDataResponse response = createSuccessResponse();
        
        when(specificDataService.querySpecificData(any(SpecificDataRequest.class), eq("default")))
                .thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("不同Source-Id应该正确传递给服务层")
    void querySpecificData_DifferentSourceIds() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        SpecificDataResponse response = createSuccessResponse();
        
        String[] sourceIds = {"mobile-app", "web-portal", "internal-service"};
        
        for (String sourceId : sourceIds) {
            when(specificDataService.querySpecificData(any(SpecificDataRequest.class), eq(sourceId)))
                    .thenReturn(Mono.just(response));

            // When & Then
            webTestClient.post()
                    .uri("/v1/specific_data")
                    .header("Source-Id", sourceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    // ===================== 请求参数验证测试 =====================

    @Test
    @DisplayName("空请求体应该返回400错误")
    void querySpecificData_EmptyBody_BadRequest() {
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("无效JSON应该返回400错误")
    void querySpecificData_InvalidJson_BadRequest() {
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("invalid json")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("缺少必需字段应该返回400错误")
    void querySpecificData_MissingRequiredFields_BadRequest() {
        // Given - 创建缺少codeSelectors的请求
        String invalidRequest = """
            {
                "indexes": [
                    {
                        "index_id": "price",
                        "time_type": "daily"
                    }
                ],
                "page_info": {
                    "page_begin": 0,
                    "page_size": 20
                }
            }""";

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("无效页面参数应该返回400错误")
    void querySpecificData_InvalidPageInfo_BadRequest() {
        // Given - 创建无效页面参数的请求
        String invalidRequest = """
            {
                "code_selectors": {
                    "include": [
                        {
                            "type": "stock",
                            "values": ["000001"]
                        }
                    ]
                },
                "indexes": [
                    {
                        "index_id": "price",
                        "time_type": "daily"
                    }
                ],
                "page_info": {
                    "page_begin": -1,
                    "page_size": 0
                }
            }""";

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ===================== 异常处理测试 =====================

    @Test
    @DisplayName("服务层超时异常应该返回错误响应")
    void querySpecificData_ServiceTimeout_ReturnErrorResponse() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        when(specificDataService.querySpecificData(any(SpecificDataRequest.class), any(String.class)))
                .thenReturn(Mono.error(new ApiTimeoutException("服务调用超时")));

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk() // Controller捕获异常返回OK状态
                .expectBody(SpecificDataResponse.class)
                .value(response -> {
                    assert response.statusCode().equals(500);
                    assert response.statusMsg().contains("服务内部错误");
                });
    }

    @Test
    @DisplayName("服务层服务器异常应该返回错误响应")
    void querySpecificData_ServiceError_ReturnErrorResponse() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        when(specificDataService.querySpecificData(any(SpecificDataRequest.class), any(String.class)))
                .thenReturn(Mono.error(new ApiServerException("远程服务错误", 503)));

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SpecificDataResponse.class)
                .value(response -> {
                    assert response.statusCode().equals(500);
                    assert response.statusMsg().contains("服务内部错误");
                });
    }

    @Test
    @DisplayName("运行时异常应该返回错误响应")
    void querySpecificData_RuntimeException_ReturnErrorResponse() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        when(specificDataService.querySpecificData(any(SpecificDataRequest.class), any(String.class)))
                .thenReturn(Mono.error(new RuntimeException("未知运行时异常")));

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SpecificDataResponse.class)
                .value(response -> {
                    assert response.statusCode().equals(500);
                    assert response.statusMsg().contains("服务内部错误");
                });
    }

    @Test
    @DisplayName("处理请求时发生异常应该返回错误响应")
    void querySpecificData_ProcessingException_ReturnErrorResponse() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        when(specificDataService.querySpecificData(any(SpecificDataRequest.class), any(String.class)))
                .thenThrow(new RuntimeException("处理请求时发生异常"));

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SpecificDataResponse.class)
                .value(response -> {
                    assert response.statusCode().equals(500);
                    assert response.statusMsg().contains("处理请求时发生内部错误");
                });
    }

    // ===================== 响应格式测试 =====================

    @Test
    @DisplayName("成功响应应该包含正确的格式和字段")
    void querySpecificData_ResponseFormat_Validation() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        SpecificDataResponse response = createDetailedResponse();
        
        when(specificDataService.querySpecificData(any(SpecificDataRequest.class), any(String.class)))
                .thenReturn(Mono.just(response));

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status_code").isEqualTo(0)
                .jsonPath("$.status_msg").isEqualTo("success")
                .jsonPath("$.data").exists()
                .jsonPath("$.data.total").isEqualTo(2)
                .jsonPath("$.data.items").isArray()
                .jsonPath("$.data.items[0].code").isEqualTo("000001")
                .jsonPath("$.data.items[0].name").isEqualTo("测试股票1")
                .jsonPath("$.data.items[0].values").isArray()
                .jsonPath("$.data.items[0].values[0].index_id").isEqualTo("price")
                .jsonPath("$.data.items[0].values[0].value").isEqualTo("10.5");
    }

    @Test
    @DisplayName("错误响应应该包含正确的错误信息格式")
    void querySpecificData_ErrorResponseFormat_Validation() throws Exception {
        // Given
        SpecificDataRequest request = createValidRequest();
        when(specificDataService.querySpecificData(any(SpecificDataRequest.class), any(String.class)))
                .thenReturn(Mono.error(new ApiTimeoutException("连接超时")));

        // When & Then
        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status_code").isEqualTo(500)
                .jsonPath("$.status_msg").value(msg -> {
                        assertThat(msg.toString()).contains("服务内部错误");
                })
                .jsonPath("$.data").exists();
    }

    // ===================== Content-Type测试 =====================

    @Test
    @DisplayName("不支持的Content-Type应该返回415错误")
    void querySpecificData_UnsupportedMediaType_415Error() throws Exception {
        SpecificDataRequest request = createValidRequest();

        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(objectMapper.writeValueAsString(request))
                .exchange()
                .expectStatus().isEqualTo(415);
    }

    @Test
    @DisplayName("缺少Content-Type应该返回400错误")
    void querySpecificData_MissingContentType_400Error() throws Exception {
        SpecificDataRequest request = createValidRequest();

        webTestClient.post()
                .uri("/v1/specific_data")
                .header("Source-Id", "test-source")
                .bodyValue(objectMapper.writeValueAsString(request))
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ===================== 辅助方法 =====================

    private SpecificDataRequest createValidRequest() {
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

    private SpecificDataResponse createSuccessResponse() {
        return SpecificDataResponse.builder()
                .statusCode(0)
                .statusMsg("success")
                .data(SpecificDataResponse.SpecificDataResult.builder()
                        .total(2)
                        .indexes(List.of())
                        .data(List.of())
                        .build())
                .build();
    }

    private SpecificDataResponse createDetailedResponse() {
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
}