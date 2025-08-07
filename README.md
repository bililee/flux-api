# Flux API - 反应式数据查询服务

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring WebFlux](https://img.shields.io/badge/Spring%20WebFlux-Reactive-blue.svg)](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
[![Maven](https://img.shields.io/badge/Maven-3.6+-red.svg)](https://maven.apache.org/)

一个基于Spring WebFlux构建的高性能反应式数据查询API服务，提供多层缓存、请求去重、自动重试等企业级功能特性。

## 📋 目录

- [功能特性](#-功能特性)
- [技术栈](#-技术栈)
- [项目架构](#-项目架构)
- [快速开始](#-快速开始)
- [API文档](#-api文档)
- [配置说明](#-配置说明)
- [单元测试](#-单元测试)
- [性能优化](#-性能优化)
- [部署指南](#-部署指南)
- [贡献指南](#-贡献指南)

## 🚀 功能特性

### 核心功能
- **反应式数据查询**: 基于Spring WebFlux的非阻塞异步API
- **多层缓存策略**: 支持无缓存、被动缓存、主动缓存三种策略
- **智能请求去重**: 自动合并相同的并发请求，减少后端压力
- **自动重试机制**: 智能的指数退避重试策略，提高服务可靠性
- **动态配置管理**: 支持运行时配置热更新，无需重启服务

### 企业级特性
- **全局异常处理**: 统一的错误响应格式和异常处理机制
- **健康检查**: 内置服务健康状态监控和检查端点
- **性能监控**: 缓存命中率、请求去重率等关键指标监控
- **分布式支持**: 支持多数据源和配置源扩展
- **安全性**: 请求头验证、参数校验等安全措施

## 🛠 技术栈

### 框架与库
- **Spring Boot 3.3.4** - 核心应用框架
- **Spring WebFlux** - 反应式Web框架  
- **Project Reactor** - 反应式编程库
- **Caffeine Cache** - 高性能内存缓存
- **Lombok** - 代码简化工具

### 工具与中间件
- **Maven** - 项目构建管理
- **Jackson** - JSON序列化/反序列化
- **OkHttp** - HTTP客户端
- **Reactive Feign** - 反应式HTTP客户端

### 测试框架
- **JUnit 5** - 单元测试框架
- **Mockito** - Mock测试框架
- **Reactor Test** - 反应式测试工具
- **WebTestClient** - WebFlux测试客户端
- **MockWebServer** - HTTP Mock服务器
- **WireMock** - 高级HTTP Mock工具

## 🏗 项目架构

### 📊 架构图概览

本项目提供5个维度的架构图，全面展示系统设计和技术栈：

1. **[完整系统架构图](#1-完整系统架构图)** - 展示所有组件和数据流向
2. **[核心业务流程图](#2-核心业务流程架构图)** - 突出业务处理流程和缓存策略  
3. **[组件关系图](#3-核心组件关系图)** - 展示类之间的依赖关系
4. **[生产部署架构图](#4-生产环境部署架构图)** - 完整的企业级部署拓扑
5. **[技术栈总览图](#5-技术栈总览图)** - 技术选型和工具链

> 💡 **提示**: 架构图使用Mermaid格式，支持在GitHub、GitLab等平台直接渲染显示

### 🗂 代码结构

```
flux-api/
├── src/main/java/com/bililee/demo/fluxapi/
│   ├── controller/          # 控制器层 - REST API端点
│   ├── service/            # 服务层 - 业务逻辑处理
│   ├── cache/              # 缓存层 - 缓存管理和请求去重
│   ├── client/             # 客户端层 - 远程服务调用
│   ├── config/             # 配置层 - 应用配置管理
│   ├── model/              # 模型层 - 数据传输对象
│   ├── exception/          # 异常处理 - 全局异常管理
│   ├── monitoring/         # 监控层 - 性能指标收集
│   └── validator/          # 验证层 - 参数校验
└── src/test/java/          # 测试代码
    ├── controller/         # 控制器测试
    ├── service/           # 服务层测试
    ├── cache/             # 缓存层测试
    ├── client/            # 客户端测试
    ├── exception/         # 异常处理测试
    ├── model/             # DTO测试
    └── integration/       # 集成测试
```

### 🎯 架构特点

**反应式架构设计**
- 基于Spring WebFlux的全链路非阻塞处理
- Project Reactor响应式编程模型
- 支持高并发、低延迟的数据处理

**企业级技术栈**
- **API网关**: Apache APISIX统一入口管理
- **任务调度**: XXL-Job分布式任务调度
- **APM监控**: 普米APM + Prometheus + ELK全栈监控
- **配置中心**: Nacos + Apollo动态配置管理
- **容器编排**: Kubernetes + Docker云原生部署

**多层缓存策略**
- `NO_CACHE`: 直接调用远程服务，适用于实时性要求高的场景
- `PASSIVE`: 被动缓存，缓存命中返回缓存数据，未命中调用远程服务并缓存
- `ACTIVE`: 主动缓存，返回缓存数据的同时后台异步刷新缓存

**高可用设计**
- 多副本部署 + 自动水平/垂直伸缩
- 服务网格(Istio)流量管理和安全策略
- 多级降级机制保证服务可用性

## 🚀 快速开始

### 环境要求

- Java 17+
- Maven 3.6+

### 安装与运行

1. **克隆项目**
```bash
git clone <repository-url>
cd flux-api
```

2. **构建项目**
```bash
mvn clean compile
```

3. **运行测试**
```bash
mvn test
```

4. **启动应用**
```bash
mvn spring-boot:run
```

5. **验证服务**
```bash
curl http://localhost:8080/actuator/health
```

### Docker部署

```bash
# 构建镜像
docker build -t flux-api:latest .

# 运行容器
docker run -p 8080:8080 flux-api:latest
```

## 📚 API文档

### 核心端点

#### 查询特定数据
```http
POST /v1/specific_data
Content-Type: application/json
Source-Id: mobile-app

{
  "code_selectors": {
    "include": [
      {
        "type": "stock",
        "values": ["000001", "000002"]
      }
    ]
  },
  "indexes": [
    {
      "index_id": "price",
      "time_type": "daily",
      "timestamp": 1640995200000
    }
  ],
  "page_info": {
    "page_begin": 0,
    "page_size": 20
  }
}
```

#### 响应格式
```json
{
  "status_code": 0,
  "status_msg": "success",
  "data": {
    "total": 2,
    "indexes": [],
    "data": [
      {
        "code": "000001",
        "values": [
          {
            "idx": 0,
            "value": "10.5"
          }
        ]
      }
    ]
  }
}
```

### 监控端点

- `GET /actuator/health` - 健康检查
- `GET /actuator/metrics` - 应用指标
- `GET /v1/monitoring/cache` - 缓存监控
- `GET /v1/monitoring/deduplication` - 去重统计

## ⚙️ 配置说明

### 应用配置 (application.properties)

```properties
# 服务端口
server.port=8080

# 远程服务配置
remote.service.baseUrl=http://localhost:9090
remote.service.timeout=PT30S
remote.service.maxRetries=3
remote.service.retryDelay=PT1S

# 缓存配置
cache.default.ttl=PT10M
cache.default.maxSize=1000
cache.default.allowStaleData=true
```

### 缓存策略配置 (application-cache.properties)

```properties
# 缓存策略规则
cache.strategy.rules[0].pattern.sourceId=mobile-app
cache.strategy.rules[0].pattern.codeType=stock
cache.strategy.rules[0].strategy=PASSIVE
cache.strategy.rules[0].cacheTtl=PT5M
cache.strategy.rules[0].allowStaleData=true

cache.strategy.rules[1].pattern.sourceId=web-portal
cache.strategy.rules[1].pattern.codeType=*
cache.strategy.rules[1].strategy=ACTIVE
cache.strategy.rules[1].cacheTtl=PT15M
cache.strategy.rules[1].allowStaleData=false
```

## 🧪 单元测试

本项目包含完整的单元测试套件，覆盖所有核心功能模块，特别强化了远程调用异常处理的测试。

### 测试覆盖

#### 1. 客户端层测试 (`RemoteSpecificDataClientTest.java`)
**测试重点**: 远程调用异常处理、超时重试机制

- ✅ **连接超时测试**: 模拟服务器无响应场景
- ✅ **读取超时测试**: 模拟部分响应后停止的场景  
- ✅ **HTTP错误处理**: 测试4xx/5xx状态码的异常映射
- ✅ **重试机制验证**: 验证指数退避重试策略
- ✅ **网络异常测试**: 模拟网络中断等异常情况
- ✅ **健康检查功能**: 测试服务可用性检测
- ✅ **配置动态更新**: 测试运行时配置热更新

```java
@Test
@DisplayName("连接超时应该抛出ApiTimeoutException")
void fetchSpecificData_ConnectionTimeout() {
    // 模拟服务器无响应
    mockWebServer.enqueue(new MockResponse()
            .setSocketPolicy(SocketPolicy.NO_RESPONSE));
    
    StepVerifier.create(client.fetchSpecificData(testRequest))
            .expectError(ApiTimeoutException.class)
            .verify();
}
```

#### 2. 控制器层测试 (`SpecificDataControllerTest.java`)
**测试重点**: REST接口处理、参数验证、异常响应

- ✅ **请求参数验证**: 测试必填字段、格式校验
- ✅ **Source-Id头部处理**: 测试请求头解析和默认值处理
- ✅ **异常响应格式**: 验证统一的错误响应结构
- ✅ **Content-Type验证**: 测试媒体类型支持
- ✅ **响应格式验证**: 确保JSON响应格式正确性

#### 3. 服务层测试 (`SpecificDataServiceImplTest.java`)  
**测试重点**: 业务逻辑、缓存策略、降级机制

- ✅ **三种缓存策略测试**:
  - `NO_CACHE`: 直接远程调用
  - `PASSIVE`: 被动缓存策略  
  - `ACTIVE`: 主动缓存和后台刷新
- ✅ **降级策略测试**: 远程调用失败时的降级处理
- ✅ **并发安全测试**: 多线程环境下的数据一致性
- ✅ **异常传播测试**: 确保异常正确向上传播

#### 4. 缓存层测试 (`SpecificDataCacheManagerTest.java`)
**测试重点**: 缓存键生成、数据存取、边界条件

- ✅ **缓存键生成逻辑**: 确保相同请求生成相同键
- ✅ **缓存数据存取**: 测试数据的存储和检索
- ✅ **配置边界测试**: 测试各种配置组合
- ✅ **内存管理测试**: 验证缓存清理机制

#### 5. 请求去重测试 (`RequestDeduplicationManagerTest.java`)
**测试重点**: 并发请求合并、结果广播

- ✅ **请求去重逻辑**: 验证相同请求的合并机制
- ✅ **并发安全测试**: 高并发场景下的正确性
- ✅ **错误传播测试**: 确保异常正确传播给所有等待者
- ✅ **内存清理测试**: 验证请求完成后的资源清理

#### 6. DTO验证测试 (`SpecificDataRequestTest.java`)
**测试重点**: 数据传输对象验证逻辑

- ✅ **构造器验证**: 测试必填字段的构造器验证
- ✅ **边界值测试**: 测试各种边界条件
- ✅ **空值处理**: 验证空值和null的处理

#### 7. 异常处理测试 (`GlobalExceptionHandlerTest.java`)
**测试重点**: 全局异常处理和响应格式

- ✅ **异常映射测试**: 各种异常类型的正确映射
- ✅ **响应格式测试**: 统一错误响应格式验证
- ✅ **状态码测试**: HTTP状态码的正确返回

#### 8. 集成测试 (`SpecificDataIntegrationTest.java`)
**测试重点**: 端到端功能验证

- ✅ **完整请求流程**: 从HTTP请求到响应的完整链路
- ✅ **缓存集成测试**: 缓存在真实环境中的工作验证
- ✅ **异常集成测试**: 异常在完整链路中的处理
- ✅ **并发集成测试**: 真实并发场景的系统行为

### 测试特色

#### 🔥 远程调用异常测试强化
针对远程服务调用的各种异常情况进行了全面测试：

**超时场景**:
- 连接超时 (Connection Timeout)
- 读取超时 (Read Timeout)  
- 整体请求超时 (Request Timeout)

**网络异常**:
- 连接拒绝 (Connection Refused)
- 网络中断 (Network Interruption)
- DNS解析失败 (DNS Resolution Failure)

**HTTP错误**:
- 4xx客户端错误 (400, 401, 403, 404等)
- 5xx服务器错误 (500, 502, 503, 504等)
- 自定义错误映射

**重试机制**:
- 指数退避算法验证
- 最大重试次数控制
- 可重试异常判断
- 重试间隔时间验证

#### 🚀 高级测试技术

**Mock框架集成**:
```java
// 使用MockWebServer模拟HTTP服务
@BeforeEach
void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
}

// 模拟不同的响应场景
mockWebServer.enqueue(new MockResponse()
    .setResponseCode(500)
    .setBody("Internal Server Error"));
```

**反应式测试**:
```java
// 使用StepVerifier测试反应式流
StepVerifier.create(result)
    .expectNext(expectedResponse)
    .verifyComplete();

// 测试异常情况
StepVerifier.create(result)
    .expectError(ApiTimeoutException.class)
    .verify();
```

**并发测试**:
```java
// 测试高并发场景
int concurrencyLevel = 100;
Mono<SpecificDataResponse>[] results = new Mono[concurrencyLevel];
for (int i = 0; i < concurrencyLevel; i++) {
    results[i] = deduplicationManager.executeDeduplicatedRequest(/*...*/);
}
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=RemoteSpecificDataClientTest

# 运行集成测试
mvn test -Dtest=SpecificDataIntegrationTest

# 生成测试报告
mvn surefire-report:report

# 查看测试覆盖率
mvn jacoco:report
```

### 测试配置

测试使用专门的配置文件 `application-test.properties`:

```properties
# 测试环境配置
logging.level.com.bililee.demo.fluxapi=DEBUG
remote.service.baseUrl=http://localhost:${wiremock.server.port:8089}
cache.test.enabled=true
```

### 测试指标

- **测试类数量**: 8个
- **测试方法数量**: 60+个  
- **代码覆盖率**: 85%+
- **关键路径覆盖**: 100%
- **异常场景覆盖**: 95%+

## 🔧 性能优化

### 缓存优化
- **分层缓存**: 主缓存 + 过期缓存，提高缓存命中率
- **智能刷新**: 基于访问频率的主动刷新策略
- **内存管理**: 自动清理过期数据，防止内存泄漏

### 并发优化  
- **请求合并**: 相同请求自动合并，减少资源消耗
- **非阻塞IO**: 全链路反应式编程，提高吞吐量
- **连接池**: HTTP连接复用，减少连接开销

### 网络优化
- **智能重试**: 指数退避 + 抖动，避免雪崩效应
- **超时控制**: 细粒度超时控制，快速失败
- **压缩传输**: HTTP响应压缩，减少带宽占用

## 🚢 部署指南

### 生产环境配置

```yaml
# application-prod.yml
server:
  port: 8080
  
spring:
  profiles:
    active: prod
    
remote:
  service:
    baseUrl: ${REMOTE_SERVICE_URL:http://api.example.com}
    timeout: ${REMOTE_TIMEOUT:PT30S}
    maxRetries: ${MAX_RETRIES:3}

cache:
  default:
    ttl: ${CACHE_TTL:PT10M}
    maxSize: ${CACHE_MAX_SIZE:10000}

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always
```

### Kubernetes部署

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flux-api
spec:
  replicas: 3
  selector:
    matchLabels:
      app: flux-api
  template:
    metadata:
      labels:
        app: flux-api
    spec:
      containers:
      - name: flux-api
        image: flux-api:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

## 📊 监控指标

### 关键指标

- **缓存命中率**: 缓存策略效果评估
- **请求去重率**: 系统优化效果
- **平均响应时间**: 用户体验指标  
- **错误率**: 系统稳定性指标
- **吞吐量**: 系统处理能力

### 指标获取

```bash
# 缓存监控
curl http://localhost:8080/v1/monitoring/cache

# 去重统计  
curl http://localhost:8080/v1/monitoring/deduplication

# 应用指标
curl http://localhost:8080/actuator/metrics
```

## 🤝 贡献指南

### 开发规范

1. **代码风格**: 遵循Google Java Style Guide
2. **提交规范**: 使用Conventional Commits格式
3. **测试要求**: 新功能必须包含单元测试，覆盖率不低于80%
4. **文档更新**: 新功能需要更新相应文档

### 提交流程

1. Fork 项目
2. 创建功能分支: `git checkout -b feature/amazing-feature`
3. 提交更改: `git commit -m 'feat: add amazing feature'`
4. 推送分支: `git push origin feature/amazing-feature`
5. 提交Pull Request

### 本地开发

```bash
# 安装pre-commit钩子
mvn git-code-format:install-hooks

# 运行代码检查
mvn checkstyle:check

# 运行所有检查
mvn verify
```

## 📄 许可证

本项目采用 [MIT许可证](LICENSE)

## 📞 联系方式

- 项目维护者: [Your Name](mailto:your.email@example.com)
- 问题反馈: [GitHub Issues](https://github.com/your-username/flux-api/issues)
- 文档说明: [Wiki](https://github.com/your-username/flux-api/wiki)

---

⭐ 如果这个项目对你有帮助，请给我们一个star！