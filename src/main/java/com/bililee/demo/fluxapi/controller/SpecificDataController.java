package com.bililee.demo.fluxapi.controller;

import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.service.impl.SpecificDataServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 特定数据控制器
 * 提供 v1/specific_data 接口，支持基于Source-Id的业务缓存策略
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class SpecificDataController {

    @Autowired
    private SpecificDataServiceImpl specificDataService;

    /**
     * 查询特定数据
     * POST /v1/specific_data
     *
     * @param request 查询请求
     * @param sourceId 业务来源标识（从请求头获取）
     * @return 查询结果
     */
    @PostMapping("/specific_data")
    public Mono<ResponseEntity<SpecificDataResponse>> querySpecificData(
            @RequestBody SpecificDataRequest request,
            @RequestHeader(value = "Source-Id", required = false) String sourceId) {

        try {
            // 如果没有提供Source-Id，使用默认值
            String actualSourceId = sourceId != null ? sourceId : "default";
            
            log.info("收到特定数据查询请求 - sourceId: {}, 代码数量: {}", 
                    actualSourceId, 
                    request.codeSelectors().include().stream()
                            .mapToInt(selector -> selector.values().size()).sum());

            return specificDataService.querySpecificData(request, actualSourceId)
                    .map(response -> {
                        log.info("特定数据查询成功 - sourceId: {}, 返回数据量: {}", 
                                actualSourceId, response.data().total());
                        return ResponseEntity.ok(response);
                    })
                    .doOnError(error -> log.error("特定数据查询失败 - sourceId: {}, error: {}", 
                            actualSourceId, error.getMessage(), error))
                    .onErrorReturn(ResponseEntity.ok(SpecificDataResponse.error(
                            500, "服务内部错误，请稍后重试")));

        } catch (Exception ex) {
            log.error("处理特定数据查询请求时发生异常 - sourceId: {}, error: {}", 
                    sourceId, ex.getMessage(), ex);
            
            SpecificDataResponse errorResponse = SpecificDataResponse.error(
                    500,
                    "处理请求时发生内部错误"
            );
            
            return Mono.just(ResponseEntity.ok(errorResponse));
        }
    }
}