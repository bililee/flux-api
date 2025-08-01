# 全局异常处理器使用指南

## 概述

本项目已实现了完善的全局异常处理机制，通过 `GlobalExceptionHandler` 类统一处理应用中的各种异常，确保所有错误响应都遵循一致的格式。

## 支持的异常类型

### 1. 自定义业务异常
- **ApiServerException**: API服务器异常，包含自定义状态码
- **ApiTimeoutException**: API超时异常

### 2. 参数验证异常
- **WebExchangeBindException**: WebFlux参数验证异常
- **MethodArgumentNotValidException**: 方法参数验证异常
- **BindException**: 数据绑定异常
- **ServerWebInputException**: 服务器Web输入异常

### 3. HTTP相关异常
- **ResponseStatusException**: HTTP状态异常
- **HttpMessageNotReadableException**: HTTP消息不可读异常

### 4. JSON处理异常
- **JsonProcessingException**: JSON处理异常

### 5. 系统异常
- **TimeoutException**: 超时异常
- **IllegalArgumentException**: 非法参数异常
- **IllegalStateException**: 非法状态异常
- **NullPointerException**: 空指针异常
- **AccessDeniedException**: 访问被拒绝异常

### 6. Reactor异常
- 自动解包 Reactor 包装的异常，确保正确处理响应式编程中的异常

## 错误响应格式

所有异常都会被转换为统一的 `CommonApiResponse` 格式：

```json
{
  "status_code": 400,
  "status_msg": "参数验证失败: name 不能为空; ",
  "data": null
}
```

## 测试异常处理

项目提供了 `ExceptionTestController` 来测试各种异常处理：

### 测试端点

1. **自定义异常测试**
   - `GET /test/exceptions/api-server-error` - 测试API服务器异常
   - `GET /test/exceptions/api-timeout` - 测试API超时异常

2. **系统异常测试**
   - `GET /test/exceptions/timeout` - 测试超时异常
   - `GET /test/exceptions/illegal-argument` - 测试非法参数异常
   - `GET /test/exceptions/illegal-state` - 测试非法状态异常
   - `GET /test/exceptions/null-pointer` - 测试空指针异常

3. **HTTP异常测试**
   - `GET /test/exceptions/response-status-error/{code}` - 测试HTTP状态异常（如404、500等）

4. **Reactor异常测试**
   - `GET /test/exceptions/reactor-wrapped` - 测试Reactor包装的异常

5. **通用异常测试**
   - `GET /test/exceptions/unknown-error` - 测试未知异常
   - `GET /test/exceptions/json-error` - 测试JSON处理异常

6. **正常响应对比**
   - `GET /test/exceptions/success` - 测试正常响应

### 测试示例

```bash
# 测试API服务器异常
curl http://localhost:8080/test/exceptions/api-server-error

# 测试404异常
curl http://localhost:8080/test/exceptions/response-status-error/404

# 测试超时异常
curl http://localhost:8080/test/exceptions/timeout
```

## 特性

### 1. 统一错误格式
所有异常都转换为 `CommonApiResponse` 格式，确保前端可以统一处理错误。

### 2. 详细日志记录
使用 SLF4J 记录详细的异常信息，包括堆栈跟踪，便于调试和监控。

### 3. 生产环境安全
在生产环境中自动隐藏敏感的错误详情，避免信息泄露。

### 4. Reactor支持
自动解包 Reactor 异常，确保响应式编程中的异常能被正确处理。

### 5. HTTP状态码映射
自动将自定义状态码映射为合适的HTTP状态码。

## 配置

### 环境检测
通过系统属性 `spring.profiles.active` 检测当前环境：
- 生产环境 (`prod`, `production`): 隐藏详细错误信息
- 开发环境 (其他): 显示详细错误信息

### 自定义异常处理
如需添加新的异常处理，可在 `GlobalExceptionHandler` 中添加新的 `@ExceptionHandler` 方法：

```java
@ExceptionHandler(YourCustomException.class)
public ResponseEntity<CommonApiResponse<Void>> handleYourCustomException(YourCustomException ex) {
    log.error("自定义异常: {}", ex.getMessage(), ex);
    
    CommonApiResponse<Void> errorResponse = CommonApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage()
    );
    
    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
}
```

## 最佳实践

1. **使用自定义异常**: 优先使用 `ApiServerException` 和 `ApiTimeoutException` 等自定义异常
2. **添加异常日志**: 在抛出异常前添加适当的日志记录
3. **异常信息安全**: 避免在异常信息中包含敏感数据
4. **响应式异常处理**: 在 Reactor 流中使用 `onErrorResume` 等操作符配合全局异常处理
5. **参数验证**: 使用 Bean Validation 注解进行参数验证，让全局异常处理器统一处理验证错误

## 监控和告警

建议配置日志监控系统来监控异常情况：
- 监控错误日志数量和频率
- 设置关键异常的告警机制
- 定期分析异常模式，优化系统稳定性