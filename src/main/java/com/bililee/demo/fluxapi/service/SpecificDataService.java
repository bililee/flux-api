package com.bililee.demo.fluxapi.service;

import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import reactor.core.publisher.Mono;

/**
 * 特定数据服务接口
 */
public interface SpecificDataService {
    
    /**
     * 查询特定数据
     *
     * @param request 查询请求
     * @return 查询结果
     */
    Mono<SpecificDataResponse> querySpecificData(SpecificDataRequest request);
}