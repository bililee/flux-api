package com.bililee.demo.fluxapi.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import com.bililee.demo.fluxapi.response.ApiStatus;

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
                .statusCode(ApiStatus.SUCCESS_CODE)
                .statusMsg(ApiStatus.SUCCESS_MSG)
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

    /**
     * 创建内部服务器错误响应
     * 
     * @return 包含500状态码和对应错误消息的响应对象
     */
    public static SpecificDataResponse internalServerError() {
        return error(ApiStatus.INTERNAL_SERVER_ERROR_CODE, ApiStatus.INTERNAL_SERVER_ERROR_MSG);
    }

    /**
     * 创建服务不可用响应
     * 
     * @return 包含503状态码和对应错误消息的响应对象
     */
    public static SpecificDataResponse serviceUnavailable() {
        return error(ApiStatus.SERVICE_UNAVAILABLE_CODE, ApiStatus.SERVICE_UNAVAILABLE_MSG);
    }

    /**
     * 创建远程服务错误响应
     * 
     * @return 包含1002状态码和对应错误消息的响应对象
     */
    public static SpecificDataResponse remoteServiceError() {
        return error(ApiStatus.REMOTE_SERVICE_ERROR_CODE, ApiStatus.REMOTE_SERVICE_ERROR_MSG);
    }

    /**
     * 创建超时错误响应
     * 
     * @return 包含1004状态码和对应错误消息的响应对象
     */
    public static SpecificDataResponse timeoutError() {
        return error(ApiStatus.TIMEOUT_ERROR_CODE, ApiStatus.TIMEOUT_ERROR_MSG);
    }

    /**
     * 创建空数据的成功响应
     * 
     * @return 包含成功状态码和空数据的响应对象
     */
    public static SpecificDataResponse success() {
        return success(SpecificDataResult.builder()
                .indexes(List.of())
                .data(List.of())
                .total(0)
                .build());
    }
}