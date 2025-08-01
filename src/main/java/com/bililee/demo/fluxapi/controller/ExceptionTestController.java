package com.bililee.demo.fluxapi.controller;

import com.bililee.demo.fluxapi.exception.ApiServerException;
import com.bililee.demo.fluxapi.exception.ApiTimeoutException;
import com.bililee.demo.fluxapi.response.CommonApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

/**
 * 异常测试控制器
 * 用于测试全局异常处理器的各种异常处理功能
 */
@RestController
@RequestMapping("/test/exceptions")
public class ExceptionTestController {

    /**
     * 测试自定义API服务器异常
     */
    @GetMapping("/api-server-error")
    public Mono<CommonApiResponse<String>> testApiServerException() {
        return Mono.error(new ApiServerException("模拟API服务器错误", 502));
    }

    /**
     * 测试API超时异常
     */
    @GetMapping("/api-timeout")
    public Mono<CommonApiResponse<String>> testApiTimeoutException() {
        return Mono.error(new ApiTimeoutException("模拟API超时"));
    }

    /**
     * 测试一般超时异常
     */
    @GetMapping("/timeout")
    public Mono<CommonApiResponse<String>> testTimeoutException() {
        return Mono.error(new TimeoutException("操作超时"));
    }

    /**
     * 测试响应状态异常
     */
    @GetMapping("/response-status-error/{code}")
    public Mono<CommonApiResponse<String>> testResponseStatusException(@PathVariable int code) {
        HttpStatus status = HttpStatus.valueOf(code);
        return Mono.error(new ResponseStatusException(status, "模拟HTTP状态异常: " + status.getReasonPhrase()));
    }

    /**
     * 测试非法参数异常
     */
    @GetMapping("/illegal-argument")
    public Mono<CommonApiResponse<String>> testIllegalArgumentException() {
        return Mono.error(new IllegalArgumentException("非法参数：输入不能为空"));
    }

    /**
     * 测试非法状态异常
     */
    @GetMapping("/illegal-state")
    public Mono<CommonApiResponse<String>> testIllegalStateException() {
        return Mono.error(new IllegalStateException("非法状态：当前状态不允许此操作"));
    }

    /**
     * 测试空指针异常
     */
    @GetMapping("/null-pointer")
    public Mono<CommonApiResponse<String>> testNullPointerException() {
        return Mono.fromCallable(() -> {
            String str = null;
            //noinspection DataFlowIssue
            return str.length(); // 故意触发空指针异常用于测试
        }).map(length -> CommonApiResponse.success("Length: " + length));
    }

    /**
     * 测试Reactor包装的异常
     */
    @GetMapping("/reactor-wrapped")
    public Mono<CommonApiResponse<String>> testReactorWrappedException() {
        return Mono.fromCallable(() -> {
            throw new ApiServerException("包装在Reactor中的异常", 500);
        }).map(result -> CommonApiResponse.success(result.toString()));
    }

    /**
     * 测试通用未知异常
     */
    @GetMapping("/unknown-error")
    public Mono<CommonApiResponse<String>> testUnknownException() {
        return Mono.error(new RuntimeException("未知的运行时异常"));
    }

    /**
     * 测试JSON处理异常
     */
    @GetMapping("/json-error")
    public Mono<CommonApiResponse<String>> testJsonProcessingException() {
        return Mono.fromCallable(() -> {
            // 模拟JSON处理异常
            throw new com.fasterxml.jackson.core.JsonProcessingException("JSON解析失败") {};
        }).map(result -> CommonApiResponse.success(result.toString()));
    }

    /**
     * 测试正常响应（用于对比）
     */
    @GetMapping("/success")
    public Mono<CommonApiResponse<String>> testSuccessResponse() {
        return Mono.just(CommonApiResponse.success("测试成功响应"));
    }
}