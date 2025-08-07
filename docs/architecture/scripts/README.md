# 架构图SVG生成工具

## 🎯 功能简介

这个工具包帮助您自动将Mermaid架构图转换为高质量的SVG文件，适用于文档、演示和项目展示。

## 📋 文件说明

- `generate-svg.sh` - 主要的SVG生成脚本
- `mermaid-cli.json` - Mermaid CLI配置文件
- `README.md` - 本使用说明

## 🚀 快速开始

### 1. 安装依赖

```bash
# 安装 Node.js (如果尚未安装)
# 方法1: 使用 nvm (推荐)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
nvm install node

# 方法2: 直接下载
# 访问 https://nodejs.org/ 下载最新版本

# 安装 Mermaid CLI
npm install -g @mermaid-js/mermaid-cli

# 安装 SVGO (可选，用于优化SVG)
npm install -g svgo
```

### 2. 生成SVG文件

```bash
# 进入脚本目录
cd docs/architecture/scripts

# 赋予执行权限
chmod +x generate-svg.sh

# 生成所有架构图
./generate-svg.sh

# 生成并优化架构图
./generate-svg.sh --optimize

# 查看帮助信息
./generate-svg.sh --help
```

### 3. 输出结果

生成的SVG文件将保存在 `docs/architecture/svg/` 目录下：

```
docs/architecture/svg/
├── deployment-architecture.svg    # 生产部署架构图
├── core-architecture.svg         # 核心组件架构图
└── business-flow.svg             # 业务流程图
```

## ⚙️ 配置说明

### Mermaid配置 (`mermaid-cli.json`)

```json
{
  "theme": "default",           // 主题: default, dark, forest, neutral
  "width": 1920,               // 输出宽度
  "height": 1200,              // 输出高度
  "backgroundColor": "white",   // 背景色
  "mermaid": {
    "theme": "default",
    "flowchart": {
      "useMaxWidth": true,      // 自适应宽度
      "htmlLabels": true,       // 启用HTML标签
      "curve": "basis"          // 连线样式
    }
  }
}
```

### 自定义配置

如需自定义配置，可以：

1. 修改 `mermaid-cli.json` 文件
2. 或创建新的配置文件，并使用 `--config` 参数指定

```bash
./generate-svg.sh --config my-config.json
```

## 📊 支持的图表类型

- **Flowchart** - 流程图 (业务流程图)
- **Graph** - 关系图 (架构图、组件图)
- **Sequence** - 时序图
- **Class** - 类图
- **State** - 状态图
- **ER** - 实体关系图
- **Gantt** - 甘特图
- **Journey** - 用户旅程图

## 🔧 高级用法

### 批量处理

```bash
# 清理输出目录
./generate-svg.sh --clean

# 详细输出模式
./generate-svg.sh --verbose

# 组合使用
./generate-svg.sh --optimize --verbose
```

### Docker使用

如果您偏好Docker环境：

```bash
# 使用官方Mermaid Docker镜像
docker run --rm -v $(pwd):/data minlag/mermaid-cli \
  -i /data/docs/architecture/mermaid/deployment.mmd \
  -o /data/docs/architecture/svg/deployment-architecture.svg
```

### CI/CD集成

在GitHub Actions中自动生成：

```yaml
name: Generate Architecture Diagrams

on: [push, pull_request]

jobs:
  generate-diagrams:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
          
      - name: Install Mermaid CLI
        run: npm install -g @mermaid-js/mermaid-cli
        
      - name: Generate SVG
        run: |
          cd docs/architecture/scripts
          chmod +x generate-svg.sh
          ./generate-svg.sh --optimize
          
      - name: Commit changes
        run: |
          git config --local user.email "action@github.com"
          git config --local user.name "GitHub Action"
          git add docs/architecture/svg/
          git diff --staged --quiet || git commit -m "Auto-generate architecture diagrams"
          git push
```

## 🐛 故障排除

### 常见问题

1. **Permission denied**
   ```bash
   chmod +x generate-svg.sh
   ```

2. **mmdc command not found**
   ```bash
   npm install -g @mermaid-js/mermaid-cli
   ```

3. **Puppeteer Chrome download failed**
   ```bash
   # 手动安装Chromium
   npx puppeteer browsers install chrome
   ```

4. **SVG文件过大**
   - 使用 `--optimize` 参数
   - 简化Mermaid图表内容
   - 调整配置文件中的尺寸设置

### 性能优化

- **并行处理**: 脚本自动并行生成多个图表
- **缓存**: 只重新生成修改过的图表
- **压缩**: 使用SVGO优化文件大小

## 📚 参考资料

- [Mermaid官方文档](https://mermaid-js.github.io/)
- [Mermaid CLI GitHub](https://github.com/mermaid-js/mermaid-cli)
- [SVG优化指南](https://github.com/svg/svgo)

## 🤝 贡献

欢迎提交Issue和Pull Request来改进这个工具包！

### 开发指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 创建Pull Request

---

💡 **提示**: 生成的SVG文件可以直接在Markdown中使用，也可以导入到各种设计工具中进行进一步编辑。