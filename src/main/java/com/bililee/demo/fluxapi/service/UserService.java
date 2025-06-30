package com.bililee.demo.fluxapi.service;

import com.bililee.demo.fluxapi.model.User;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private final Map<String, User> userDatabase = new HashMap<>();

    public UserService() {
        userDatabase.put("1", new User("1", "Alice", "alice@example.com"));
        userDatabase.put("2", new User("2", "Bob", "bob@example.com"));
        userDatabase.put("3", new User("3", "Charlie", "charlie@example.com"));
        userDatabase.put("4", new User("4", "David", "david@example.com"));
    }

    public Mono<User> findUserByIdAsync(String id) {
        System.out.println("[UserService] - 开始异步查找用户 ID: " + id + " (模拟外部数据源调用).");
        return Mono.justOrEmpty(userDatabase.get(id))
                .delayElement(Duration.ofMillis(3000)) // 模拟异步I/O延迟
                .doOnNext(user -> System.out.println("[UserService] - 找到用户 ID: " + id + " 姓名: " + user.getName() + " (数据源返回)."))
                .doOnSuccess(user -> {
                    if (user == null) {
                        System.out.println("[UserService] - 未找到用户 ID: " + id + " (数据源返回空).");
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
        System.out.println("[UserService] - 开始异步获取用户额外详情 ID: " + userId + " (模拟外部数据源调用).");
        String details = "Details for " + userId + ": Likes pizza, plays guitar.";
        return Mono.just(details)
                .delayElement(Duration.ofMillis(200)) // 模拟另一个异步I/O延迟
                .doOnNext(d -> System.out.println("[UserService] - 找到用户额外详情 ID: " + userId + " (数据源返回)."));
    }
}