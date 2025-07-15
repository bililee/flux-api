package com.bililee.demo.fluxapi.common.feign;

import com.bililee.demo.fluxapi.model.ApiData;
import feign.Param;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

// ExternalApiClient.java
@FeignClient(name = "externalApi", url = "${external.api.url}")
public interface ExternalApiClient {

    @GetMapping("/test/fetch")
    Mono<ApiData> fetchData(@Param String id);
}
