package com.bililee.demo.fluxapi.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SpecificDataRequest DTO 单元测试
 * 测试请求DTO的验证逻辑，包括所有嵌套record的参数验证
 */
@DisplayName("特定数据请求DTO测试")
class SpecificDataRequestTest {

    // ===================== SpecificDataRequest 主类测试 =====================

    @Nested
    @DisplayName("SpecificDataRequest主类验证测试")
    class SpecificDataRequestValidationTest {

        @Test
        @DisplayName("有效的完整请求应该创建成功")
        void createValidRequest_Success() {
            // Given
            var codeSelectors = createValidCodeSelectors();
            var indexes = createValidIndexes();
            var pageInfo = createValidPageInfo();
            var sort = createValidSort();

            // When
            var request = SpecificDataRequest.builder()
                    .codeSelectors(codeSelectors)
                    .indexes(indexes)
                    .pageInfo(pageInfo)
                    .sort(sort)
                    .build();

            // Then
            assertThat(request.codeSelectors()).isEqualTo(codeSelectors);
            assertThat(request.indexes()).isEqualTo(indexes);
            assertThat(request.pageInfo()).isEqualTo(pageInfo);
            assertThat(request.sort()).isEqualTo(sort);
        }

        @Test
        @DisplayName("codeSelectors为null应该抛出IllegalArgumentException")
        void createRequest_NullCodeSelectors_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.builder()
                    .codeSelectors(null)
                    .indexes(createValidIndexes())
                    .pageInfo(createValidPageInfo())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("code_selectors不能为空");
        }

        @Test
        @DisplayName("indexes为null应该抛出IllegalArgumentException")
        void createRequest_NullIndexes_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.builder()
                    .codeSelectors(createValidCodeSelectors())
                    .indexes(null)
                    .pageInfo(createValidPageInfo())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("indexes不能为空");
        }

        @Test
        @DisplayName("indexes为空列表应该抛出IllegalArgumentException")
        void createRequest_EmptyIndexes_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.builder()
                    .codeSelectors(createValidCodeSelectors())
                    .indexes(Collections.emptyList())
                    .pageInfo(createValidPageInfo())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("indexes不能为空");
        }

        @Test
        @DisplayName("pageInfo为null应该抛出IllegalArgumentException")
        void createRequest_NullPageInfo_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.builder()
                    .codeSelectors(createValidCodeSelectors())
                    .indexes(createValidIndexes())
                    .pageInfo(null)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page_info不能为空");
        }

        @Test
        @DisplayName("sort为null应该允许创建")
        void createRequest_NullSort_Success() {
            var request = SpecificDataRequest.builder()
                    .codeSelectors(createValidCodeSelectors())
                    .indexes(createValidIndexes())
                    .pageInfo(createValidPageInfo())
                    .sort(null)
                    .build();

            assertThat(request.sort()).isNull();
        }
    }

    // ===================== CodeSelectors 嵌套类测试 =====================

    @Nested
    @DisplayName("CodeSelectors验证测试")
    class CodeSelectorsValidationTest {

        @Test
        @DisplayName("有效的CodeSelectors应该创建成功")
        void createValidCodeSelectors_Success() {
            // Given
            var include = List.of(createValidCodeSelector());

            // When
            var codeSelectors = SpecificDataRequest.CodeSelectors.builder()
                    .include(include)
                    .build();

            // Then
            assertThat(codeSelectors.include()).isEqualTo(include);
            assertThat(codeSelectors.include()).hasSize(1);
        }

        @Test
        @DisplayName("include为null应该抛出IllegalArgumentException")
        void createCodeSelectors_NullInclude_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.CodeSelectors.builder()
                    .include(null)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("include不能为空");
        }

        @Test
        @DisplayName("include为空列表应该抛出IllegalArgumentException")
        void createCodeSelectors_EmptyInclude_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.CodeSelectors.builder()
                    .include(Collections.emptyList())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("include不能为空");
        }

        @Test
        @DisplayName("include包含多个CodeSelector应该创建成功")
        void createCodeSelectors_MultipleSelectors_Success() {
            var include = List.of(
                    SpecificDataRequest.CodeSelector.builder()
                            .type("stock")
                            .values(List.of("000001", "000002"))
                            .build(),
                    SpecificDataRequest.CodeSelector.builder()
                            .type("bond")
                            .values(List.of("123456"))
                            .build()
            );

            var codeSelectors = SpecificDataRequest.CodeSelectors.builder()
                    .include(include)
                    .build();

            assertThat(codeSelectors.include()).hasSize(2);
        }
    }

    // ===================== CodeSelector 嵌套类测试 =====================

    @Nested
    @DisplayName("CodeSelector验证测试")
    class CodeSelectorValidationTest {

        @Test
        @DisplayName("有效的CodeSelector应该创建成功")
        void createValidCodeSelector_Success() {
            // Given
            String type = "stock";
            List<String> values = List.of("000001", "000002", "000003");

            // When
            var codeSelector = SpecificDataRequest.CodeSelector.builder()
                    .type(type)
                    .values(values)
                    .build();

            // Then
            assertThat(codeSelector.type()).isEqualTo(type);
            assertThat(codeSelector.values()).isEqualTo(values);
            assertThat(codeSelector.values()).hasSize(3);
        }

        @Test
        @DisplayName("type为null应该抛出IllegalArgumentException")
        void createCodeSelector_NullType_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.CodeSelector.builder()
                    .type(null)
                    .values(List.of("000001"))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type不能为空");
        }

        @Test
        @DisplayName("type为空字符串应该抛出IllegalArgumentException")
        void createCodeSelector_EmptyType_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.CodeSelector.builder()
                    .type("")
                    .values(List.of("000001"))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type不能为空");
        }

        @Test
        @DisplayName("type为只包含空格的字符串应该抛出IllegalArgumentException")
        void createCodeSelector_BlankType_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.CodeSelector.builder()
                    .type("   ")
                    .values(List.of("000001"))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type不能为空");
        }

        @Test
        @DisplayName("values为null应该抛出IllegalArgumentException")
        void createCodeSelector_NullValues_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.CodeSelector.builder()
                    .type("stock")
                    .values(null)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("values不能为空");
        }

        @Test
        @DisplayName("values为空列表应该抛出IllegalArgumentException")
        void createCodeSelector_EmptyValues_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.CodeSelector.builder()
                    .type("stock")
                    .values(Collections.emptyList())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("values不能为空");
        }

        @Test
        @DisplayName("单个value应该创建成功")
        void createCodeSelector_SingleValue_Success() {
            var codeSelector = SpecificDataRequest.CodeSelector.builder()
                    .type("stock")
                    .values(List.of("000001"))
                    .build();

            assertThat(codeSelector.values()).hasSize(1);
            assertThat(codeSelector.values().get(0)).isEqualTo("000001");
        }
    }

    // ===================== IndexRequest 嵌套类测试 =====================

    @Nested
    @DisplayName("IndexRequest验证测试")
    class IndexRequestValidationTest {

        @Test
        @DisplayName("有效的IndexRequest应该创建成功")
        void createValidIndexRequest_Success() {
            // Given
            String indexId = "price";
            String timeType = "daily";
            Long timestamp = System.currentTimeMillis();
            Map<String, Object> attribute = Map.of("market", "SZ", "currency", "CNY");

            // When
            var indexRequest = SpecificDataRequest.IndexRequest.builder()
                    .indexId(indexId)
                    .timeType(timeType)
                    .timestamp(timestamp)
                    .attribute(attribute)
                    .build();

            // Then
            assertThat(indexRequest.indexId()).isEqualTo(indexId);
            assertThat(indexRequest.timeType()).isEqualTo(timeType);
            assertThat(indexRequest.timestamp()).isEqualTo(timestamp);
            assertThat(indexRequest.attribute()).isEqualTo(attribute);
        }

        @Test
        @DisplayName("indexId为null应该抛出IllegalArgumentException")
        void createIndexRequest_NullIndexId_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.IndexRequest.builder()
                    .indexId(null)
                    .timeType("daily")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("index_id不能为空");
        }

        @Test
        @DisplayName("indexId为空字符串应该抛出IllegalArgumentException")
        void createIndexRequest_EmptyIndexId_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.IndexRequest.builder()
                    .indexId("")
                    .timeType("daily")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("index_id不能为空");
        }

        @Test
        @DisplayName("indexId为只包含空格的字符串应该抛出IllegalArgumentException")
        void createIndexRequest_BlankIndexId_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.IndexRequest.builder()
                    .indexId("   ")
                    .timeType("daily")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("index_id不能为空");
        }

        @Test
        @DisplayName("timeType为null应该允许创建")
        void createIndexRequest_NullTimeType_Success() {
            var indexRequest = SpecificDataRequest.IndexRequest.builder()
                    .indexId("price")
                    .timeType(null)
                    .build();

            assertThat(indexRequest.timeType()).isNull();
        }

        @Test
        @DisplayName("timestamp为null应该允许创建")
        void createIndexRequest_NullTimestamp_Success() {
            var indexRequest = SpecificDataRequest.IndexRequest.builder()
                    .indexId("price")
                    .timestamp(null)
                    .build();

            assertThat(indexRequest.timestamp()).isNull();
        }

        @Test
        @DisplayName("attribute为null应该允许创建")
        void createIndexRequest_NullAttribute_Success() {
            var indexRequest = SpecificDataRequest.IndexRequest.builder()
                    .indexId("price")
                    .attribute(null)
                    .build();

            assertThat(indexRequest.attribute()).isNull();
        }

        @Test
        @DisplayName("attribute为空Map应该允许创建")
        void createIndexRequest_EmptyAttribute_Success() {
            var indexRequest = SpecificDataRequest.IndexRequest.builder()
                    .indexId("price")
                    .attribute(Collections.emptyMap())
                    .build();

            assertThat(indexRequest.attribute()).isEmpty();
        }
    }

    // ===================== PageInfo 嵌套类测试 =====================

    @Nested
    @DisplayName("PageInfo验证测试")
    class PageInfoValidationTest {

        @Test
        @DisplayName("有效的PageInfo应该创建成功")
        void createValidPageInfo_Success() {
            // Given
            Integer pageBegin = 0;
            Integer pageSize = 20;

            // When
            var pageInfo = SpecificDataRequest.PageInfo.builder()
                    .pageBegin(pageBegin)
                    .pageSize(pageSize)
                    .build();

            // Then
            assertThat(pageInfo.pageBegin()).isEqualTo(pageBegin);
            assertThat(pageInfo.pageSize()).isEqualTo(pageSize);
        }

        @Test
        @DisplayName("pageBegin为null应该抛出IllegalArgumentException")
        void createPageInfo_NullPageBegin_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.PageInfo.builder()
                    .pageBegin(null)
                    .pageSize(20)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page_begin必须大于等于0");
        }

        @Test
        @DisplayName("pageBegin为负数应该抛出IllegalArgumentException")
        void createPageInfo_NegativePageBegin_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.PageInfo.builder()
                    .pageBegin(-1)
                    .pageSize(20)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page_begin必须大于等于0");
        }

        @Test
        @DisplayName("pageBegin为0应该创建成功")
        void createPageInfo_ZeroPageBegin_Success() {
            var pageInfo = SpecificDataRequest.PageInfo.builder()
                    .pageBegin(0)
                    .pageSize(20)
                    .build();

            assertThat(pageInfo.pageBegin()).isEqualTo(0);
        }

        @Test
        @DisplayName("pageSize为null应该抛出IllegalArgumentException")
        void createPageInfo_NullPageSize_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.PageInfo.builder()
                    .pageBegin(0)
                    .pageSize(null)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page_size必须大于0");
        }

        @Test
        @DisplayName("pageSize为0应该抛出IllegalArgumentException")
        void createPageInfo_ZeroPageSize_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.PageInfo.builder()
                    .pageBegin(0)
                    .pageSize(0)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page_size必须大于0");
        }

        @Test
        @DisplayName("pageSize为负数应该抛出IllegalArgumentException")
        void createPageInfo_NegativePageSize_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.PageInfo.builder()
                    .pageBegin(0)
                    .pageSize(-1)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page_size必须大于0");
        }

        @Test
        @DisplayName("pageSize为1应该创建成功")
        void createPageInfo_PageSizeOne_Success() {
            var pageInfo = SpecificDataRequest.PageInfo.builder()
                    .pageBegin(0)
                    .pageSize(1)
                    .build();

            assertThat(pageInfo.pageSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("大的pageBegin和pageSize应该创建成功")
        void createPageInfo_LargeValues_Success() {
            var pageInfo = SpecificDataRequest.PageInfo.builder()
                    .pageBegin(1000)
                    .pageSize(100)
                    .build();

            assertThat(pageInfo.pageBegin()).isEqualTo(1000);
            assertThat(pageInfo.pageSize()).isEqualTo(100);
        }
    }

    // ===================== SortInfo 嵌套类测试 =====================

    @Nested
    @DisplayName("SortInfo验证测试")
    class SortInfoValidationTest {

        @Test
        @DisplayName("有效的SortInfo应该创建成功")
        void createValidSortInfo_Success() {
            // Given
            Integer idx = 0;
            String type = "asc";

            // When
            var sortInfo = SpecificDataRequest.SortInfo.builder()
                    .idx(idx)
                    .type(type)
                    .build();

            // Then
            assertThat(sortInfo.idx()).isEqualTo(idx);
            assertThat(sortInfo.type()).isEqualTo(type);
        }

        @Test
        @DisplayName("idx为null应该抛出IllegalArgumentException")
        void createSortInfo_NullIdx_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.SortInfo.builder()
                    .idx(null)
                    .type("asc")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("idx必须大于等于0");
        }

        @Test
        @DisplayName("idx为负数应该抛出IllegalArgumentException")
        void createSortInfo_NegativeIdx_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.SortInfo.builder()
                    .idx(-1)
                    .type("asc")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("idx必须大于等于0");
        }

        @Test
        @DisplayName("idx为0应该创建成功")
        void createSortInfo_ZeroIdx_Success() {
            var sortInfo = SpecificDataRequest.SortInfo.builder()
                    .idx(0)
                    .type("asc")
                    .build();

            assertThat(sortInfo.idx()).isEqualTo(0);
        }

        @Test
        @DisplayName("type为null应该抛出IllegalArgumentException")
        void createSortInfo_NullType_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.SortInfo.builder()
                    .idx(0)
                    .type(null)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type不能为空");
        }

        @Test
        @DisplayName("type为空字符串应该抛出IllegalArgumentException")
        void createSortInfo_EmptyType_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.SortInfo.builder()
                    .idx(0)
                    .type("")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type不能为空");
        }

        @Test
        @DisplayName("type为只包含空格的字符串应该抛出IllegalArgumentException")
        void createSortInfo_BlankType_ThrowsException() {
            assertThatThrownBy(() -> SpecificDataRequest.SortInfo.builder()
                    .idx(0)
                    .type("   ")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type不能为空");
        }

        @Test
        @DisplayName("type为desc应该创建成功")
        void createSortInfo_DescType_Success() {
            var sortInfo = SpecificDataRequest.SortInfo.builder()
                    .idx(1)
                    .type("desc")
                    .build();

            assertThat(sortInfo.type()).isEqualTo("desc");
        }
    }

    // ===================== 辅助方法 =====================

    private SpecificDataRequest.CodeSelectors createValidCodeSelectors() {
        return SpecificDataRequest.CodeSelectors.builder()
                .include(List.of(createValidCodeSelector()))
                .build();
    }

    private SpecificDataRequest.CodeSelector createValidCodeSelector() {
        return SpecificDataRequest.CodeSelector.builder()
                .type("stock")
                .values(List.of("000001", "000002"))
                .build();
    }

    private List<SpecificDataRequest.IndexRequest> createValidIndexes() {
        return List.of(SpecificDataRequest.IndexRequest.builder()
                .indexId("price")
                .timeType("daily")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private SpecificDataRequest.PageInfo createValidPageInfo() {
        return SpecificDataRequest.PageInfo.builder()
                .pageBegin(0)
                .pageSize(20)
                .build();
    }

    private List<SpecificDataRequest.SortInfo> createValidSort() {
        return List.of(SpecificDataRequest.SortInfo.builder()
                .idx(0)
                .type("asc")
                .build());
    }
}