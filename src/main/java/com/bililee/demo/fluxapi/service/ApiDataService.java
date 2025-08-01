package com.bililee.demo.fluxapi.service;

import com.bililee.demo.fluxapi.common.feign.ExternalApiClient;
import com.bililee.demo.fluxapi.model.ApiData;
import com.bililee.demo.fluxapi.model.ApiRequest;
import com.bililee.demo.fluxapi.model.ApiResponse;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ApiDataService {

    @Resource
    private ExternalApiClient externalApiClient;

    @Autowired
    private Cache<String, ApiData> caffeineCache;

    // 用于防止同一id并发刷新
    private final ConcurrentHashMap<String, AtomicBoolean> refreshing = new ConcurrentHashMap<>();

    // 用于无缓存时同id请求的等待队列
    private final ConcurrentMap<String, Sinks.One<ApiData>> pendingFetch = new ConcurrentHashMap<>();

    public Mono<ApiResponse> getData(String id) {
        // 1. 预处理：将id拆分为多个子id（这里以逗号分隔为例，可根据实际需求修改）
        String[] subIds = id.split(",");
        
        // 2. 对每个子id请求数据，并收集所有结果，失败的用特殊ApiData标记
        return reactor.core.publisher.Flux.fromArray(subIds)
                .flatMap(subId -> fetchDataWithDefault(subId)
                        .timeout(Duration.ofSeconds(5))
                        .onErrorResume(ex -> {
                            // 异常的情况下的默认数据
                            ApiData errorData = new ApiData();
                            errorData.setId(subId);
                            errorData.setContent("[error:" + ex.getClass().getSimpleName() + "]");
                            return Mono.just(errorData);
                        })
                )
                .collectList()
                .map(apiDataList -> {
                    // 3. 合并所有ApiData的content
                    StringBuilder mergedContent = new StringBuilder();
                    for (ApiData data : apiDataList) {
                        if (mergedContent.length() > 0) mergedContent.append(";");
                        mergedContent.append(data.getContent());
                    }
                    // 4. 构造新的ApiData
                    ApiData merged = new ApiData();
                    merged.setId(id);
                    merged.setContent(mergedContent.toString());
                    // 5. 用processData处理并返回
                    return processData(merged);
                });
    }


    private ApiResponse processData(ApiData rawData) {
        // 这里可以添加业务逻辑处理
        rawData.setContent("[Processed] " + rawData.getContent());
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setData(rawData);
        apiResponse.setStatusMsg("");
        apiResponse.setStatusCode(0);
        return apiResponse;
    }

    public Mono<ApiResponse> getApiData(ApiRequest apiRequest) {
        List<String> indicatorIds = apiRequest.getIndicatorIds();
        return reactor.core.publisher.Flux.fromIterable(indicatorIds)
                .flatMap(subId -> {
                    ApiData cached = caffeineCache.getIfPresent(subId);
                    long now = System.currentTimeMillis();
                    if (cached != null && cached.getExpireAt() > now) {
                        // 缓存有效，直接返回
                        return Mono.just(cached);
                    } else if (cached != null) {
                        // 缓存过期，触发异步刷新，但本次仍返回旧数据
                        triggerAsyncRefresh(subId);
                        return Mono.just(cached);
                    } else {
                        // 缓存没有，远程拉取并写入缓存
                        return fetchAndCache(subId);
                    }
                })
                .collectList()
                .map(apiDataList -> {
                    StringBuilder mergedContent = new StringBuilder();
                    for (ApiData data : apiDataList) {
                        if (mergedContent.length() > 0) mergedContent.append(";");
                        mergedContent.append(data.getContent());
                    }
                    ApiData merged = new ApiData();
                    merged.setId(indicatorIds.toString());
                    merged.setContent(mergedContent.toString());
                    return processData(merged);
                });
    }

    /**
     * 包装fetchData，若响应数据为异常（如content为空或为[error:xxx]），则返回默认数据
     */
    private Mono<ApiData> fetchDataWithDefault(String subId) {
        return externalApiClient.fetchData(subId)
                .map(data -> {
                    if (data == null || data.getContent() == null || data.getContent().trim().isEmpty() || data.getContent().startsWith("[error:")) {
                        ApiData defaultData = new ApiData();
                        defaultData.setId(subId);
                        defaultData.setContent("[default]");
                        return defaultData;
                    }
                    return data;
                })
                .onErrorResume(ex -> {
                    ApiData errorData = new ApiData();
                    errorData.setId(subId);
                    errorData.setContent("[error:" + ex.getClass().getSimpleName() + "]");
                    return Mono.just(errorData);
                });
    }

    private Mono<ApiData> fetchAndCache(String subId) {
        // 如果已有等待队列，说明已有请求在拉取，直接等待
        Sinks.One<ApiData> sink = pendingFetch.computeIfAbsent(subId, k -> Sinks.one());
        if (sink.currentSubscriberCount() > 0) {
            // 有其他请求在等待，最多等3秒
            return sink.asMono().timeout(Duration.ofSeconds(3)).onErrorResume(e -> {
                // 超时后再查一次缓存
                ApiData cached = caffeineCache.getIfPresent(subId);
                if (cached != null) {
                    return Mono.just(cached);
                } else {
                    ApiData errorData = new ApiData();
                    errorData.setId(subId);
                    errorData.setContent("[error:Timeout]");
                    errorData.setExpireAt(System.currentTimeMillis() + 10_000); // 错误缓存10秒
                    return Mono.just(errorData);
                }
            });
        }
        // 只有第一个请求会走到这里，负责远程拉取
        return fetchDataWithDefault(subId)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(ex -> {
                    ApiData errorData = new ApiData();
                    errorData.setId(subId);
                    errorData.setContent("[error:" + ex.getClass().getSimpleName() + "]");
                    errorData.setExpireAt(System.currentTimeMillis() + 60_000); // 错误缓存1分钟
                    caffeineCache.put(subId, errorData);
                    sink.tryEmitValue(errorData);
                    pendingFetch.remove(subId);
                    return Mono.just(errorData);
                })
                .map(data -> {
                    data.setExpireAt(System.currentTimeMillis() + 10 * 60_000); // 正常数据缓存10分钟
                    caffeineCache.put(subId, data);
                    sink.tryEmitValue(data);
                    pendingFetch.remove(subId);
                    return data;
                });
    }

    private void triggerAsyncRefresh(String subId) {
        refreshing.computeIfAbsent(subId, k -> new AtomicBoolean(false));
        AtomicBoolean flag = refreshing.get(subId);
        if (flag.compareAndSet(false, true)) {
            fetchAndCache(subId).doFinally(sig -> flag.set(false)).subscribe();
        }
    }
}
