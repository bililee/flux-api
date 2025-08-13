#!/bin/bash

# 简化版快速测试脚本 - 避免复杂的并发处理
set -e

API_URL="http://localhost:8085/v1/specific_data"
NUM_REQUESTS=30
PARALLEL_JOBS=5

echo "🚀 简化版快速测试 - 验证多次快速请求修复效果"
echo "📊 测试配置:"
echo "   - API地址: $API_URL"
echo "   - 请求总数: $NUM_REQUESTS"
echo "   - 并行任务: $PARALLEL_JOBS"
echo "   - 目标: 验证无超时错误"
echo ""

# 测试请求体
REQUEST_BODY='{
    "code_selectors": {
        "include": [
            {
                "type": "stock_code",
                "values": [
                    "33:300033",
                    "185:AAPL",
                    "177:HK0988"
                ]
            }
        ]
    },
    "indexes": [
        {
            "index_id": "last_price"
        }
    ],
    "page_info": {
        "page_size": 20,
        "page_begin": 0
    },
    "sort": [
        {
            "idx": 0,
            "type": "DESC"
        }
    ]
}'

# 结果文件
RESULTS_FILE=$(mktemp)

echo "🏃 开始测试..."

# 发送请求的函数
send_batch_requests() {
    local batch_id=$1
    local start_idx=$2
    local end_idx=$3
    
    for ((i=start_idx; i<=end_idx; i++)); do
        local start_time=$(date +%s%3N)
        
        # 发送请求
        local response=$(curl -s -w "\n%{http_code}" \
            -X POST \
            -H "Content-Type: application/json" \
            -H "Source-Id: test_123" \
            -d "$REQUEST_BODY" \
            --max-time 15 \
            "$API_URL" 2>/dev/null || echo -e "\nERROR")
        
        local end_time=$(date +%s%3N)
        local duration=$((end_time - start_time))
        local http_code=$(echo "$response" | tail -n1)
        local response_body=$(echo "$response" | head -n -1)
        
        # 分析结果
        local status="unknown"
        local success="false"
        
        if [ "$http_code" = "200" ]; then
            if echo "$response_body" | grep -q '"statusCode":0'; then
                status="success"
                success="true"
            elif echo "$response_body" | grep -q 'timeout\|Timeout\|TIMEOUT'; then
                status="timeout_error"
            else
                status="business_error"
            fi
        elif [ "$http_code" = "ERROR" ]; then
            status="curl_error"
        else
            status="http_${http_code}"
        fi
        
        # 写入结果
        echo "$i,$duration,$status,$success" >> "$RESULTS_FILE"
        
        # 显示进度
        printf "\r📈 批次 %d: 完成请求 %d/%d" $batch_id $i $end_idx
    done
}

# 计算每批次的请求数
batch_size=$((NUM_REQUESTS / PARALLEL_JOBS))
remainder=$((NUM_REQUESTS % PARALLEL_JOBS))

# 启动并行批次
pids=()
current_start=1

for ((batch=1; batch<=PARALLEL_JOBS; batch++)); do
    current_batch_size=$batch_size
    if [ $batch -le $remainder ]; then
        ((current_batch_size++))
    fi
    
    current_end=$((current_start + current_batch_size - 1))
    
    send_batch_requests $batch $current_start $current_end &
    pids+=($!)
    
    current_start=$((current_end + 1))
done

# 等待所有批次完成
for pid in "${pids[@]}"; do
    wait $pid
done

echo ""
echo ""
echo "✅ 测试完成!"

# 统计结果
success_count=0
error_count=0
timeout_count=0
total_duration=0

while IFS=',' read -r req_id duration status success; do
    total_duration=$((total_duration + duration))
    if [ "$success" = "true" ]; then
        ((success_count++))
    else
        ((error_count++))
        if [[ "$status" == *"timeout"* ]]; then
            ((timeout_count++))
        fi
    fi
done < "$RESULTS_FILE"

total_requests=$((success_count + error_count))
success_rate=0
avg_duration=0

if [ $total_requests -gt 0 ]; then
    success_rate=$(awk "BEGIN {printf \"%.2f\", $success_count * 100 / $total_requests}")
    avg_duration=$(awk "BEGIN {printf \"%.2f\", $total_duration / $total_requests}")
fi

echo ""
echo "📊 === 测试结果 ==="
echo "总请求数: $total_requests"
echo "成功请求: $success_count (${success_rate}%)"
echo "失败请求: $error_count"
echo "超时请求: $timeout_count"
echo "平均响应时间: ${avg_duration}ms"

# 响应时间分析
if [ $success_count -gt 0 ]; then
    echo ""
    echo "⏱️  响应时间详情:"
    
    # 提取成功请求的响应时间
    awk -F',' '$4=="true" {print $2}' "$RESULTS_FILE" | sort -n > /tmp/success_times.txt
    
    if [ -s /tmp/success_times.txt ]; then
        min=$(head -n1 /tmp/success_times.txt)
        max=$(tail -n1 /tmp/success_times.txt)
        
        echo "  最小响应时间: ${min}ms"
        echo "  最大响应时间: ${max}ms"
        
        rm -f /tmp/success_times.txt
    fi
fi

# 错误分析
if [ $error_count -gt 0 ]; then
    echo ""
    echo "❌ 错误类型统计:"
    awk -F',' '$4=="false" {print $3}' "$RESULTS_FILE" | sort | uniq -c | sort -nr | while read count error; do
        echo "  - $error: $count 次"
    done
fi

# 修复效果评估
echo ""
echo "🎯 === 修复效果评估 ==="

if [ $timeout_count -eq 0 ]; then
    echo "✅ 优秀! 没有检测到超时错误"
elif [ $timeout_count -lt 3 ]; then
    echo "✅ 良好! 超时错误很少 ($timeout_count 次)"
else
    echo "⚠️  需要进一步优化! 超时错误: $timeout_count 次"
fi

if [ $(echo "$success_rate >= 95" | bc -l) -eq 1 ]; then
    echo "✅ 成功率优秀 (${success_rate}%)"
elif [ $(echo "$success_rate >= 90" | bc -l) -eq 1 ]; then
    echo "✅ 成功率良好 (${success_rate}%)"
else
    echo "⚠️  成功率需要提升 (${success_rate}%)"
fi

if [ $(echo "$avg_duration < 1000" | bc -l) -eq 1 ]; then
    echo "✅ 响应时间优秀 (${avg_duration}ms)"
elif [ $(echo "$avg_duration < 2000" | bc -l) -eq 1 ]; then
    echo "✅ 响应时间良好 (${avg_duration}ms)"
else
    echo "⚠️  响应时间需要优化 (${avg_duration}ms)"
fi

echo ""
echo "📄 详细结果数据: $RESULTS_FILE"
echo "🏁 测试完成!"

# 清理
# rm -f "$RESULTS_FILE"  # 保留结果文件供分析
