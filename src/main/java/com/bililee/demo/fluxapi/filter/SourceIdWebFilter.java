package com.bililee.demo.fluxapi.filter;

import com.bililee.demo.fluxapi.model.ApiResponse;
import com.bililee.demo.fluxapi.validator.SourceIdValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.lang.NonNull;

import java.nio.charset.StandardCharsets;

/**
 * Source-Id WebFlux 过滤器
 * 用于校验请求头中的 Source-Id 参数
 */
@Slf4j
@Component
public class SourceIdWebFilter implements WebFilter {

    @Autowired
    private SourceIdValidator sourceIdValidator;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        
        log.debug("过滤器处理请求: {} {}", method, path);
        
        // 检查是否需要校验 Source-Id
        if (!shouldValidateSourceId(path)) {
            log.debug("路径无需 Source-Id 校验: {}", path);
            return chain.filter(exchange);
        }
        
        // 获取 Source-Id 头部
        String sourceId = request.getHeaders().getFirst("Source-Id");
        
        // 校验 Source-Id
        SourceIdValidator.ValidationResult result = sourceIdValidator.validate(sourceId, path);
        
        if (!result.isValid()) {
            log.warn("Source-Id 校验失败: {} - 请求: {} {}", result.getMessage(), method, path);
            
            // 返回错误响应
            return writeErrorResponse(exchange.getResponse(), result.getMessage(), HttpStatus.BAD_REQUEST);
        }
        
        log.debug("Source-Id 校验通过: {} - 请求: {} {}", sourceId, method, path);
        
        // 继续处理请求
        return chain.filter(exchange);
    }

    /**
     * 判断路径是否需要校验 Source-Id
     */
    private boolean shouldValidateSourceId(String path) {
        // 需要校验的路径
        if (path.startsWith("/v1/specific_data")) {
            return true;
        }
        if (path.startsWith("/api/data")) {
            return true;
        }
        
        // 不需要校验的路径
        if (path.startsWith("/monitor")) {
            return false;
        }
        if (path.startsWith("/actuator")) {
            return false;
        }
        if (path.startsWith("/health")) {
            return false;
        }
        if (path.startsWith("/error")) {
            return false;
        }
        if (path.startsWith("/test")) {
            return false;
        }
        
        // 静态资源
        if (path.startsWith("/static") || 
            path.startsWith("/css") || 
            path.startsWith("/js") || 
            path.startsWith("/images") || 
            path.equals("/favicon.ico")) {
            return false;
        }
        
        // 默认不校验
        return false;
    }

    /**
     * 写入错误响应
     */
    private Mono<Void> writeErrorResponse(ServerHttpResponse response, String message, HttpStatus status) {
        try {
            // 设置响应头
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            
            // 构建错误响应
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setStatusCode(status.value());
            apiResponse.setStatusMsg(message);
            apiResponse.setData(null);
            
            // 序列化响应体
            String jsonResponse = objectMapper.writeValueAsString(apiResponse);
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            
            // 写入响应体
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
            
        } catch (JsonProcessingException e) {
            log.error("序列化错误响应失败", e);
            
            // 降级处理：返回简单的错误信息
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            
            String fallbackMessage = "Source-Id validation failed";
            byte[] fallbackBytes = fallbackMessage.getBytes(StandardCharsets.UTF_8);
            DataBuffer fallbackBuffer = response.bufferFactory().wrap(fallbackBytes);
            
            return response.writeWith(Mono.just(fallbackBuffer));
        }
    }
}