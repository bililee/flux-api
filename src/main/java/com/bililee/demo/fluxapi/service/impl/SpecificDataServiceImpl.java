package com.bililee.demo.fluxapi.service.impl;

import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.service.SpecificDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 特定数据服务实现类
 */
@Slf4j
@Service
public class SpecificDataServiceImpl implements SpecificDataService {

    @Override
    public Mono<SpecificDataResponse> querySpecificData(SpecificDataRequest request) {
        log.info("查询特定数据，请求参数: {}", request);

        return Mono.fromCallable(() -> processSpecificDataQuery(request))
                .doOnSuccess(response -> log.info("特定数据查询成功，返回结果数量: {}", response.data().total()))
                .doOnError(error -> log.error("特定数据查询失败: {}", error.getMessage(), error));
    }

    /**
     * 处理特定数据查询
     * 这里是模拟实现，实际项目中应该调用Repository或外部API
     */
    private SpecificDataResponse processSpecificDataQuery(SpecificDataRequest request) {
        // 构建索引响应
        List<SpecificDataResponse.IndexResponse> indexes = buildIndexResponses(request.indexes());

        // 构建数据项
        List<SpecificDataResponse.DataItem> dataItems = buildDataItems(request);

        // 构建结果
        SpecificDataResponse.SpecificDataResult result = SpecificDataResponse.SpecificDataResult.builder()
                .indexes(indexes)
                .data(dataItems)
                .total(dataItems.size())
                .build();

        return SpecificDataResponse.success(result);
    }

    /**
     * 构建索引响应
     */
    private List<SpecificDataResponse.IndexResponse> buildIndexResponses(List<SpecificDataRequest.IndexRequest> indexRequests) {
        List<SpecificDataResponse.IndexResponse> indexes = new ArrayList<>();

        for (SpecificDataRequest.IndexRequest indexRequest : indexRequests) {
            String valueType = determineValueType(indexRequest.indexId());
            String timeType = indexRequest.timeType() != null ? indexRequest.timeType() : "SNAPSHOT";
            String timestamp = indexRequest.timestamp() != null ? indexRequest.timestamp().toString() : "0";
            Map<String, Object> attribute = indexRequest.attribute() != null ? indexRequest.attribute() : new HashMap<>();

            SpecificDataResponse.IndexResponse indexResponse = SpecificDataResponse.IndexResponse.builder()
                    .valueType(valueType)
                    .indexId(indexRequest.indexId())
                    .timestamp(timestamp)
                    .timeType(timeType)
                    .attribute(attribute)
                    .build();

            indexes.add(indexResponse);
        }

        return indexes;
    }

    /**
     * 构建数据项
     */
    private List<SpecificDataResponse.DataItem> buildDataItems(SpecificDataRequest request) {
        List<SpecificDataResponse.DataItem> dataItems = new ArrayList<>();

        // 从code_selectors中获取代码列表
        List<String> codes = extractCodes(request.codeSelectors());

        for (String code : codes) {
            List<SpecificDataResponse.ValueItem> values = buildValueItems(code, request.indexes());

            SpecificDataResponse.DataItem dataItem = SpecificDataResponse.DataItem.builder()
                    .code(code)
                    .values(values)
                    .build();

            dataItems.add(dataItem);
        }

        return dataItems;
    }

    /**
     * 从代码选择器中提取代码列表
     */
    private List<String> extractCodes(SpecificDataRequest.CodeSelectors codeSelectors) {
        List<String> codes = new ArrayList<>();
        
        for (SpecificDataRequest.CodeSelector selector : codeSelectors.include()) {
            codes.addAll(selector.values());
        }
        
        return codes;
    }

    /**
     * 构建值项列表
     */
    private List<SpecificDataResponse.ValueItem> buildValueItems(String code, List<SpecificDataRequest.IndexRequest> indexRequests) {
        List<SpecificDataResponse.ValueItem> values = new ArrayList<>();

        for (int i = 0; i < indexRequests.size(); i++) {
            SpecificDataRequest.IndexRequest indexRequest = indexRequests.get(i);
            Object value = getMockValue(code, indexRequest.indexId());

            SpecificDataResponse.ValueItem valueItem = SpecificDataResponse.ValueItem.builder()
                    .idx(i)
                    .value(value)
                    .build();

            values.add(valueItem);
        }

        return values;
    }

    /**
     * 根据索引ID确定值类型
     */
    private String determineValueType(String indexId) {
        return switch (indexId) {
            case "security_name" -> "STRING";
            case "last_price", "open_price", "high_price", "low_price", "volume" -> "BIG_DECIMAL";
            case "change_rate" -> "DOUBLE";
            case "trade_date" -> "DATE";
            case "is_trading" -> "BOOLEAN";
            default -> "STRING";
        };
    }

    /**
     * 获取模拟数据值
     * 实际项目中应该从数据库或外部API获取真实数据
     */
    private Object getMockValue(String code, String indexId) {
        return switch (indexId) {
            case "security_name" -> getMockSecurityName(code);
            case "last_price" -> getMockPrice(code);
            case "open_price" -> getMockPrice(code) * 0.98;
            case "high_price" -> getMockPrice(code) * 1.05;
            case "low_price" -> getMockPrice(code) * 0.95;
            case "volume" -> getMockVolume(code);
            case "change_rate" -> getMockChangeRate();
            case "trade_date" -> "2024-01-15";
            case "is_trading" -> true;
            default -> null;
        };
    }

    /**
     * 获取模拟证券名称
     */
    private String getMockSecurityName(String code) {
        if (code.contains("881169")) {
            return "贵金属";
        } else if (code.contains("000001")) {
            return "平安银行";
        } else if (code.contains("600000")) {
            return "浦发银行";
        } else {
            return "测试证券";
        }
    }

    /**
     * 获取模拟价格
     */
    private Double getMockPrice(String code) {
        if (code.contains("881169")) {
            return null; // 贵金属指数可能没有价格
        } else {
            return 10.50 + (code.hashCode() % 100) * 0.1;
        }
    }

    /**
     * 获取模拟成交量
     */
    private Long getMockVolume(String code) {
        return 1000000L + (code.hashCode() % 1000000);
    }

    /**
     * 获取模拟涨跌幅
     */
    private Double getMockChangeRate() {
        return (Math.random() - 0.5) * 0.2; // -10% 到 10% 的随机涨跌幅
    }
}