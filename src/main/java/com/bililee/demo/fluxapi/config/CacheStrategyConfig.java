package com.bililee.demo.fluxapi.config;

import com.bililee.demo.fluxapi.config.source.ConfigSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * 缓存策略配置管理器
 * 支持动态配置热加载，基于Source-Id业务维度
 */
@Slf4j
@Data
@Component
public class CacheStrategyConfig {

    @Autowired
    private ConfigSource configSource;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<CacheRuleConfig> rules = new CopyOnWriteArrayList<>();
    private CacheRuleConfig defaultRule;

    /**
     * 缓存策略枚举
     */
    public enum CacheStrategy {
        /**
         * 主动缓存 - 定时刷新
         */
        ACTIVE,
        
        /**
         * 被动缓存 - 请求时缓存
         */
        PASSIVE,
        
        /**
         * 不缓存 - 直接透传
         */
        NO_CACHE
    }

    /**
     * 缓存规则配置
     */
    @Data
    public static class CacheRuleConfig {
        /**
         * 匹配模式
         */
        private MatchPattern pattern;
        
        /**
         * 缓存策略
         */
        private CacheStrategy strategy;
        
        /**
         * 缓存过期时间
         */
        private Duration cacheTtl = Duration.ofMinutes(10);
        
        /**
         * 主动刷新间隔（仅对ACTIVE策略有效）
         */
        private Duration refreshInterval = Duration.ofMinutes(5);
        
        /**
         * 是否允许返回过期数据
         */
        private boolean allowStaleData = true;
        
        /**
         * 优先级（数字越小优先级越高）
         */
        private int priority = 100;

        /**
         * 匹配模式
         */
        @Data
        public static class MatchPattern {
            /**
             * 股票代码模式（正则表达式）
             */
            private String code = ".*";
            
            /**
             * 指标ID模式（正则表达式）
             */
            private String index = ".*";
            
            /**
             * 业务来源ID模式（正则表达式）
             */
            private String sourceId = ".*";

            // 编译后的正则表达式（用于性能优化）
            private transient Pattern codePattern;
            private transient Pattern indexPattern;
            private transient Pattern sourceIdPattern;

            public Pattern getCodePattern() {
                if (codePattern == null && code != null) {
                    codePattern = Pattern.compile(code);
                }
                return codePattern;
            }

            public Pattern getIndexPattern() {
                if (indexPattern == null && index != null) {
                    indexPattern = Pattern.compile(index);
                }
                return indexPattern;
            }

            public Pattern getSourceIdPattern() {
                if (sourceIdPattern == null && sourceId != null) {
                    sourceIdPattern = Pattern.compile(sourceId);
                }
                return sourceIdPattern;
            }
        }
    }

    @PostConstruct
    public void init() {
        loadDefaultConfig();
        loadDynamicConfigs();
        setupConfigListeners();
        log.info("缓存策略配置管理器初始化完成，加载{}条规则", rules.size());
    }

    /**
     * 根据请求参数获取缓存策略
     */
    public CacheStrategy getCacheStrategy(String code, String indexId, String sourceId) {
        CacheRuleConfig rule = getCacheRuleConfig(code, indexId, sourceId);
        return rule != null ? rule.getStrategy() : CacheStrategy.PASSIVE;
    }

    /**
     * 根据请求参数获取缓存配置
     */
    public CacheRuleConfig getCacheRuleConfig(String code, String indexId, String sourceId) {
        return rules.stream()
                .filter(rule -> matchesRule(rule, code, indexId, sourceId))
                .min((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()))
                .orElse(defaultRule);
    }

    /**
     * 检查是否匹配规则
     */
    private boolean matchesRule(CacheRuleConfig rule, String code, String indexId, String sourceId) {
        if (rule.getPattern() == null) {
            return false;
        }

        CacheRuleConfig.MatchPattern pattern = rule.getPattern();
        
        boolean codeMatches = pattern.getCodePattern() == null || 
                             pattern.getCodePattern().matcher(code != null ? code : "").matches();
        
        boolean indexMatches = pattern.getIndexPattern() == null || 
                              pattern.getIndexPattern().matcher(indexId != null ? indexId : "").matches();
        
        boolean sourceIdMatches = pattern.getSourceIdPattern() == null || 
                                 pattern.getSourceIdPattern().matcher(sourceId != null ? sourceId : "").matches();

        return codeMatches && indexMatches && sourceIdMatches;
    }

    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        defaultRule = new CacheRuleConfig();
        defaultRule.setStrategy(CacheStrategy.PASSIVE);
        defaultRule.setCacheTtl(Duration.ofMinutes(10));
        defaultRule.setRefreshInterval(Duration.ofMinutes(5));
        defaultRule.setAllowStaleData(true);
        defaultRule.setPriority(999);

        CacheRuleConfig.MatchPattern defaultPattern = new CacheRuleConfig.MatchPattern();
        defaultPattern.setCode(".*");
        defaultPattern.setIndex(".*");
        defaultPattern.setSourceId(".*");
        defaultRule.setPattern(defaultPattern);

        // 从配置源获取默认配置并覆盖
        configSource.getConfig("cache.strategy.default")
                .doOnNext(this::updateDefaultConfig)
                .subscribe();
    }

    /**
     * 加载动态配置
     */
    private void loadDynamicConfigs() {
        // 加载所有缓存策略配置
        configSource.getAllConfigs("cache.strategy")
                .doOnNext(configs -> {
                    configs.forEach((key, value) -> {
                        if (!key.equals("cache.strategy.default")) {
                            loadRuleConfig(key, value);
                        }
                    });
                })
                .subscribe();
    }

    /**
     * 设置配置监听器
     */
    private void setupConfigListeners() {
        // 监听默认配置变更
        configSource.addConfigListener("cache.strategy.default", 
                (key, oldValue, newValue) -> updateDefaultConfig(newValue));

        // 监听所有策略配置变更（这里简化实现，实际可能需要更精细的监听）
        configSource.getAllConfigs("cache.strategy")
                .doOnNext(configs -> {
                    configs.keySet().forEach(key -> {
                        if (!key.equals("cache.strategy.default")) {
                            configSource.addConfigListener(key,
                                    (configKey, oldValue, newValue) -> updateRuleConfig(configKey, newValue));
                        }
                    });
                })
                .subscribe();
    }

    /**
     * 更新默认配置
     */
    private void updateDefaultConfig(String configJson) {
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (node.has("strategy")) {
                defaultRule.setStrategy(CacheStrategy.valueOf(node.get("strategy").asText()));
            }
            if (node.has("cache_ttl")) {
                defaultRule.setCacheTtl(Duration.parse("PT" + node.get("cache_ttl").asText()));
            }
            if (node.has("refresh_interval")) {
                defaultRule.setRefreshInterval(Duration.parse("PT" + node.get("refresh_interval").asText()));
            }
            if (node.has("allow_stale_data")) {
                defaultRule.setAllowStaleData(node.get("allow_stale_data").asBoolean());
            }
            log.info("默认缓存策略配置已更新");
        } catch (Exception e) {
            log.error("更新默认配置失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 加载规则配置
     */
    private void loadRuleConfig(String configKey, String configJson) {
        try {
            CacheRuleConfig rule = parseRuleConfig(configJson);
            if (rule != null) {
                // 移除旧的同名规则
                rules.removeIf(r -> configKey.equals(getConfigKeyForRule(r)));
                rules.add(rule);
                log.info("加载缓存规则配置: {}", configKey);
            }
        } catch (Exception e) {
            log.error("加载配置规则失败: {} - {}", configKey, e.getMessage(), e);
        }
    }

    /**
     * 更新规则配置
     */
    private void updateRuleConfig(String configKey, String configJson) {
        try {
            CacheRuleConfig newRule = parseRuleConfig(configJson);
            if (newRule != null) {
                // 移除旧规则
                rules.removeIf(r -> configKey.equals(getConfigKeyForRule(r)));
                // 添加新规则
                rules.add(newRule);
                log.info("缓存规则配置已更新: {}", configKey);
            }
        } catch (Exception e) {
            log.error("更新配置规则失败: {} - {}", configKey, e.getMessage(), e);
        }
    }

    /**
     * 解析规则配置JSON
     */
    private CacheRuleConfig parseRuleConfig(String configJson) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(configJson);
        
        CacheRuleConfig rule = new CacheRuleConfig();
        
        // 解析匹配模式
        if (node.has("pattern")) {
            JsonNode patternNode = node.get("pattern");
            CacheRuleConfig.MatchPattern pattern = new CacheRuleConfig.MatchPattern();
            
            if (patternNode.has("code")) {
                pattern.setCode(patternNode.get("code").asText());
            }
            if (patternNode.has("index")) {
                pattern.setIndex(patternNode.get("index").asText());
            }
            if (patternNode.has("source_id")) {
                pattern.setSourceId(patternNode.get("source_id").asText());
            }
            
            rule.setPattern(pattern);
        }
        
        // 解析策略
        if (node.has("strategy")) {
            rule.setStrategy(CacheStrategy.valueOf(node.get("strategy").asText()));
        }
        
        // 解析TTL
        if (node.has("cache_ttl")) {
            rule.setCacheTtl(Duration.parse("PT" + node.get("cache_ttl").asText()));
        }
        
        // 解析刷新间隔
        if (node.has("refresh_interval")) {
            rule.setRefreshInterval(Duration.parse("PT" + node.get("refresh_interval").asText()));
        }
        
        // 解析是否允许过期数据
        if (node.has("allow_stale_data")) {
            rule.setAllowStaleData(node.get("allow_stale_data").asBoolean());
        }
        
        // 解析优先级
        if (node.has("priority")) {
            rule.setPriority(node.get("priority").asInt());
        }
        
        return rule;
    }

    /**
     * 为规则生成配置键（简化实现）
     */
    private String getConfigKeyForRule(CacheRuleConfig rule) {
        // 这里可以根据规则的特征生成键，实际项目中可能需要更复杂的逻辑
        return "cache.strategy.rule_" + rule.hashCode();
    }
}