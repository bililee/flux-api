#!/bin/bash

# Flux API 架构图SVG生成脚本
# 作者: 项目团队
# 用途: 批量将Mermaid图表转换为SVG格式

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 路径配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MERMAID_DIR="$(dirname "$SCRIPT_DIR")/mermaid"
SVG_DIR="$(dirname "$SCRIPT_DIR")/svg"
CONFIG_FILE="$SCRIPT_DIR/mermaid-cli.json"

# 创建输出目录
mkdir -p "$SVG_DIR"

echo -e "${BLUE}🎨 Flux API 架构图SVG生成器${NC}"
echo -e "${BLUE}================================${NC}"

# 检查依赖
check_dependencies() {
    echo -e "${YELLOW}📋 检查依赖...${NC}"
    
    if ! command -v mmdc &> /dev/null; then
        echo -e "${RED}❌ Mermaid CLI 未安装${NC}"
        echo -e "${YELLOW}请运行: npm install -g @mermaid-js/mermaid-cli${NC}"
        exit 1
    fi
    
    if ! command -v node &> /dev/null; then
        echo -e "${RED}❌ Node.js 未安装${NC}"
        echo -e "${YELLOW}请安装 Node.js 18+${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✅ 依赖检查通过${NC}"
}

# 生成单个SVG文件
generate_svg() {
    local mmd_file="$1"
    local svg_file="$2"
    local description="$3"
    
    echo -e "${YELLOW}🔄 生成 $description...${NC}"
    
    if [ ! -f "$mmd_file" ]; then
        echo -e "${RED}❌ 源文件不存在: $mmd_file${NC}"
        return 1
    fi
    
    # 生成SVG
    if mmdc -i "$mmd_file" -o "$svg_file" -c "$CONFIG_FILE" --quiet; then
        # 检查文件大小
        local file_size=$(du -h "$svg_file" | cut -f1)
        echo -e "${GREEN}✅ $description 生成成功 (${file_size})${NC}"
        echo -e "   📁 输出: $svg_file"
        return 0
    else
        echo -e "${RED}❌ $description 生成失败${NC}"
        return 1
    fi
}

# 优化SVG文件 (可选)
optimize_svg() {
    local svg_file="$1"
    
    if command -v svgo &> /dev/null; then
        echo -e "${YELLOW}🔧 优化 SVG: $(basename "$svg_file")${NC}"
        svgo "$svg_file" --quiet
        echo -e "${GREEN}✅ SVG 优化完成${NC}"
    fi
}

# 生成所有架构图
generate_all() {
    echo -e "${BLUE}📊 开始生成架构图...${NC}"
    
    local success_count=0
    local total_count=0
    
    # 定义要生成的图表
    declare -A diagrams=(
        ["deployment.mmd"]="deployment-architecture.svg|生产部署架构图"
        ["core-components.mmd"]="core-architecture.svg|核心组件架构图"
        ["business-flow.mmd"]="business-flow.svg|业务流程图"
    )
    
    # 遍历生成每个图表
    for mmd_file in "${!diagrams[@]}"; do
        total_count=$((total_count + 1))
        
        IFS='|' read -r svg_file description <<< "${diagrams[$mmd_file]}"
        
        local mmd_path="$MERMAID_DIR/$mmd_file"
        local svg_path="$SVG_DIR/$svg_file"
        
        if generate_svg "$mmd_path" "$svg_path" "$description"; then
            success_count=$((success_count + 1))
            
            # 可选：优化SVG
            if [ "$OPTIMIZE_SVG" = "true" ]; then
                optimize_svg "$svg_path"
            fi
        fi
        
        echo ""
    done
    
    # 生成统计信息
    echo -e "${BLUE}📈 生成统计${NC}"
    echo -e "${BLUE}============${NC}"
    echo -e "✅ 成功: ${GREEN}${success_count}${NC}"
    echo -e "❌ 失败: ${RED}$((total_count - success_count))${NC}"
    echo -e "📊 总计: ${BLUE}${total_count}${NC}"
    
    if [ $success_count -eq $total_count ]; then
        echo -e "${GREEN}🎉 所有架构图生成完成！${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  部分架构图生成失败${NC}"
        return 1
    fi
}

# 显示帮助信息
show_help() {
    cat << EOF
Flux API 架构图SVG生成器

用法:
    $0 [选项]

选项:
    -h, --help          显示帮助信息
    -o, --optimize      优化生成的SVG文件 (需要安装 svgo)
    -c, --clean         清理输出目录
    -v, --verbose       详细输出
    --config FILE       指定配置文件路径
    
示例:
    $0                  # 生成所有架构图
    $0 -o              # 生成并优化架构图
    $0 -c              # 清理输出目录
    
依赖:
    - Node.js 18+
    - @mermaid-js/mermaid-cli
    - svgo (可选，用于优化)

安装依赖:
    npm install -g @mermaid-js/mermaid-cli
    npm install -g svgo  # 可选
EOF
}

# 清理输出目录
clean_output() {
    echo -e "${YELLOW}🧹 清理输出目录...${NC}"
    if [ -d "$SVG_DIR" ]; then
        rm -rf "$SVG_DIR"/*
        echo -e "${GREEN}✅ 清理完成${NC}"
    else
        echo -e "${YELLOW}⚠️  输出目录不存在${NC}"
    fi
}

# 主函数
main() {
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -o|--optimize)
                export OPTIMIZE_SVG="true"
                shift
                ;;
            -c|--clean)
                clean_output
                exit 0
                ;;
            -v|--verbose)
                set -x
                shift
                ;;
            --config)
                CONFIG_FILE="$2"
                shift 2
                ;;
            *)
                echo -e "${RED}❌ 未知选项: $1${NC}"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 执行主流程
    check_dependencies
    generate_all
    
    echo -e "${GREEN}🎯 任务完成！${NC}"
    echo -e "${BLUE}📁 SVG文件位置: $SVG_DIR${NC}"
    echo -e "${BLUE}📋 在README.md中查看使用说明${NC}"
}

# 错误处理
trap 'echo -e "${RED}❌ 脚本执行失败${NC}"; exit 1' ERR

# 执行主函数
main "$@"