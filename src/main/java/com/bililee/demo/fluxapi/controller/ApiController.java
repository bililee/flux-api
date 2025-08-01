package com.bililee.demo.fluxapi.controller;

import com.bililee.demo.fluxapi.model.ApiRequest;
import com.bililee.demo.fluxapi.model.ApiResponse;
import com.bililee.demo.fluxapi.service.ApiDataService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/dataapi")
public class ApiController {

    @Resource
    private ApiDataService apiDataService;

    @GetMapping("/{id}")
    public Mono<ApiResponse> getUserById(@PathVariable String id) {
        // 控制器收到请求的日志
        System.out.println("[UserController] - 收到获取用户 ID: " + id + " 的请求.");

        // 调用 userService 的异步方法 findUserByIdAsync 来获取用户数据。
        // userService.findUserByIdAsync(id) 返回一个 Mono<User>，表示一个异步操作。
        return apiDataService.getData(id);
    }

    @PostMapping("/fetch_data")
    public Mono<ApiResponse> fetchData(
        @RequestBody ApiRequest apiRequest
    ) {
        // 控制器收到请求的日志
        return apiDataService.getApiData(apiRequest);
    }

}
