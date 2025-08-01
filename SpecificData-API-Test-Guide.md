# SpecificData API 测试指南

## 接口概述

**接口地址**: `POST /v1/specific_data`

**功能描述**: 根据指定的代码选择器、指标和分页信息查询特定数据

## 请求格式

### Content-Type
```
application/json
```

### 请求体示例
```json
{
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
  },
  "sort": [
    {
      "idx": 0,
      "type": "DESC"
    }
  ]
}
```

## 响应格式

### 成功响应示例
```json
{
  "data": {
    "indexes": [
      {
        "value_type": "STRING",
        "index_id": "security_name",
        "timestamp": "0",
        "time_type": "SNAPSHOT",
        "attribute": {}
      },
      {
        "value_type": "BIG_DECIMAL",
        "index_id": "last_price",
        "timestamp": "0",
        "time_type": "DAY_1",
        "attribute": {}
      }
    ],
    "data": [
      {
        "code": "48:881169",
        "values": [
          {
            "idx": 0,
            "value": "贵金属"
          },
          {
            "idx": 1,
            "value": null
          }
        ]
      }
    ],
    "total": 1
  },
  "status_code": 0,
  "status_msg": "success"
}
```

## 测试用例

### 1. 基本查询测试

使用curl命令测试：
```bash
curl -X POST http://localhost:8080/v1/specific_data \
  -H "Content-Type: application/json" \
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
    },
    "sort": [
      {
        "idx": 0,
        "type": "DESC"
      }
    ]
  }'
```

### 2. 多股票代码查询测试

```bash
curl -X POST http://localhost:8080/v1/specific_data \
  -H "Content-Type: application/json" \
  -d '{
    "code_selectors": {
      "include": [
        {
          "type": "stock_code",
          "values": ["48:881169", "000001", "600000"]
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
      },
      {
        "index_id": "volume"
      }
    ],
    "page_info": {
      "page_begin": 0,
      "page_size": 10
    },
    "sort": [
      {
        "idx": 1,
        "type": "ASC"
      }
    ]
  }'
```

### 3. 分页查询测试

```bash
curl -X POST http://localhost:8080/v1/specific_data \
  -H "Content-Type: application/json" \
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
      }
    ],
    "page_info": {
      "page_begin": 1,
      "page_size": 5
    }
  }'
```

### 4. 错误请求测试

#### 缺少必填字段
```bash
curl -X POST http://localhost:8080/v1/specific_data \
  -H "Content-Type: application/json" \
  -d '{
    "indexes": [
      {
        "index_id": "security_name"
      }
    ]
  }'
```

#### 无效的分页参数
```bash
curl -X POST http://localhost:8080/v1/specific_data \
  -H "Content-Type: application/json" \
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
      }
    ],
    "page_info": {
      "page_begin": -1,
      "page_size": 0
    }
  }'
```

## 支持的指标类型

当前模拟实现支持以下指标：

| 指标ID | 中文名称 | 值类型 | 描述 |
|--------|----------|--------|------|
| security_name | 证券名称 | STRING | 证券的名称 |
| last_price | 最新价 | BIG_DECIMAL | 最新成交价格 |
| open_price | 开盘价 | BIG_DECIMAL | 当日开盘价格 |
| high_price | 最高价 | BIG_DECIMAL | 当日最高价格 |
| low_price | 最低价 | BIG_DECIMAL | 当日最低价格 |
| volume | 成交量 | BIG_DECIMAL | 成交量 |
| change_rate | 涨跌幅 | DOUBLE | 涨跌幅百分比 |
| trade_date | 交易日期 | DATE | 交易日期 |
| is_trading | 是否交易 | BOOLEAN | 是否在交易中 |

## 错误处理

接口会返回以下几种错误：

### 参数验证错误 (400)
```json
{
  "data": {
    "indexes": [],
    "data": [],
    "total": 0
  },
  "status_code": 400,
  "status_msg": "参数验证失败: code_selectors不能为空"
}
```

### 服务器内部错误 (500)
```json
{
  "data": {
    "indexes": [],
    "data": [],
    "total": 0
  },
  "status_code": 500,
  "status_msg": "处理请求时发生内部错误: ..."
}
```

## 使用Postman测试

1. 创建新的POST请求
2. URL: `http://localhost:8080/v1/specific_data`
3. Headers: 添加 `Content-Type: application/json`
4. Body: 选择raw格式，粘贴上述JSON请求体
5. 发送请求查看响应

## 注意事项

1. 当前实现为模拟数据，实际部署时需要连接真实的数据源
2. 贵金属指数(48:881169)的价格字段会返回null，这是预期行为
3. 所有请求和响应都会记录在应用日志中
4. 全局异常处理器会捕获并统一处理各种异常情况