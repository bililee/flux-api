# 启动测试指南

## 修复内容

我已经修复了以下启动问题：

### 1. **ConfigSource Bean 缺失**
- ✅ 添加了 `ConfigSourceConfiguration` 配置类
- ✅ 修改了 `NacosConfigSource` 的条件注解为 `matchIfMissing = true`
- ✅ 确保至少有一个 `ConfigSource` Bean 被创建

### 2. **缓存初始化顺序问题**
- ✅ 修复了 `staleCache` 在 `primaryCache` 之前初始化
- ✅ 在 `removalListener` 中添加了 null 检查
- ✅ 延迟启动定时任务，避免初始化时的依赖问题

### 3. **异常处理增强**
- ✅ 在初始化方法中添加了详细的异常处理
- ✅ 在缓存操作中添加了安全检查
- ✅ 定时任务使用了异常包装

## 现在可以启动了

### 方式1: Maven 启动
```bash
mvn clean spring-boot:run
```

### 方式2: JAR 启动
```bash
mvn clean package
java -jar target/flux-api-*.jar
```

## 预期启动日志

如果启动成功，你应该看到类似的日志：

```
INFO - 创建默认配置源（Nacos模拟实现）
INFO - Nacos配置源初始化完成，加载6项默认配置
INFO - 缓存管理器初始化完成
INFO - 启动缓存定时任务
INFO - 缓存策略配置管理器初始化完成，加载3条规则
INFO - 远程服务客户端初始化完成，目标地址: https://remote-datacenter.example.com/api
INFO - 缓存定时任务启动完成
INFO - Started FluxApiApplication in X.XXX seconds
```

## 验证测试

### 1. 健康检查
```bash
curl http://localhost:8080/monitor/health
```

预期响应：
```json
{
  "status": "UP",
  "timestamp": 1234567890,
  "config_source": {
    "type": "nacos",
    "healthy": true
  },
  "cache": {
    "primary_size": 0,
    "stale_size": 0,
    "hit_rate": "0.00%"
  },
  "system": {
    "memory_used_mb": 123,
    "memory_max_mb": 1234,
    "memory_usage_percent": "10.0%"
  }
}
```

### 2. 测试specific_data接口
```bash
curl -X POST http://localhost:8080/v1/specific_data \
  -H "Content-Type: application/json" \
  -H "Source-Id: test_system" \
  -d '{
    "code_selectors": {
      "include": [{"type": "stock_code", "values": ["48:881169"]}]
    },
    "indexes": [
      {"index_id": "security_name"},
      {"index_id": "last_price", "time_type": "DAY_1", "timestamp": 0, "attribute": {}}
    ],
    "page_info": {"page_begin": 0, "page_size": 20}
  }'
```

### 3. 查看缓存状态
```bash
curl http://localhost:8080/monitor/cache/stats
```

## 如果还有问题

如果启动仍然失败，请：

1. **查看完整错误日志**: 
   ```bash
   mvn spring-boot:run > startup.log 2>&1
   ```

2. **检查Java版本**: 确保使用Java 17+
   ```bash
   java -version
   ```

3. **检查端口占用**: 确保8080端口未被占用
   ```bash
   lsof -i :8080  # Mac/Linux
   netstat -ano | findstr :8080  # Windows
   ```

4. **提供具体错误信息**: 将完整的错误堆栈发给我

## 配置说明

现在使用的关键配置：
- `config.source.type=nacos` - 使用Nacos配置源（模拟实现）
- `monitoring.type=console` - 使用控制台监控
- 缓存策略会自动从配置源加载，支持热更新

一切就绪！🚀