package com.bililee.demo.fluxapi.service;


import com.bililee.demo.fluxapi.model.User;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service // 标记这是一个Spring服务组件
public class UserService {

    // 模拟的用户数据存储，这里假设是来自外部的数据库或另一个微服务
    private final Map<String, User> userDatabase = new HashMap<>();

    public UserService() {
        userDatabase.put("1", new User("1", "Alice", "alice@example.com"));
        userDatabase.put("2", new User("2", "Bob", "bob@example.com"));
        userDatabase.put("3", new User("3", "Charlie", "charlie@example.com"));
        userDatabase.put("4", new User("4", "David", "david@example.com"));
    }

    /**
     * 异步模拟从数据源获取用户信息。
     * 这是一个I/O密集型操作的模拟，实际可能是数据库查询、外部API调用等。
     *
     * @param id 用户ID
     * @return 包含用户对象的Mono，如果未找到则为Mono.empty()
     */
    public Mono<User> findUserByIdAsync(String id) {
        System.out.println("[UserService] - 开始异步查找用户 ID: " + id);
        // Mono.justOrEmpty(userDatabase.get(id)) 用于从Map中获取数据并创建Mono。
        // .delayElement(Duration.ofMillis(300)) 模拟网络延迟或数据库查询时间，
        // 但这个操作是非阻塞的，当前处理线程不会等待300毫秒。
        return Mono.justOrEmpty(userDatabase.get(id))
                .delayElement(Duration.ofMillis(300)) // 模拟异步I/O延迟
                .doOnNext(user -> System.out.println("[UserService] - 找到用户 ID: " + id + " 姓名: " + user.getName()))
                .doOnSuccess(user -> {
                    if (user == null) {
                        System.out.println("[UserService] - 未找到用户 ID: " + id);
                    }
                });
    }

    /**
     * 异步模拟从另一个数据源获取用户的额外详细信息（例如：用户偏好、最近活动）。
     *
     * @param userId 用户ID
     * @return 包含用户详细信息字符串的Mono
     */
    public Mono<String> getUserAdditionalDetailsAsync(String userId) {
        System.out.println("[UserService] - 开始异步获取用户额外详情 ID: " + userId);
        String details = "Details for " + userId + ": Likes pizza, plays guitar.";
        // Mono.just(details) 创建包含详情的Mono。
        // .delayElement(Duration.ofMillis(200)) 模拟另一个异步I/O延迟。
        return Mono.just(details)
                .delayElement(Duration.ofMillis(200))
                .doOnNext(d -> System.out.println("[UserService] - 找到用户额外详情 ID: " + userId));
    }
}