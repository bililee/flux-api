package com.bililee.demo.fluxapi.service;

import com.bililee.demo.fluxapi.common.feign.ExternalApiClient;
import com.bililee.demo.fluxapi.model.ApiData;
import com.bililee.demo.fluxapi.model.ApiRequest;
import com.bililee.demo.fluxapi.model.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
public class ApiDataService {

    @Resource
    private ExternalApiClient externalApiClient;

    public Mono<ApiResponse> getData(String id) {
        // 1. 预处理：将id拆分为多个子id（这里以逗号分隔为例，可根据实际需求修改）
        String[] subIds = id.split(",");
        
        // 2. 对每个子id请求数据，并收集所有结果，失败的用特殊ApiData标记
        return reactor.core.publisher.Flux.fromArray(subIds)
                .flatMap(subId -> externalApiClient.fetchData(subId)
                        .timeout(Duration.ofSeconds(5))
                        .onErrorResume(ex -> {
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
        // 1. 预处理：将id拆分为多个子id（这里以逗号分隔为例，可根据实际需求修改）
        List<String> indicatorIds = apiRequest.getIndicatorIds();
        
        // 2. 对每个子id请求数据，并收集所有结果，失败的用特殊ApiData标记
        return reactor.core.publisher.Flux.fromArray(indicatorIds.toArray(new String[0]))
                .flatMap(subId -> externalApiClient.fetchData(subId)
                        .timeout(Duration.ofSeconds(5))
                        .onErrorResume(ex -> {
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
                    merged.setId(indicatorIds.toString());
                    merged.setContent(mergedContent.toString());
                    // 5. 用processData处理并返回
                    return processData(merged);
                });
    }

}
