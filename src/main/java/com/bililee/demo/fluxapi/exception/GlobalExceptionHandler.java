package com.bililee.demo.fluxapi.exception;

import com.bililee.demo.fluxapi.response.CommonApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.nio.file.AccessDeniedException;
import java.util.concurrent.TimeoutException;
import reactor.core.Exceptions;

/**
 * 全局异常处理器
 * 统一处理应用中的各种异常，返回标准化的错误响应格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义API服务器异常
     */
    @ExceptionHandler(ApiServerException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleApiServerException(ApiServerException ex) {
        log.error("API服务器异常: {}", ex.getMessage(), ex);
        
        HttpStatus httpStatus = resolveHttpStatus(ex.getStatusCode());
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                ex.getStatusCode(), 
                ex.getMessage()
        );
        
        return new ResponseEntity<>(errorResponse, httpStatus);
    }

    /**
     * 处理API超时异常
     */
    @ExceptionHandler(ApiTimeoutException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleApiTimeoutException(ApiTimeoutException ex) {
        log.error("API超时异常: {}", ex.getMessage(), ex);
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.REQUEST_TIMEOUT.value(),
                "请求超时: " + ex.getMessage()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.REQUEST_TIMEOUT);
    }

    /**
     * 处理一般超时异常
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleTimeoutException(TimeoutException ex) {
        log.error("超时异常: {}", ex.getMessage(), ex);
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.REQUEST_TIMEOUT.value(),
                "操作超时，请稍后重试"
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.REQUEST_TIMEOUT);
    }



    /**
     * 处理参数验证异常 (WebFlux)
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleWebExchangeBindException(WebExchangeBindException ex) {
        log.error("参数验证异常: {}", ex.getMessage(), ex);
        
        StringBuilder errorMsg = new StringBuilder("参数验证失败: ");
        ex.getFieldErrors().forEach(error -> {
            errorMsg.append(error.getField())
                    .append(" ")
                    .append(error.getDefaultMessage())
                    .append("; ");
        });
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                errorMsg.toString()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理方法参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.error("方法参数验证异常: {}", ex.getMessage(), ex);
        
        StringBuilder errorMsg = new StringBuilder("参数验证失败: ");
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errorMsg.append(fieldError.getField())
                    .append(" ")
                    .append(fieldError.getDefaultMessage())
                    .append("; ");
        }
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                errorMsg.toString()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleBindException(BindException ex) {
        log.error("数据绑定异常: {}", ex.getMessage(), ex);
        
        StringBuilder errorMsg = new StringBuilder("数据绑定失败: ");
        ex.getFieldErrors().forEach(error -> {
            errorMsg.append(error.getField())
                    .append(" ")
                    .append(error.getDefaultMessage())
                    .append("; ");
        });
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                errorMsg.toString()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理服务器Web输入异常
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleServerWebInputException(ServerWebInputException ex) {
        log.error("服务器Web输入异常: {}", ex.getMessage(), ex);
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                "请求参数格式错误: " + ex.getReason()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理HTTP消息不可读异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.error("HTTP消息不可读异常: {}", ex.getMessage(), ex);
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                "请求体格式错误，请检查JSON格式"
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理JSON处理异常
     */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleJsonProcessingException(JsonProcessingException ex) {
        log.error("JSON处理异常: {}", ex.getMessage(), ex);
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                "JSON数据处理失败: " + ex.getOriginalMessage()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }



    /**
     * 处理访问被拒绝异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.error("访问被拒绝异常: {}", ex.getMessage(), ex);
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.FORBIDDEN.value(),
                "访问被拒绝: " + ex.getMessage()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("非法参数异常: {}", ex.getMessage(), ex);
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                "参数错误: " + ex.getMessage()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleIllegalStateException(IllegalStateException ex) {
        log.error("非法状态异常: {}", ex.getMessage(), ex);
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.CONFLICT.value(),
                "操作状态错误: " + ex.getMessage()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleNullPointerException(NullPointerException ex) {
        log.error("空指针异常: {}", ex.getMessage(), ex);
        
        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "系统内部错误，请联系管理员"
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 处理 ResponseStatusException 异常，将其转换为统一的 CommonApiResponse 错误格式。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("响应状态异常: {}", ex.getMessage(), ex);
        
        HttpStatus status = (HttpStatus) ex.getStatusCode();
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();

        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(status.value(), reason);
        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * 捕获其他未被特定处理的通用异常，作为内部服务器错误。
     * 包含Reactor异常解包逻辑
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<Void>> handleGenericException(Exception ex) {
        // 尝试解包Reactor包装的异常
        Throwable unwrapped = Exceptions.unwrap(ex);
        
        if (unwrapped != ex && unwrapped instanceof RuntimeException) {
            // 如果解包出不同的异常，尝试重新处理解包后的异常
            RuntimeException runtimeEx = (RuntimeException) unwrapped;
            
            if (runtimeEx instanceof ApiServerException) {
                return handleApiServerException((ApiServerException) runtimeEx);
            } else if (runtimeEx instanceof ApiTimeoutException) {
                return handleApiTimeoutException((ApiTimeoutException) runtimeEx);
            } else if (runtimeEx instanceof IllegalArgumentException) {
                return handleIllegalArgumentException((IllegalArgumentException) runtimeEx);
            } else if (runtimeEx instanceof IllegalStateException) {
                return handleIllegalStateException((IllegalStateException) runtimeEx);
            } else if (runtimeEx instanceof NullPointerException) {
                return handleNullPointerException((NullPointerException) runtimeEx);
            }
            
            // 对于解包后的其他异常，使用解包后的异常信息
            log.error("捕获到Reactor包装的异常: 原异常: {}, 解包后: {}", ex.getMessage(), unwrapped.getMessage(), unwrapped);
        } else {
            log.error("捕获到未处理的异常: {}", ex.getMessage(), ex);
        }

        // 使用原异常或解包后的异常来生成错误信息
        Throwable targetException = (unwrapped != ex) ? unwrapped : ex;
        
        // 生产环境不返回详细的错误信息，避免泄露敏感信息
        String errorMsg = isProductionEnvironment() ? 
                "系统内部错误，请稍后重试" : 
                "系统内部错误: " + targetException.getMessage();

        CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                errorMsg
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 将自定义状态码映射为HTTP状态码
     */
    private HttpStatus resolveHttpStatus(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return HttpStatus.OK;
        } else if (statusCode >= 400 && statusCode < 500) {
            return HttpStatus.valueOf(statusCode);
        } else if (statusCode >= 500) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 判断是否为生产环境
     * 实际项目中可以通过读取配置文件或环境变量来判断
     */
    private boolean isProductionEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "dev");
        return "prod".equals(profile) || "production".equals(profile);
    }
}