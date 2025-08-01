package com.bililee.demo.fluxapi.controller;

import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.service.SpecificDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 特定数据控制器
 * 提供 v1/specific_data 接口
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class SpecificDataController {

    @Autowired
    private SpecificDataService specificDataService;

    /**
     * 查询特定数据
     * POST /v1/specific_data
     *
     * @param request 查询请求
     * @return 查询结果
     */
    @PostMapping("/specific_data")
    public Mono<ResponseEntity<SpecificDataResponse>> querySpecificData(
            @RequestBody SpecificDataRequest request) {

        try {
            log.info("收到特定数据查询请求: {}", request);

            return specificDataService.querySpecificData(request)
                    .map(response -> {
                        log.info("特定数据查询成功，返回数据量: {}", response.data().total());
                        return ResponseEntity.ok(response);
                    })
                    .doOnError(error -> log.error("特定数据查询失败: {}", error.getMessage(), error));

        } catch (Exception ex) {
            log.error("处理特定数据查询请求时发生异常: {}", ex.getMessage(), ex);
            
            SpecificDataResponse errorResponse = SpecificDataResponse.error(
                    500,
                    "处理请求时发生内部错误: " + ex.getMessage()
            );
            
            return Mono.just(ResponseEntity.ok(errorResponse));
        }
    }
}