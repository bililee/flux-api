package com.bililee.demo.fluxapi.exception;

import com.bililee.demo.fluxapi.response.CommonApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.Exceptions;

import java.nio.file.AccessDeniedException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 单元测试
 * 测试全局异常处理器的各种异常处理逻辑，包括自定义异常、标准异常、Reactor异常等
 */
@DisplayName("全局异常处理器测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    // ===================== 自定义异常处理测试 =====================

    @Nested
    @DisplayName("自定义异常处理测试")
    class CustomExceptionHandlingTest {

        @Test
        @DisplayName("ApiServerException应该返回对应的HTTP状态码和错误信息")
        void handleApiServerException_Success() {
            // Given
            ApiServerException exception = new ApiServerException("远程服务不可用", 503);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleApiServerException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus_code()).isEqualTo(503);
            assertThat(response.getBody().getStatus_msg()).isEqualTo("远程服务不可用");
            assertThat(response.getBody().getData()).isNull();
        }

        @Test
        @DisplayName("ApiServerException 4xx错误应该返回对应状态码")
        void handleApiServerException_4xxError() {
            // Given
            ApiServerException exception = new ApiServerException("请求参数错误", 400);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleApiServerException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getStatus_code()).isEqualTo(400);
        }

        @Test
        @DisplayName("ApiTimeoutException应该返回408超时状态码")
        void handleApiTimeoutException_Success() {
            // Given
            ApiTimeoutException exception = new ApiTimeoutException("连接超时");

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleApiTimeoutException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus_code()).isEqualTo(408);
            assertThat(response.getBody().getStatus_msg()).contains("请求超时");
            assertThat(response.getBody().getStatus_msg()).contains("连接超时");
        }

        @Test
        @DisplayName("普通TimeoutException应该返回408状态码")
        void handleTimeoutException_Success() {
            // Given
            TimeoutException exception = new TimeoutException("操作超时");

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleTimeoutException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
            assertThat(response.getBody().getStatus_code()).isEqualTo(408);
            assertThat(response.getBody().getStatus_msg()).isEqualTo("操作超时，请稍后重试");
        }
    }

    // ===================== 参数验证异常测试 =====================

    @Nested
    @DisplayName("参数验证异常处理测试")
    class ValidationExceptionHandlingTest {

        @Test
        @DisplayName("WebExchangeBindException应该返回400状态码和详细错误信息")
        void handleWebExchangeBindException_Success() {
            // Given
            var bindingResult = new BeanPropertyBindingResult(new Object(), "testObject");
            bindingResult.addError(new FieldError("testObject", "field1", "不能为空"));
            bindingResult.addError(new FieldError("testObject", "field2", "格式错误"));
            
            WebExchangeBindException exception = new WebExchangeBindException(null, bindingResult);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleWebExchangeBindException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getStatus_code()).isEqualTo(400);
            assertThat(response.getBody().getStatus_msg()).contains("参数验证失败");
            assertThat(response.getBody().getStatus_msg()).contains("field1");
            assertThat(response.getBody().getStatus_msg()).contains("不能为空");
            assertThat(response.getBody().getStatus_msg()).contains("field2");
            assertThat(response.getBody().getStatus_msg()).contains("格式错误");
        }

        @Test
        @DisplayName("MethodArgumentNotValidException应该返回400状态码")
        void handleMethodArgumentNotValidException_Success() {
            // Given
            var bindingResult = new BeanPropertyBindingResult(new Object(), "testObject");
            bindingResult.addError(new FieldError("testObject", "name", "名称不能为空"));
            
            MethodArgumentNotValidException exception = 
                    new MethodArgumentNotValidException(null, bindingResult);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleMethodArgumentNotValidException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getStatus_code()).isEqualTo(400);
            assertThat(response.getBody().getStatus_msg()).contains("参数验证失败");
            assertThat(response.getBody().getStatus_msg()).contains("name");
        }

        @Test
        @DisplayName("ServerWebInputException应该返回400状态码")
        void handleServerWebInputException_Success() {
            // Given
            ServerWebInputException exception = 
                    new ServerWebInputException("请求参数格式错误");

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleServerWebInputException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getStatus_code()).isEqualTo(400);
            assertThat(response.getBody().getStatus_msg()).contains("请求参数格式错误");
            assertThat(response.getBody().getStatus_msg()).contains("JSON格式错误");
        }
    }

    // ===================== 数据处理异常测试 =====================

    @Nested
    @DisplayName("数据处理异常测试")
    class DataProcessingExceptionHandlingTest {

        @Test
        @DisplayName("HttpMessageNotReadableException应该返回400状态码")
        void handleHttpMessageNotReadableException_Success() {
            // Given
            HttpMessageNotReadableException exception = 
                    new HttpMessageNotReadableException("JSON解析失败", (Throwable) null);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleHttpMessageNotReadableException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getStatus_code()).isEqualTo(400);
            assertThat(response.getBody().getStatus_msg()).contains("请求体格式错误");
            assertThat(response.getBody().getStatus_msg()).contains("JSON格式");
        }

        @Test
        @DisplayName("JsonProcessingException应该返回400状态码")
        void handleJsonProcessingException_Success() {
            // Given
            JsonProcessingException exception = new JsonProcessingException("JSON处理失败") {};

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleJsonProcessingException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getStatus_code()).isEqualTo(400);
            assertThat(response.getBody().getStatus_msg()).contains("JSON数据处理失败");
        }
    }

    // ===================== 标准异常处理测试 =====================

    @Nested
    @DisplayName("标准异常处理测试")
    class StandardExceptionHandlingTest {

        @Test
        @DisplayName("AccessDeniedException应该返回403状态码")
        void handleAccessDeniedException_Success() {
            // Given
            AccessDeniedException exception = new AccessDeniedException("无权访问此资源");

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleAccessDeniedException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().getStatus_code()).isEqualTo(403);
            assertThat(response.getBody().getStatus_msg()).contains("访问被拒绝");
            assertThat(response.getBody().getStatus_msg()).contains("无权访问此资源");
        }

        @Test
        @DisplayName("IllegalArgumentException应该返回400状态码")
        void handleIllegalArgumentException_Success() {
            // Given
            IllegalArgumentException exception = new IllegalArgumentException("参数值无效");

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleIllegalArgumentException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getStatus_code()).isEqualTo(400);
            assertThat(response.getBody().getStatus_msg()).contains("参数错误");
            assertThat(response.getBody().getStatus_msg()).contains("参数值无效");
        }

        @Test
        @DisplayName("IllegalStateException应该返回409状态码")
        void handleIllegalStateException_Success() {
            // Given
            IllegalStateException exception = new IllegalStateException("状态不正确");

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleIllegalStateException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().getStatus_code()).isEqualTo(409);
            assertThat(response.getBody().getStatus_msg()).contains("操作状态错误");
            assertThat(response.getBody().getStatus_msg()).contains("状态不正确");
        }

        @Test
        @DisplayName("NullPointerException应该返回500状态码")
        void handleNullPointerException_Success() {
            // Given
            NullPointerException exception = new NullPointerException("空指针异常");

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleNullPointerException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getStatus_code()).isEqualTo(500);
            assertThat(response.getBody().getStatus_msg()).isEqualTo("系统内部错误，请联系管理员");
        }

        @Test
        @DisplayName("ResponseStatusException应该返回对应状态码")
        void handleResponseStatusException_Success() {
            // Given
            ResponseStatusException exception = 
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "资源未找到");

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleResponseStatusException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getStatus_code()).isEqualTo(404);
            assertThat(response.getBody().getStatus_msg()).isEqualTo("资源未找到");
        }

        @Test
        @DisplayName("ResponseStatusException无reason应该使用默认原因")
        void handleResponseStatusException_NoReason() {
            // Given
            ResponseStatusException exception = 
                    new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleResponseStatusException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getStatus_code()).isEqualTo(500);
            assertThat(response.getBody().getStatus_msg()).isEqualTo("Internal Server Error");
        }
    }

    // ===================== 通用异常处理测试 =====================

    @Nested
    @DisplayName("通用异常处理测试")
    class GenericExceptionHandlingTest {

        @Test
        @DisplayName("未知异常应该返回500状态码")
        void handleGenericException_UnknownException() {
            // Given
            Exception exception = new Exception("未知错误");

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleGenericException(exception);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getStatus_code()).isEqualTo(500);
            // 在非生产环境应该包含错误详情
            assertThat(response.getBody().getStatus_msg()).contains("系统内部错误");
        }

        @Test
        @DisplayName("Reactor包装的ApiServerException应该被正确解包")
        void handleGenericException_ReactorWrappedApiServerException() {
            // Given
            ApiServerException originalException = new ApiServerException("服务不可用", 503);
            Exception wrappedException = Exceptions.propagate(originalException);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleGenericException(wrappedException);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getStatus_code()).isEqualTo(503);
            assertThat(response.getBody().getStatus_msg()).isEqualTo("服务不可用");
        }

        @Test
        @DisplayName("Reactor包装的ApiTimeoutException应该被正确解包")
        void handleGenericException_ReactorWrappedApiTimeoutException() {
            // Given
            ApiTimeoutException originalException = new ApiTimeoutException("请求超时");
            Exception wrappedException = Exceptions.propagate(originalException);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleGenericException(wrappedException);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
            assertThat(response.getBody().getStatus_code()).isEqualTo(408);
            assertThat(response.getBody().getStatus_msg()).contains("请求超时");
        }

        @Test
        @DisplayName("Reactor包装的IllegalArgumentException应该被正确解包")
        void handleGenericException_ReactorWrappedIllegalArgumentException() {
            // Given
            IllegalArgumentException originalException = new IllegalArgumentException("参数错误");
            Exception wrappedException = Exceptions.propagate(originalException);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleGenericException(wrappedException);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getStatus_code()).isEqualTo(400);
            assertThat(response.getBody().getStatus_msg()).contains("参数错误");
        }

        @Test
        @DisplayName("Reactor包装的其他异常应该使用解包后的异常信息")
        void handleGenericException_ReactorWrappedOtherException() {
            // Given
            RuntimeException originalException = new RuntimeException("自定义运行时异常");
            Exception wrappedException = Exceptions.propagate(originalException);

            // When
            ResponseEntity<CommonApiResponse<Void>> response = 
                    exceptionHandler.handleGenericException(wrappedException);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getStatus_code()).isEqualTo(500);
            // 应该使用解包后的异常信息
            assertThat(response.getBody().getStatus_msg()).contains("自定义运行时异常");
        }
    }

    // ===================== HTTP状态码映射测试 =====================

    @Nested
    @DisplayName("HTTP状态码映射测试")
    class HttpStatusMappingTest {

        @Test
        @DisplayName("2xx状态码应该映射为OK")
        void resolveHttpStatus_2xxShouldMapToOk() {
            // Given
            ApiServerException exception200 = new ApiServerException("成功", 200);
            ApiServerException exception201 = new ApiServerException("已创建", 201);

            // When
            ResponseEntity<CommonApiResponse<Void>> response200 = 
                    exceptionHandler.handleApiServerException(exception200);
            ResponseEntity<CommonApiResponse<Void>> response201 = 
                    exceptionHandler.handleApiServerException(exception201);

            // Then
            assertThat(response200.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response201.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("4xx状态码应该映射为对应的客户端错误状态码")
        void resolveHttpStatus_4xxShouldMapToClientError() {
            // Given
            ApiServerException exception400 = new ApiServerException("请求错误", 400);
            ApiServerException exception404 = new ApiServerException("未找到", 404);

            // When
            ResponseEntity<CommonApiResponse<Void>> response400 = 
                    exceptionHandler.handleApiServerException(exception400);
            ResponseEntity<CommonApiResponse<Void>> response404 = 
                    exceptionHandler.handleApiServerException(exception404);

            // Then
            assertThat(response400.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response404.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("5xx状态码应该映射为INTERNAL_SERVER_ERROR")
        void resolveHttpStatus_5xxShouldMapToInternalServerError() {
            // Given
            ApiServerException exception500 = new ApiServerException("服务器错误", 500);
            ApiServerException exception503 = new ApiServerException("服务不可用", 503);

            // When
            ResponseEntity<CommonApiResponse<Void>> response500 = 
                    exceptionHandler.handleApiServerException(exception500);
            ResponseEntity<CommonApiResponse<Void>> response503 = 
                    exceptionHandler.handleApiServerException(exception503);

            // Then
            assertThat(response500.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response503.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("其他状态码应该映射为BAD_REQUEST")
        void resolveHttpStatus_OtherCodesShouldMapToBadRequest() {
            // Given
            ApiServerException exception100 = new ApiServerException("继续", 100);
            ApiServerException exception999 = new ApiServerException("未知", 999);

            // When
            ResponseEntity<CommonApiResponse<Void>> response100 = 
                    exceptionHandler.handleApiServerException(exception100);
            ResponseEntity<CommonApiResponse<Void>> response999 = 
                    exceptionHandler.handleApiServerException(exception999);

            // Then
            assertThat(response100.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response999.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ===================== 生产环境测试 =====================

    @Nested
    @DisplayName("生产环境错误处理测试")
    class ProductionEnvironmentTest {

        @Test
        @DisplayName("生产环境下应该隐藏详细错误信息")
        void handleGenericException_ProductionEnvironment() {
            // Given
            System.setProperty("spring.profiles.active", "prod");
            Exception exception = new Exception("敏感错误信息");

            try {
                // When
                ResponseEntity<CommonApiResponse<Void>> response = 
                        exceptionHandler.handleGenericException(exception);

                // Then
                assertThat(response.getBody().getStatus_msg()).isEqualTo("系统内部错误，请稍后重试");
            } finally {
                // 清理系统属性
                System.clearProperty("spring.profiles.active");
            }
        }

        @Test
        @DisplayName("开发环境下应该显示详细错误信息")
        void handleGenericException_DevelopmentEnvironment() {
            // Given
            System.setProperty("spring.profiles.active", "dev");
            Exception exception = new Exception("详细错误信息");

            try {
                // When
                ResponseEntity<CommonApiResponse<Void>> response = 
                        exceptionHandler.handleGenericException(exception);

                // Then
                assertThat(response.getBody().getStatus_msg()).contains("详细错误信息");
            } finally {
                // 清理系统属性
                System.clearProperty("spring.profiles.active");
            }
        }
    }
}