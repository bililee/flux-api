package com.bililee.demo.fluxapi.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 特定数据查询响应DTO
 */
@Builder
public record SpecificDataResponse(
        SpecificDataResult data,
        
        @JsonProperty("status_code")
        Integer statusCode,
        
        @JsonProperty("status_msg")
        String statusMsg
) {
    public SpecificDataResponse {
        if (statusCode == null) {
            throw new IllegalArgumentException("status_code不能为空");
        }
        if (statusMsg == null || statusMsg.trim().isEmpty()) {
            throw new IllegalArgumentException("status_msg不能为空");
        }
    }

    /**
     * 特定数据结果
     */
    @Builder
    public record SpecificDataResult(
            List<IndexResponse> indexes,
            List<DataItem> data,
            Integer total
    ) {
        public SpecificDataResult {
            if (indexes == null) {
                throw new IllegalArgumentException("indexes不能为空");
            }
            if (data == null) {
                throw new IllegalArgumentException("data不能为空");
            }
            if (total == null || total < 0) {
                throw new IllegalArgumentException("total必须大于等于0");
            }
        }
    }

    /**
     * 指标响应
     */
    @Builder
    public record IndexResponse(
            @JsonProperty("value_type")
            String valueType,
            
            @JsonProperty("index_id")
            String indexId,
            
            String timestamp,
            
            @JsonProperty("time_type")
            String timeType,
            
            Map<String, Object> attribute
    ) {
        public IndexResponse {
            if (valueType == null || valueType.trim().isEmpty()) {
                throw new IllegalArgumentException("value_type不能为空");
            }
            if (indexId == null || indexId.trim().isEmpty()) {
                throw new IllegalArgumentException("index_id不能为空");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp不能为空");
            }
            if (timeType == null || timeType.trim().isEmpty()) {
                throw new IllegalArgumentException("time_type不能为空");
            }
        }
    }

    /**
     * 数据项
     */
    @Builder
    public record DataItem(
            String code,
            List<ValueItem> values
    ) {
        public DataItem {
            if (code == null || code.trim().isEmpty()) {
                throw new IllegalArgumentException("code不能为空");
            }
            if (values == null) {
                throw new IllegalArgumentException("values不能为空");
            }
        }
    }

    /**
     * 值项
     */
    @Builder
    public record ValueItem(
            Integer idx,
            Object value
    ) {
        public ValueItem {
            if (idx == null || idx < 0) {
                throw new IllegalArgumentException("idx必须大于等于0");
            }
        }
    }

    /**
     * 创建成功响应
     */
    public static SpecificDataResponse success(SpecificDataResult data) {
        return SpecificDataResponse.builder()
                .data(data)
                .statusCode(0)
                .statusMsg("success")
                .build();
    }

    /**
     * 创建错误响应
     */
    public static SpecificDataResponse error(Integer statusCode, String statusMsg) {
        return SpecificDataResponse.builder()
                .data(SpecificDataResult.builder()
                        .indexes(List.of())
                        .data(List.of())
                        .total(0)
                        .build())
                .statusCode(statusCode)
                .statusMsg(statusMsg)
                .build();
    }
}