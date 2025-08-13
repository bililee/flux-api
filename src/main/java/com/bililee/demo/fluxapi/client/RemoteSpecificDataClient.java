package com.bililee.demo.fluxapi.client;

import com.bililee.demo.fluxapi.config.source.ConfigSource;
import com.bililee.demo.fluxapi.exception.ApiServerException;
import com.bililee.demo.fluxapi.exception.ApiTimeoutException;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.resilience.ResilienceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * 远程特定数据服务客户端
 * 支持HTTPS调用和动态配置
 */
@Slf4j
@Component
public class RemoteSpecificDataClient {

    @Autowired
    private ConfigSource configSource;

    @Autowired
    private ResilienceService resilienceService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;
    private RemoteServiceConfig config;

    @PostConstruct
    public void init() {
        loadRemoteServiceConfig();
        initWebClient();
        setupConfigListener();
    }

    /**
     * 调用远程服务获取特定数据（支持Source-Id）
     */
    public Mono<SpecificDataResponse> fetchSpecificData(SpecificDataRequest request, String sourceId) {
        String actualSourceId = sourceId != null ? sourceId : "default";
        
        log.info("调用远程服务获取特定数据 - sourceId: {}, 请求代码数量: {}", 
                actualSourceId,
                request.codeSelectors().include().stream()
                        .mapToInt(selector -> selector.values().size()).sum());

        return resilienceService.executeResilientCall(
                actualSourceId, 
                "fetch_specific_data",
                () -> executeRawRemoteCall(request)
        )
        .doOnSuccess(response -> log.info("弹性远程服务调用成功 - sourceId: {}, 返回数据量: {}", 
                actualSourceId, response.data() != null ? response.data().total() : 0))
        .doOnError(error -> log.error("弹性远程服务调用失败 - sourceId: {}, 错误: {}", 
                actualSourceId, error.getMessage()));
    }

    /**
     * 调用远程服务获取特定数据（兼容原接口）
     */
    public Mono<SpecificDataResponse> fetchSpecificData(SpecificDataRequest request) {
        return fetchSpecificData(request, "default");
    }

    /**
     * 健康检查（支持Source-Id）
     */
    public Mono<Boolean> healthCheck(String sourceId) {
        String actualSourceId = sourceId != null ? sourceId : "default";
        
        return resilienceService.healthCheck(actualSourceId, () ->
                webClient.get()
                        .uri("/health")
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(response -> true)
                        .onErrorReturn(false)
        )
        .doOnNext(healthy -> log.debug("远程服务健康状态 - sourceId: {}, 健康: {}", actualSourceId, healthy));
    }

    /**
     * 健康检查（兼容原接口）
     */
    public Mono<Boolean> healthCheck() {
        return healthCheck("default");
    }

    /**
     * 加载远程服务配置
     */
    private void loadRemoteServiceConfig() {
        configSource.getConfig("remote.service.config")
                .doOnNext(this::updateRemoteServiceConfig)
                .subscribe();
    }

    /**
     * 初始化WebClient
     */
    private void initWebClient() {
        if (config == null) {
            // 使用默认配置
            config = new RemoteServiceConfig(
                    "http://127.0.0.1:8086",
                    "/v1/specific_data",
                    Duration.ofSeconds(5),
                    2,
                    Duration.ofMillis(500)
            );
        }

        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        log.info("远程服务客户端初始化完成，目标地址: {}", config.getBaseUrl());
    }

    /**
     * 设置配置监听器
     */
    private void setupConfigListener() {
        configSource.addConfigListener("remote.service.config", 
                (key, oldValue, newValue) -> {
                    updateRemoteServiceConfig(newValue);
                    initWebClient(); // 重新初始化WebClient
                });
    }

    /**
     * 更新远程服务配置
     */
    private void updateRemoteServiceConfig(String configJson) {
        try {
            JsonNode node = objectMapper.readTree(configJson);
            
            String baseUrl = node.has("base_url") ? 
                    node.get("base_url").asText() : "https://remote-datacenter.example.com/api";
            String endpoint = node.has("endpoint") ? 
                    node.get("endpoint").asText() : "/v1/specific_data";
            Duration timeout = node.has("timeout") ? 
                    Duration.parse("PT" + node.get("timeout").asText()) : Duration.ofSeconds(5);
            int maxRetries = node.has("max_retries") ? 
                    node.get("max_retries").asInt() : 2;
            Duration retryDelay = node.has("retry_delay") ? 
                    Duration.parse("PT" + node.get("retry_delay").asText()) : Duration.ofMillis(500);

            this.config = new RemoteServiceConfig(baseUrl, endpoint, timeout, maxRetries, retryDelay);
            log.info("远程服务配置已更新: baseUrl={}, timeout={}", baseUrl, timeout);
        } catch (Exception e) {
            log.error("更新远程服务配置失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行原始远程调用（无弹性策略）
     */
    private Mono<SpecificDataResponse> executeRawRemoteCall(SpecificDataRequest request) {
        return webClient.post()
                .uri(config.getEndpoint())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SpecificDataResponse.class)
                .onErrorMap(this::mapException);
    }

    /**
     * 映射异常类型
     */
    private Throwable mapException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webEx) {
            HttpStatus status = (HttpStatus) webEx.getStatusCode();
            
            if (status.is4xxClientError()) {
                return new ApiServerException(
                        "远程服务客户端错误: " + webEx.getMessage(), 
                        status.value());
            } else if (status.is5xxServerError()) {
                return new ApiServerException(
                        "远程服务器错误: " + webEx.getMessage(), 
                        status.value());
            }
        }
        
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return new ApiTimeoutException("远程服务调用超时: " + throwable.getMessage());
        }
        
        if (throwable instanceof ApiServerException || throwable instanceof ApiTimeoutException) {
            return throwable;
        }
        
        return new ApiServerException(
                "远程服务调用失败: " + throwable.getMessage(), 
                500);
    }

    /**
     * 远程服务配置
     */
    private record RemoteServiceConfig(
            String baseUrl,
            String endpoint,
            Duration timeout,
            int maxRetries,
            Duration retryDelay
    ) {
        public String getBaseUrl() { return baseUrl; }
        public String getEndpoint() { return endpoint; }
        // timeout, maxRetries, retryDelay 已移到ResilienceService中管理
    }
}