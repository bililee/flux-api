# 项目架构图说明

本项目包含4个核心架构图，全面展示系统设计和部署结构。

## 📊 架构图列表

1. **生产部署架构图** (`deployment-architecture.svg`)
   - 完整的生产环境部署拓扑
   - 包含APISIX网关、XXL-Job、普米APM等企业级组件
   - K8s集群、监控、数据库、缓存等完整基础设施

2. **核心组件架构图** (`core-architecture.svg`)
   - 简化的核心组件关系
   - 突出主要技术栈和依赖关系

3. **业务流程图** (`business-flow.svg`)
   - 核心业务处理流程
   - 缓存策略、降级机制、监控埋点

4. **详细系统架构图** (`detailed-system-architecture.svg`)
   - 完整的系统分层架构
   - 所有组件和数据流向

## 🎨 架构图特色

### 技术特色
- **反应式架构**: Spring WebFlux + Project Reactor
- **多层缓存**: 本地缓存 + Redis + 降级缓存
- **API网关**: Apache APISIX统一入口
- **任务调度**: XXL-Job分布式任务
- **APM监控**: 普米指标 + Prometheus + Grafana
- **服务治理**: 配置中心 + 服务发现

### 部署特色
- **容器化部署**: Kubernetes + Docker
- **高可用架构**: 多副本 + 自动伸缩
- **监控完备**: 指标监控 + 链路追踪 + 日志分析
- **安全防护**: WAF + API网关 + 网络隔离

## 📁 文件结构

```
docs/
├── architecture/
│   ├── README.md                    # 本文件
│   ├── mermaid/                     # Mermaid源文件
│   │   ├── deployment.mmd
│   │   ├── core-components.mmd
│   │   ├── business-flow.mmd
│   │   └── detailed-system.mmd
│   ├── svg/                         # SVG输出文件
│   │   ├── deployment-architecture.svg
│   │   ├── core-architecture.svg
│   │   ├── business-flow.svg
│   │   └── detailed-system-architecture.svg
│   └── scripts/
│       ├── generate-svg.sh          # SVG生成脚本
│       └── mermaid-cli.json         # Mermaid配置
```

## 🔄 SVG生成方法

### 方法1: 使用Mermaid CLI (推荐)

```bash
# 安装Mermaid CLI
npm install -g @mermaid-js/mermaid-cli

# 生成单个SVG
mmdc -i docs/architecture/mermaid/deployment.mmd -o docs/architecture/svg/deployment-architecture.svg

# 批量生成所有SVG
cd docs/architecture/scripts && ./generate-svg.sh
```

### 方法2: 使用在线编辑器

1. 访问 [Mermaid Live Editor](https://mermaid.live/)
2. 复制Mermaid代码到编辑器
3. 点击"Download SVG"下载

### 方法3: 使用VS Code插件

1. 安装"Mermaid Preview"插件
2. 打开.mmd文件
3. 右键选择"Export to SVG"

## ⚙️ 配置文件

Mermaid CLI配置 (`mermaid-cli.json`):
```json
{
  "theme": "default",
  "width": 1920,
  "height": 1080,
  "backgroundColor": "white",
  "configFile": "config.json"
}
```

## 📋 使用说明

1. **开发团队**: 参考架构图理解系统设计和代码结构
2. **运维团队**: 基于部署架构图进行环境规划和监控配置  
3. **产品团队**: 通过业务流程图了解系统能力和用户体验
4. **架构师**: 使用详细架构图进行技术决策和扩展规划

## 🔄 更新维护

- 架构图应与代码同步更新
- 重大架构变更需要同步更新文档
- 建议每个迭代检查架构图的准确性