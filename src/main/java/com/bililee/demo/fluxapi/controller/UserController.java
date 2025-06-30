package com.bililee.demo.fluxapi.controller;


import com.bililee.demo.fluxapi.model.User;
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

@RestController // 标记这是一个REST控制器
@RequestMapping("/users") // 定义API的基础路径
public class UserController {

    // 简单地用一个Map来模拟数据库存储用户数据
    private final Map<String, User> users = new HashMap<>();

    public UserController() {
        // 初始化一些模拟用户数据
        users.put("1", new User("1", "Alice", "alice@example.com"));
        users.put("2", new User("2", "Bob", "bob@example.com"));
        users.put("3", new User("3", "Charlie", "charlie@example.com"));
    }

    /**
     * 获取所有用户列表
     * GET /users
     * 返回Flux<User>，表示一个包含多个用户对象的异步流
     */
    @GetMapping
    public Flux<User> getAllUsers() {
        // Flux.fromIterable() 将集合转换为响应式流
        // .delayElements(Duration.ofMillis(50)) 模拟异步操作和延迟，使非阻塞特性更明显
        System.out.println("Received request for all users.");
        return Flux.fromIterable(users.values())
                .delayElements(Duration.ofMillis(50)) // 模拟一个小的异步延迟
                .doOnComplete(() -> System.out.println("Finished serving all users."));
    }

    /**
     * 根据ID获取单个用户
     * GET /users/{id}
     * 返回Mono<User>，表示一个包含0或1个用户对象的异步结果
     */
    @GetMapping("/{id}")
    public Mono<User> getUserById(@PathVariable String id) {
        System.out.println("Received request for user with ID: " + id);
        return Mono.justOrEmpty(users.get(id)) // 如果Map中没有找到，则返回Mono.empty()
                .delayElement(Duration.ofMillis(100)) // 模拟一个小的异步延迟
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id)))
                .doOnSuccess(user -> System.out.println("Found user: " + user.getName()))
                .doOnError(error -> System.err.println("Error finding user: " + error.getMessage()));
    }

    /**
     * 创建新用户
     * POST /users
     * 接收Mono<User>，表示异步接收请求体中的用户对象
     * 返回Mono<User>，表示创建成功后的用户对象
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created
    public Mono<User> createUser(@RequestBody User user) {
        System.out.println("Received request to create user: " + user.getName());
        // 模拟生成一个唯一ID
        user.setId(UUID.randomUUID().toString());
        users.put(user.getId(), user);
        return Mono.just(user) // 返回创建成功的用户对象
                .delayElement(Duration.ofMillis(200)) // 模拟一个异步保存操作的延迟
                .doOnSuccess(createdUser -> System.out.println("User created: " + createdUser.getName() + " with ID: " + createdUser.getId()));
    }

    /**
     * 更新用户
     * PUT /users/{id}
     * 接收Mono<User>和PathVariable
     * 返回Mono<User>，表示更新后的用户对象
     */
    @PutMapping("/{id}")
    public Mono<User> updateUser(@PathVariable String id, @RequestBody User user) {
        System.out.println("Received request to update user with ID: " + id);
        return Mono.justOrEmpty(users.get(id))
                .flatMap(existingUser -> {
                    existingUser.setName(user.getName());
                    existingUser.setEmail(user.getEmail());
                    users.put(existingUser.getId(), existingUser); // 更新Map中的用户
                    return Mono.just(existingUser);
                })
                .delayElement(Duration.ofMillis(150)) // 模拟异步更新延迟
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for update with ID: " + id)))
                .doOnSuccess(updatedUser -> System.out.println("User updated: " + updatedUser.getName()))
                .doOnError(error -> System.err.println("Error updating user: " + error.getMessage()));
    }

    /**
     * 删除用户
     * DELETE /users/{id}
     * 返回Mono<Void>，表示没有返回内容
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    public Mono<Void> deleteUser(@PathVariable String id) {
        System.out.println("Received request to delete user with ID: " + id);
        return Mono.fromRunnable(() -> {
                    User removedUser = users.remove(id);
                    if (removedUser == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for deletion with ID: " + id);
                    }
                })
                .delayElement(Duration.ofMillis(100)) // 模拟异步删除延迟
                .then() // 将Mono<Void>转换为没有值的Mono
                .doOnSuccess(v -> System.out.println("User deleted with ID: " + id))
                .doOnError(error -> System.err.println("Error deleting user: " + error.getMessage()));
    }

    /**
     * 示例流式响应：每秒推送一个用户
     * GET /users/stream
     * Content-Type: text/event-stream (Server-Sent Events)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<User> streamAllUsers() {
        System.out.println("Received request for user stream.");
        return Flux.interval(Duration.ofSeconds(1)) // 每秒触发一个事件
                .zipWithIterable(users.values()) // 将计数与用户数据zip起来
                .map(tuple -> tuple.getT2()) // 只取用户数据
                .doOnComplete(() -> System.out.println("Finished streaming all users."))
                .doOnError(error -> System.err.println("Error during streaming: " + error.getMessage()));
    }
}