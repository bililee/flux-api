package com.bililee.demo.fluxapi.service.impl;

import com.bililee.demo.fluxapi.cache.SpecificDataCacheManager;
import com.bililee.demo.fluxapi.config.CacheStrategyConfig;
import com.bililee.demo.fluxapi.model.dto.SpecificDataRequest;
import com.bililee.demo.fluxapi.model.dto.SpecificDataResponse;
import com.bililee.demo.fluxapi.response.ApiStatus;
import com.bililee.demo.fluxapi.service.SpecificDataService;
import com.bililee.demo.fluxapi.strategy.cache.CacheStrategyExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;



/**
 * 特定数据服务实现类
 * 
 * <p>使用优化后的策略模式框架，支持多种缓存策略：</p>
 * <ul>
 *   <li>NO_CACHE: 直接透传到远程服务</li>
 *   <li>PASSIVE: 被动缓存，先查缓存后远程调用</li>
 *   <li>ACTIVE: 主动缓存，缓存优先+异步刷新</li>
 * </ul>
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Service
public class SpecificDataServiceImpl implements SpecificDataService {

    @Autowired
    private CacheStrategyConfig cacheStrategyConfig;

    @Autowired
    private SpecificDataCacheManager cacheManager;

    @Autowired
    private CacheStrategyExecutor strategyExecutor;

    @Override
    public Mono<SpecificDataResponse> querySpecificData(SpecificDataRequest request) {
        return querySpecificData(request, null);
    }

    /**
     * 查询特定数据（支持Source-Id）
     * 
     * <p>使用策略模式框架，根据配置的缓存策略自动选择最优的处理方式</p>
     * <p>新增功能：</p>
     * <ul>
     *   <li>根据index_id和time_type分别获取缓存策略</li>
     *   <li>按策略拆分请求并并行执行</li>
     *   <li>支持排序字段优先处理逻辑</li>
     * </ul>
     */
    public Mono<SpecificDataResponse> querySpecificData(SpecificDataRequest request, String sourceId) {

        // 1. 按缓存策略拆分请求
        Map<CacheStrategyConfig.CacheStrategy, List<SpecificDataRequest.IndexRequest>> strategyGroups = 
                splitRequestByStrategy(request, sourceId);

        log.debug("请求拆分结果 - sourceId: {}, 策略组数: {}", sourceId, strategyGroups.size());
        strategyGroups.forEach((strategy, indexes) -> 
                log.debug("策略 {} 包含 {} 个indexes", strategy, indexes.size()));

        // 2. 检查是否需要排序优先处理
        if (needsSortingFirst(request, strategyGroups)) {
            log.debug("检测到排序字段且有多个策略组，执行排序优先逻辑");
            return executeRequestsWithSorting(request, sourceId, strategyGroups);
        } else {
            log.debug("无需排序优先处理，执行并行处理逻辑");
            return executeRequestsParallel(request, sourceId, strategyGroups);
        }
    }

    /**
     * 提取第一个代码（用于策略匹配）
     */
    private String extractFirstCode(SpecificDataRequest request) {
        if (request.codeSelectors() != null && 
            request.codeSelectors().include() != null && 
            !request.codeSelectors().include().isEmpty()) {
            
            var firstSelector = request.codeSelectors().include().get(0);
            if (firstSelector.values() != null && !firstSelector.values().isEmpty()) {
                return firstSelector.values().get(0);
            }
        }
        return "";
    }



    /**
     * 按缓存策略拆分请求
     * 
     * <p>根据每个index的index_id和time_type获取对应的缓存策略，将indexes按策略分组</p>
     */
    private Map<CacheStrategyConfig.CacheStrategy, List<SpecificDataRequest.IndexRequest>> 
            splitRequestByStrategy(SpecificDataRequest request, String sourceId) {
        
        String firstCode = extractFirstCode(request);
        Map<CacheStrategyConfig.CacheStrategy, List<SpecificDataRequest.IndexRequest>> strategyGroups = 
                new HashMap<>();

        for (SpecificDataRequest.IndexRequest index : request.indexes()) {
            // 根据index_id和time_type获取缓存策略
            CacheStrategyConfig.CacheStrategy strategy = cacheStrategyConfig.getCacheStrategy(
                    firstCode, index.indexId(), sourceId);
            
            // 按策略分组
            strategyGroups.computeIfAbsent(strategy, k -> new ArrayList<>()).add(index);
            
            log.debug("Index {} (time_type: {}) -> 策略: {}", 
                    index.indexId(), index.timeType(), strategy);
        }

        return strategyGroups;
    }

    /**
     * 判断是否需要排序优先处理
     * 
     * <p>条件：有排序字段 && 拆分后有多个策略组</p>
     */
    private boolean needsSortingFirst(SpecificDataRequest request, 
            Map<CacheStrategyConfig.CacheStrategy, List<SpecificDataRequest.IndexRequest>> strategyGroups) {
        
        boolean hasSortFields = request.sort() != null && !request.sort().isEmpty();
        boolean hasMultipleGroups = strategyGroups.size() > 1;
        
        log.debug("排序检查 - 有排序字段: {}, 多个策略组: {}", hasSortFields, hasMultipleGroups);
        return hasSortFields && hasMultipleGroups;
    }

    /**
     * 带排序的执行逻辑
     * 
     * <p>先执行排序相关的indexes，再并行执行其他indexes</p>
     */
    private Mono<SpecificDataResponse> executeRequestsWithSorting(
            SpecificDataRequest request, 
            String sourceId,
            Map<CacheStrategyConfig.CacheStrategy, List<SpecificDataRequest.IndexRequest>> strategyGroups) {
        
        // 1. 找到排序相关的indexes
        Set<String> sortRelatedIndexIds = findSortRelatedIndexes(request);
        
        // 2. 将策略组分为排序相关和非排序相关
        Map<CacheStrategyConfig.CacheStrategy, List<SpecificDataRequest.IndexRequest>> sortGroups = new HashMap<>();
        Map<CacheStrategyConfig.CacheStrategy, List<SpecificDataRequest.IndexRequest>> nonSortGroups = new HashMap<>();
        
        strategyGroups.forEach((strategy, indexes) -> {
            List<SpecificDataRequest.IndexRequest> sortIndexes = new ArrayList<>();
            List<SpecificDataRequest.IndexRequest> nonSortIndexes = new ArrayList<>();
            
            for (SpecificDataRequest.IndexRequest index : indexes) {
                if (sortRelatedIndexIds.contains(index.indexId())) {
                    sortIndexes.add(index);
                } else {
                    nonSortIndexes.add(index);
                }
            }
            
            if (!sortIndexes.isEmpty()) {
                sortGroups.put(strategy, sortIndexes);
            }
            if (!nonSortIndexes.isEmpty()) {
                nonSortGroups.put(strategy, nonSortIndexes);
            }
        });

        // 3. 先执行排序相关的请求
        Mono<Map<CacheStrategyConfig.CacheStrategy, SpecificDataResponse>> sortResults = 
                executeStrategyGroups(request, sourceId, sortGroups, "排序相关");
        
        // 4. 排序完成后执行非排序相关的请求
        return sortResults.flatMap(sortResponseMap -> {
            if (nonSortGroups.isEmpty()) {
                // 只有排序请求，直接合并结果
                return Mono.just(mergeResponses(sortResponseMap));
            } else {
                // 并行执行非排序请求，然后合并所有结果
                return executeStrategyGroups(request, sourceId, nonSortGroups, "非排序相关")
                        .map(nonSortResponseMap -> {
                            Map<CacheStrategyConfig.CacheStrategy, SpecificDataResponse> allResults = new HashMap<>();
                            allResults.putAll(sortResponseMap);
                            allResults.putAll(nonSortResponseMap);
                            return mergeResponses(allResults);
                        });
            }
        });
    }

    /**
     * 并行执行逻辑
     * 
     * <p>同时执行所有策略组的请求</p>
     */
    private Mono<SpecificDataResponse> executeRequestsParallel(
            SpecificDataRequest request,
            String sourceId,
            Map<CacheStrategyConfig.CacheStrategy, List<SpecificDataRequest.IndexRequest>> strategyGroups) {
        
        return executeStrategyGroups(request, sourceId, strategyGroups, "并行执行")
                .map(this::mergeResponses);
    }

    /**
     * 执行策略组
     * 
     * <p>为每个策略组创建子请求并并行执行</p>
     */
    private Mono<Map<CacheStrategyConfig.CacheStrategy, SpecificDataResponse>> executeStrategyGroups(
            SpecificDataRequest originalRequest,
            String sourceId,
            Map<CacheStrategyConfig.CacheStrategy, List<SpecificDataRequest.IndexRequest>> strategyGroups,
            String logPrefix) {
        
        if (strategyGroups.isEmpty()) {
            return Mono.just(new HashMap<>());
        }

        log.debug("{} - 开始执行{}个策略组", logPrefix, strategyGroups.size());

        List<Mono<Map.Entry<CacheStrategyConfig.CacheStrategy, SpecificDataResponse>>> monoList = 
                strategyGroups.entrySet().stream()
                .map(entry -> {
                    CacheStrategyConfig.CacheStrategy strategy = entry.getKey();
                    List<SpecificDataRequest.IndexRequest> indexes = entry.getValue();
                    
                    // 创建子请求
                    SpecificDataRequest subRequest = createSubRequest(originalRequest, indexes);
                    String cacheKey = cacheManager.generateCacheKey(subRequest, sourceId);
                    
                    // 获取策略配置（使用第一个index的配置）
                    String firstCode = extractFirstCode(originalRequest);
                    String firstIndexId = indexes.get(0).indexId();
                    CacheStrategyConfig.CacheRuleConfig rule = cacheStrategyConfig.getCacheRuleConfig(
                            firstCode, firstIndexId, sourceId);

                    log.debug("{} - 执行策略: {}, indexes: {}", 
                            logPrefix, strategy, 
                            indexes.stream().map(SpecificDataRequest.IndexRequest::indexId)
                                    .collect(Collectors.joining(",")));

                    // 执行策略
                    return strategyExecutor.execute(
                            strategy,                           // 策略类型
                            subRequest,                        // 子请求对象
                            sourceId,                          // 源ID
                            cacheKey,                          // 缓存键
                            rule,                              // 缓存规则
                            "/v1/specific_data",               // API路径
                            SpecificDataRequest.class,         // 请求类型
                            SpecificDataResponse.class         // 响应类型
                    ).map(response -> (Map.Entry<CacheStrategyConfig.CacheStrategy, SpecificDataResponse>) 
                            new AbstractMap.SimpleEntry<>(strategy, response));
                })
                .collect(Collectors.toList());

        // 并行执行所有子请求（添加超时控制）
        return Flux.mergeSequential(monoList)
                .timeout(Duration.ofSeconds(30)) // 30秒总超时
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .doOnSuccess(results -> log.debug("{} - 完成执行，获得{}个结果", logPrefix, results.size()))
                .doOnError(error -> log.error("{} - 执行失败: {}", logPrefix, error.getMessage()));
    }

    /**
     * 创建子请求
     * 
     * <p>复制原始请求的所有参数，只替换indexes</p>
     */
    private SpecificDataRequest createSubRequest(SpecificDataRequest originalRequest, 
            List<SpecificDataRequest.IndexRequest> indexes) {
        
        return SpecificDataRequest.builder()
                .codeSelectors(originalRequest.codeSelectors())
                .indexes(indexes)
                .pageInfo(originalRequest.pageInfo())
                .sort(originalRequest.sort())
                .build();
    }

    /**
     * 找到排序相关的indexes
     * 
     * <p>根据排序字段的idx找到对应的index_id</p>
     */
    private Set<String> findSortRelatedIndexes(SpecificDataRequest request) {
        if (request.sort() == null || request.sort().isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> sortIndexes = request.sort().stream()
                .map(SpecificDataRequest.SortInfo::idx)
                .collect(Collectors.toSet());

        Set<String> sortRelatedIndexIds = new HashSet<>();
        for (int i = 0; i < request.indexes().size(); i++) {
            if (sortIndexes.contains(i)) {
                sortRelatedIndexIds.add(request.indexes().get(i).indexId());
            }
        }

        log.debug("排序相关的index_ids: {}", sortRelatedIndexIds);
        return sortRelatedIndexIds;
    }

    /**
     * 合并响应结果
     * 
     * <p>将多个子请求的结果合并为一个完整的响应</p>
     */
    private SpecificDataResponse mergeResponses(Map<CacheStrategyConfig.CacheStrategy, SpecificDataResponse> responseMap) {
        if (responseMap.isEmpty()) {
            throw new IllegalArgumentException("没有响应结果需要合并");
        }

        log.debug("开始合并{}个响应结果", responseMap.size());

        // 如果只有一个响应，直接返回
        if (responseMap.size() == 1) {
            SpecificDataResponse singleResponse = responseMap.values().iterator().next();
            log.debug("只有一个响应结果，直接返回");
            return singleResponse;
        }

        // 检查所有响应的状态码
        List<SpecificDataResponse> responses = new ArrayList<>(responseMap.values());
        
        // 如果有任何失败的响应，返回第一个失败响应
        for (SpecificDataResponse response : responses) {
            if (!Objects.equals(ApiStatus.SUCCESS_CODE, response.statusCode())) {
                log.warn("发现失败响应，状态码: {}, 消息: {}", response.statusCode(), response.statusMsg());
                return response;
            }
        }

        // 所有响应都成功，合并数据
        List<SpecificDataResponse.IndexResponse> mergedIndexes = new ArrayList<>();
        List<SpecificDataResponse.DataItem> mergedData = new ArrayList<>();
        int totalCount = 0;

        for (SpecificDataResponse response : responses) {
            if (response.data() != null) {
                // 合并indexes
                if (response.data().indexes() != null) {
                    mergedIndexes.addAll(response.data().indexes());
                }
                
                // 合并data
                if (response.data().data() != null) {
                    mergedData.addAll(response.data().data());
                }
                
                // 取最大total值（避免重复计数，根据业务逻辑调整）
                if (response.data().total() != null) {
                    totalCount = Math.max(totalCount, response.data().total());
                }
            }
        }

        // 构建合并后的响应
        SpecificDataResponse.SpecificDataResult mergedResult = SpecificDataResponse.SpecificDataResult.builder()
                .indexes(mergedIndexes)
                .data(mergedData)
                .total(totalCount)
                .build();

        SpecificDataResponse mergedResponse = SpecificDataResponse.builder()
                .data(mergedResult)
                .statusCode(ApiStatus.SUCCESS_CODE)
                .statusMsg(ApiStatus.SUCCESS_MSG)
                .build();

        log.debug("合并完成，合并了{}个indexes，{}个data items，总计: {}", 
                mergedIndexes.size(), mergedData.size(), totalCount);
        
        return mergedResponse;
    }
}