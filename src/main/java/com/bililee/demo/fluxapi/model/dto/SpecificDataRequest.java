package com.bililee.demo.fluxapi.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 特定数据查询请求DTO
 */
@Builder
public record SpecificDataRequest(
        @JsonProperty("code_selectors")
        CodeSelectors codeSelectors,
        
        List<IndexRequest> indexes,
        
        @JsonProperty("page_info")
        PageInfo pageInfo,
        
        List<SortInfo> sort
) {
    public SpecificDataRequest {
        if (codeSelectors == null) {
            throw new IllegalArgumentException("code_selectors不能为空");
        }
        if (indexes == null || indexes.isEmpty()) {
            throw new IllegalArgumentException("indexes不能为空");
        }
        if (pageInfo == null) {
            throw new IllegalArgumentException("page_info不能为空");
        }
    }

    /**
     * 代码选择器
     */
    @Builder
    public record CodeSelectors(
            List<CodeSelector> include
    ) {
        public CodeSelectors {
            if (include == null || include.isEmpty()) {
                throw new IllegalArgumentException("include不能为空");
            }
        }
    }

    /**
     * 代码选择器项
     */
    @Builder
    public record CodeSelector(
            String type,
            List<String> values
    ) {
        public CodeSelector {
            if (type == null || type.trim().isEmpty()) {
                throw new IllegalArgumentException("type不能为空");
            }
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("values不能为空");
            }
        }
    }

    /**
     * 指标请求
     */
    @Builder
    public record IndexRequest(
            @JsonProperty("index_id")
            String indexId,
            
            @JsonProperty("time_type")
            String timeType,
            
            Long timestamp,
            
            Map<String, Object> attribute
    ) {
        public IndexRequest {
            if (indexId == null || indexId.trim().isEmpty()) {
                throw new IllegalArgumentException("index_id不能为空");
            }
        }
    }

    /**
     * 分页信息
     */
    @Builder
    public record PageInfo(
            @JsonProperty("page_begin")
            Integer pageBegin,
            
            @JsonProperty("page_size")
            Integer pageSize
    ) {
        public PageInfo {
            if (pageBegin == null || pageBegin < 0) {
                throw new IllegalArgumentException("page_begin必须大于等于0");
            }
            if (pageSize == null || pageSize <= 0) {
                throw new IllegalArgumentException("page_size必须大于0");
            }
        }
    }

    /**
     * 排序信息
     */
    @Builder
    public record SortInfo(
            Integer idx,
            String type
    ) {
        public SortInfo {
            if (idx == null || idx < 0) {
                throw new IllegalArgumentException("idx必须大于等于0");
            }
            if (type == null || type.trim().isEmpty()) {
                throw new IllegalArgumentException("type不能为空");
            }
        }
    }
}