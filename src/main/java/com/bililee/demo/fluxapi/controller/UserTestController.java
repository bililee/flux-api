package com.bililee.demo.fluxapi.controller;


import com.bililee.demo.fluxapi.model.User;
import com.bililee.demo.fluxapi.response.CommonApiResponse;
import com.bililee.demo.fluxapi.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users-test")
public class UserTestController {

    private final UserService userService;
    private final Map<String, User> internalUsers = new HashMap<>();

    public UserTestController(UserService userService) {
        this.userService = userService;
        internalUsers.put("1", new User("1", "Alice", "alice@example.com"));
        internalUsers.put("2", new User("2", "Bob", "bob@example.com"));
        internalUsers.put("3", new User("3", "Charlie", "charlie@example.com"));
    }

    /**
     * 获取所有用户列表
     * 返回 Flux<CommonApiResponse<User>>
     */
    @GetMapping
    public Flux<CommonApiResponse<User>> getAllUsers() {
        System.out.println("[UserController] - 收到获取所有用户的请求.");
        return Flux.fromIterable(internalUsers.values())
                .delayElements(Duration.ofMillis(50))
                .map(CommonApiResponse::success) // 包装每个用户对象为成功响应
                .doOnComplete(() -> System.out.println("[UserController] - 所有用户请求处理完成."));
    }

    /**
     * 根据ID获取单个用户 (异步请求数据源)
     * 返回 Mono<CommonApiResponse<User>>
     */
    @GetMapping("/{id}")
    public Mono<CommonApiResponse<User>> getUserById(@PathVariable String id) {
        System.out.println("[UserController] - 收到获取用户 ID: " + id + " 的请求.");
        return userService.findUserByIdAsync(id)
                .map(CommonApiResponse::success) // 找到用户，包装为成功响应
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id)))
                .doOnSuccess(userApiResp -> System.out.println("[UserController] - 成功处理用户 ID: " + id + " 的请求."))
                .doOnError(error -> System.err.println("[UserController] - 处理用户 ID: " + id + " 请求失败: " + error.getMessage()));
    }

    /**
     * 获取用户及其组合的详细信息 (展示多数据源异步聚合)
     * 返回 Mono<CommonApiResponse<Map<String, Object>>>
     */
    @GetMapping("/{id}/details")
    public Mono<CommonApiResponse<Map<String, Object>>> getUserWithCombinedDetails(@PathVariable String id) {
        System.out.println("[UserController] - 收到获取用户 ID: " + id + " 组合详情的请求.");

        Mono<User> userMono = userService.findUserByIdAsync(id);
        Mono<String> detailsMono = userService.getUserAdditionalDetailsAsync(id);

        return Mono.zip(userMono, detailsMono)
                .map(tuple -> {
                    User user = tuple.getT1();
                    String details = tuple.getT2();
                    Map<String, Object> combinedInfo = new HashMap<>();
                    combinedInfo.put("user", user);
                    combinedInfo.put("additionalDetails", details);
                    return CommonApiResponse.success(combinedInfo); // 包装组合后的数据为成功响应
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User or details not found for ID: " + id)))
                .doOnSuccess(apiResp -> System.out.println("[UserController] - 成功处理用户 ID: " + id + " 组合详情的请求."))
                .doOnError(error -> System.err.println("[UserController] - 处理用户 ID: " + id + " 组合详情请求失败: " + error.getMessage()));
    }

    /**
     * 创建新用户
     * 返回 Mono<CommonApiResponse<User>>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CommonApiResponse<User>> createUser(@RequestBody User user) {
        System.out.println("[UserController] - 收到创建用户请求: " + user.getName());
        user.setId(UUID.randomUUID().toString());
        internalUsers.put(user.getId(), user);
        return Mono.just(user)
                .delayElement(Duration.ofMillis(200))
                .map(CommonApiResponse::success) // 包装创建的用户为成功响应
                .doOnSuccess(createdUserResp -> System.out.println("[UserController] - 用户创建成功: " + createdUserResp.getData().getName() + " ID: " + createdUserResp.getData().getId()));
    }

    /**
     * 更新用户
     * 返回 Mono<CommonApiResponse<User>>
     */
    @PutMapping("/{id}")
    public Mono<CommonApiResponse<User>> updateUser(@PathVariable String id, @RequestBody User user) {
        System.out.println("[UserController] - 收到更新用户 ID: " + id + " 的请求.");
        return Mono.justOrEmpty(internalUsers.get(id))
                .flatMap(existingUser -> {
                    existingUser.setName(user.getName());
                    existingUser.setEmail(user.getEmail());
                    internalUsers.put(existingUser.getId(), existingUser);
                    return Mono.just(existingUser);
                })
                .delayElement(Duration.ofMillis(150))
                .map(CommonApiResponse::success) // 包装更新的用户为成功响应
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for update with ID: " + id)))
                .doOnSuccess(updatedUserResp -> System.out.println("[UserController] - 用户更新成功: " + updatedUserResp.getData().getName()))
                .doOnError(error -> System.err.println("[UserController] - 更新用户失败: " + error.getMessage()));
    }

    /**
     * 删除用户
     * 返回 Mono<CommonApiResponse<Void>> (因为没有数据返回，但需要包装)
     */
    @DeleteMapping("/{id}")
    public Mono<CommonApiResponse<Void>> deleteUser(@PathVariable String id) {
        System.out.println("[UserController] - 收到删除用户 ID: " + id + " 的请求.");
        return Mono.fromRunnable(() -> {
                    User removedUser = internalUsers.remove(id);
                    if (removedUser == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for deletion with ID: " + id);
                    }
                })
                .delayElement(Duration.ofMillis(100))
                .then(Mono.just(CommonApiResponse.success())) // 删除成功，返回不带数据的成功响应
                .doOnSuccess(v -> System.out.println("[UserController] - 用户删除成功: " + id))
                .doOnError(error -> System.err.println("[UserController] - 删除用户失败: " + error.getMessage()));
    }

    /**
     * 示例流式响应：每秒推送一个用户
     * 返回 Flux<CommonApiResponse<User>>
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CommonApiResponse<User>> streamAllUsers() {
        System.out.println("[UserController] - 收到用户流请求.");
        return Flux.interval(Duration.ofSeconds(1))
                .zipWithIterable(internalUsers.values())
                .map(tuple -> tuple.getT2())
                .map(CommonApiResponse::success) // 包装每个流式用户为成功响应
                .doOnComplete(() -> System.out.println("[UserController] - 用户流传输完成."))
                .doOnError(error -> System.err.println("[UserController] - 用户流传输失败: " + error.getMessage()));
    }
}