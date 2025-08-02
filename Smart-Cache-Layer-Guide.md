# 智能缓存层架构使用指南

## 🏗️ 架构概述

本项目实现了一个基于Spring WebFlux的智能缓存层，主要用作跨机房的流量缓存代理。支持多种缓存策略、请求去重、降级保护和实时监控。

### 核心特性

- ✅ **多级缓存策略**: 主动缓存/被动缓存/直接透传
- ✅ **请求去重**: 相同请求合并处理，避免重复调用
- ✅ **动态配置**: 支持Nacos等配置中心，热加载无需重启
- ✅ **降级保护**: 远程服务异常时返回过期缓存或默认响应
- ✅ **业务隔离**: 基于Source-Id的业务维度缓存策略
- ✅ **内存优化**: 针对6C8G服务器优化，避免GC影响
- ✅ **监控告警**: 丰富的监控指标和可扩展的监控系统
- ✅ **异步处理**: 全异步架构，远程服务不阻塞本地服务

## 🔧 快速开始

### 1. 启动服务

```bash
# 使用缓存配置启动
java -jar flux-api.jar --spring.profiles.active=cache

# 或者指定配置文件
java -jar flux-api.jar --spring.config.location=classpath:application-cache.properties
```

### 2. 基本调用

```bash
curl -X POST http://localhost:8080/v1/specific_data \
  -H "Content-Type: application/json" \
  -H "Source-Id: trading_system" \
  -d '{
    "code_selectors": {
      "include": [
        {
          "type": "stock_code",
          "values": ["48:881169"]
        }
      ]
    },
    "indexes": [
      {
        "index_id": "security_name"
      },
      {
        "index_id": "last_price",
        "time_type": "DAY_1",
        "timestamp": 0,
        "attribute": {}
      }
    ],
    "page_info": {
      "page_begin": 0,
      "page_size": 20
    }
  }'
```

### 3. 监控端点

```bash
# 健康检查
curl http://localhost:8080/monitor/health

# 缓存统计
curl http://localhost:8080/monitor/cache/stats

# 系统指标
curl http://localhost:8080/monitor/metrics

# 生成监控报告
curl http://localhost:8080/monitor/report
```

## 📋 配置管理

### 缓存策略配置

支持通过Nacos等配置中心动态配置缓存策略：

```json
{
  "pattern": {
    "code": "48:.*",
    "index": "last_price",
    "source_id": "trading_system"
  },
  "strategy": "ACTIVE",
  "cache_ttl": "5m",
  "refresh_interval": "30s",
  "allow_stale_data": true,
  "priority": 10
}
```

### 配置说明

| 字段 | 说明 | 示例 |
|------|------|------|
| `pattern.code` | 股票代码正则匹配 | `"48:.*"` |
| `pattern.index` | 指标ID正则匹配 | `"last_price"` |
| `pattern.source_id` | 业务来源正则匹配 | `"trading_system"` |
| `strategy` | 缓存策略 | `ACTIVE`/`PASSIVE`/`NO_CACHE` |
| `cache_ttl` | 缓存过期时间 | `"5m"`, `"30s"`, `"1h"` |
| `refresh_interval` | 主动刷新间隔 | `"30s"` |
| `allow_stale_data` | 是否允许返回过期数据 | `true`/`false` |
| `priority` | 优先级（数字越小优先级越高） | `10` |

### 远程服务配置

```json
{
  "base_url": "https://remote-datacenter.example.com/api",
  "endpoint": "/v1/specific_data",
  "timeout": "5s",
  "max_retries": 2,
  "retry_delay": "500ms",
  "circuit_breaker": {
    "failure_threshold": 5,
    "recovery_timeout": "30s"
  }
}
```

## 🚀 缓存策略详解

### 1. 主动缓存 (ACTIVE)

**适用场景**: 高频访问的实时数据，如股价、指数等

**工作原理**:
- 数据定期自动刷新（refresh_interval）
- 用户请求优先返回缓存数据
- 后台异步更新缓存，不阻塞用户请求

**配置示例**:
```json
{
  "pattern": {
    "code": "48:881169",
    "index": "last_price"
  },
  "strategy": "ACTIVE",
  "cache_ttl": "5m",
  "refresh_interval": "30s"
}
```

### 2. 被动缓存 (PASSIVE)

**适用场景**: 中等频率访问的数据，如股票基本信息

**工作原理**:
- 首次请求时获取并缓存数据
- 缓存过期后下次请求时重新获取
- 支持返回过期数据以提高可用性

**配置示例**:
```json
{
  "pattern": {
    "code": "[0-9]{6}",
    "index": "security_name"
  },
  "strategy": "PASSIVE",
  "cache_ttl": "1h",
  "allow_stale_data": true
}
```

### 3. 直接透传 (NO_CACHE)

**适用场景**: 实时性要求极高的数据

**工作原理**:
- 不使用缓存，直接调用远程服务
- 仍然支持请求去重
- 远程服务异常时可返回默认响应

**配置示例**:
```json
{
  "pattern": {
    "index": "realtime_.*"
  },
  "strategy": "NO_CACHE"
}
```

## 🔄 请求去重机制

### 工作原理

1. **请求键生成**: 基于请求参数和Source-Id生成唯一键
2. **首请求处理**: 第一个请求执行实际逻辑
3. **后续请求等待**: 相同请求加入等待队列
4. **结果广播**: 首请求完成后，结果同时响应给所有等待的请求

### 优势

- **减少远程调用**: 避免短时间内重复请求
- **提高响应速度**: 后续请求直接获得结果
- **降低系统负载**: 减少对远程服务的压力

## 📊 监控与告警

### 监控指标

#### 缓存相关
- `cache.access` - 缓存访问次数（命中/未命中）
- `cache.refresh` - 缓存刷新次数（成功/失败）
- `cache.primary.size` - 主缓存大小
- `cache.primary.hit_rate` - 主缓存命中率

#### 远程调用相关
- `remote.call` - 远程调用次数（成功/失败）
- `remote.call.duration` - 远程调用耗时
- `api.response.duration` - API响应耗时

#### 请求去重相关
- `request.deduplication` - 请求去重统计
- `request.wait.duration` - 请求等待时间
- `request.pending.count` - 等待队列大小

#### 降级相关
- `fallback.triggered` - 降级触发次数
- `business.error` - 业务错误统计

### 监控端点

| 端点 | 功能 | 示例 |
|------|------|------|
| `/monitor/health` | 健康检查 | 整体服务状态 |
| `/monitor/cache/stats` | 缓存统计 | 命中率、大小等 |
| `/monitor/metrics` | 监控指标 | 所有指标快照 |
| `/monitor/config` | 配置信息 | 当前配置状态 |
| `/monitor/system` | 系统信息 | CPU、内存等 |
| `/monitor/report` | 生成报告 | 详细监控报告 |

## 🛡️ 降级策略

### 降级触发条件

1. 远程服务调用超时
2. 远程服务返回错误
3. 网络连接异常
4. 熔断器开启

### 降级处理流程

```mermaid
graph TD
    A[远程调用失败] → B{检查过期缓存}
    B →|有过期数据| C[返回过期缓存]
    B →|无过期数据| D[返回默认错误响应]
    C → E[触发异步修复]
    D → E
    E → F[记录降级指标]
```

### 配置降级策略

```yaml
cache:
  fallback:
    enable_stale_cache: true
    stale_cache_ttl: 2h
    default_error_message: "服务暂时不可用，请稍后重试"
    enable_async_repair: true
```

## 🔧 扩展配置源

### 支持的配置源

- **Nacos**: 生产推荐，支持热更新
- **MySQL**: 数据库配置，适合复杂场景
- **Apollo**: 携程配置中心
- **Local File**: 本地文件，开发测试用

### 扩展新配置源

1. 实现 `ConfigSource` 接口
2. 添加 `@ConditionalOnProperty` 条件
3. 实现配置监听和热更新

```java
@Component
@ConditionalOnProperty(name = "config.source.type", havingValue = "mysql")
public class MySqlConfigSource implements ConfigSource {
    // 实现配置源逻辑
}
```

## 📈 性能优化

### 内存优化

- **主缓存**: 最大8000条记录，约500MB
- **过期缓存**: 最大2000条记录，约125MB
- **自动清理**: 定期清理过期数据
- **GC友好**: 避免大对象分配

### 并发优化

- **异步处理**: 全流程异步，不阻塞请求
- **线程池**: 独立的刷新线程池
- **无锁设计**: 使用ConcurrentHashMap等并发安全容器

### 网络优化

- **连接复用**: WebClient连接池
- **超时控制**: 分层超时保护
- **重试机制**: 指数退避重试

## 🧪 测试方法

### 单元测试

```bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=SpecificDataServiceImplTest
```

### 压力测试

```bash
# 使用Apache Bench
ab -n 1000 -c 10 -T application/json \
   -H "Source-Id: test_system" \
   -p test_data.json \
   http://localhost:8080/v1/specific_data

# 使用JMeter
jmeter -n -t cache_test.jmx -l results.jtl
```

### 缓存策略测试

```bash
# 测试主动缓存
curl -H "Source-Id: trading_system" \
     -d @active_cache_request.json \
     http://localhost:8080/v1/specific_data

# 测试被动缓存
curl -H "Source-Id: info_system" \
     -d @passive_cache_request.json \
     http://localhost:8080/v1/specific_data

# 测试直接透传
curl -H "Source-Id: realtime_system" \
     -d @no_cache_request.json \
     http://localhost:8080/v1/specific_data
```

## 🚨 故障排查

### 常见问题

1. **缓存命中率低**
   - 检查缓存策略配置
   - 查看监控指标
   - 调整缓存TTL

2. **远程调用失败**
   - 检查网络连接
   - 查看远程服务状态
   - 调整超时和重试配置

3. **内存使用过高**
   - 检查缓存大小设置
   - 查看GC日志
   - 调整缓存容量

### 日志分析

```bash
# 查看缓存相关日志
grep "cache" application.log

# 查看远程调用日志
grep "remote" application.log

# 查看降级日志
grep "fallback" application.log
```

## 📝 最佳实践

1. **配置管理**
   - 使用版本控制管理配置
   - 分环境配置策略
   - 监控配置变更

2. **监控告警**
   - 设置关键指标阈值
   - 配置告警通知
   - 定期检查监控报告

3. **容量规划**
   - 根据业务量调整缓存大小
   - 监控内存使用情况
   - 预留资源余量

4. **版本升级**
   - 灰度发布新版本
   - 监控关键指标
   - 准备回滚方案

## 🔗 相关文档

- [SpecificData API测试指南](./SpecificData-API-Test-Guide.md)
- [全局异常处理指南](./Exception-Handling-Guide.md)
- [Spring WebFlux官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [Caffeine缓存官方文档](https://github.com/ben-manes/caffeine)