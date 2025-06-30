package com.bililee.demo.fluxapi.controller;


import com.bililee.demo.fluxapi.model.User;
import com.bililee.demo.fluxapi.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

// 注意：其他方法如 getAllUsers, createUser, updateUser, deleteUser, streamAllUsers 保持不变，
// 或者根据你实际的项目需要进行调整。这里只展示getUserById的核心修改。

@RestController
@RequestMapping("/users")
public class UserController {

    // 注入 UserService。
    // Spring 会自动创建 UserService 的实例并注入到这里。
    private final UserService userService;

    // 构造函数，用于依赖注入 UserService
    public UserController(UserService userService) {
        this.userService = userService;
        // 如果内部仍然需要模拟一些用户数据用于其他方法的简单演示，可以保留，否则可移除
        // 例如，如果getAllUsers不从UserService获取，则可能需要内部数据
        // private final Map<String, User> internalUsers = new HashMap<>();
        // internalUsers.put("1", new User("1", "Alice", "alice@example.com"));
        // ...
    }

    /**
     * 根据ID获取单个用户。
     * 该接口的核心处理逻辑是异步请求 UserService（模拟的外部数据源）来获取用户数据。
     *
     * GET /users/{id}
     *
     * @param id 用户唯一标识符。
     * @return Mono<User> 包含用户数据的异步响应流；如果用户不存在则返回Mono.error(HttpStatus.NOT_FOUND)。
     */
    @GetMapping("/{id}")
    public Mono<User> getUserById(@PathVariable String id) {
        // 控制器收到请求的日志
        System.out.println("[UserController] - 收到获取用户 ID: " + id + " 的请求.");

        // 调用 userService 的异步方法 findUserByIdAsync 来获取用户数据。
        // userService.findUserByIdAsync(id) 返回一个 Mono<User>，表示一个异步操作。
        return userService.findUserByIdAsync(id)
                // 如果 Mono<User> 返回 Mono.empty() (即用户不存在)，则抛出 404 NOT_FOUND 异常。
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id)))
                // 当Mono成功发出数据时（找到用户），记录日志
                .doOnSuccess(user -> System.out.println("[UserController] - 成功从UserService获取到用户 ID: " + id + " 姓名: " + user.getName()))
                // 当Mono发出错误时（例如用户不存在导致的404），记录错误日志
                .doOnError(error -> System.err.println("[UserController] - 处理用户 ID: " + id + " 请求失败: " + error.getMessage()));
    }

    // ... 其他方法（如 getAllUsers, createUser, updateUser, deleteUser, streamAllUsers）保持不变或根据需要调整。
    // 请确保您的项目中包含了UserService.java 和 User.java 文件。
}