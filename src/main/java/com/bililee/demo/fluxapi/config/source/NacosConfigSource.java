package com.bililee.demo.fluxapi.config.source;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Nacos配置源实现
 * 当配置 config.source.type=nacos 时启用
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "config.source.type", havingValue = "nacos", matchIfMissing = true)
public class NacosConfigSource implements ConfigSource {

    // 模拟Nacos配置存储
    private final Map<String, String> configCache = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<ConfigChangeListener>> listeners = new ConcurrentHashMap<>();

    // 初始化一些默认配置
    public NacosConfigSource() {
        initDefaultConfigs();
        startConfigRefreshTask();
    }

    @Override
    public Mono<String> getConfig(String configKey) {
        return Mono.fromCallable(() -> {
            String config = configCache.get(configKey);
            log.debug("从Nacos获取配置: {} = {}", configKey, config);
            return config;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Map<String, String>> getAllConfigs(String group) {
        return Mono.fromCallable(() -> {
            Map<String, String> groupConfigs = new ConcurrentHashMap<>();
            for (Map.Entry<String, String> entry : configCache.entrySet()) {
                if (entry.getKey().startsWith(group + ".")) {
                    groupConfigs.put(entry.getKey(), entry.getValue());
                }
            }
            log.debug("从Nacos获取配置组: {} 共{}项配置", group, groupConfigs.size());
            return groupConfigs;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public void addConfigListener(String configKey, ConfigChangeListener listener) {
        listeners.computeIfAbsent(configKey, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.info("添加配置监听器: {}", configKey);
    }

    @Override
    public void removeConfigListener(String configKey, ConfigChangeListener listener) {
        CopyOnWriteArrayList<ConfigChangeListener> configListeners = listeners.get(configKey);
        if (configListeners != null) {
            configListeners.remove(listener);
            log.info("移除配置监听器: {}", configKey);
        }
    }

    @Override
    public ConfigSourceType getType() {
        return ConfigSourceType.NACOS;
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> {
            // 模拟健康检查
            boolean healthy = configCache.size() > 0;
            log.debug("Nacos健康检查: {}", healthy);
            return healthy;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 初始化默认配置
     */
    private void initDefaultConfigs() {
        // 默认缓存策略配置
        configCache.put("cache.strategy.default", """
            {
              "strategy": "PASSIVE",
              "cache_ttl": "10m",
              "refresh_interval": "5m",
              "allow_stale_data": true
            }
            """);

        // 贵金属实时数据配置
        configCache.put("cache.strategy.precious_metals", """
            {
              "pattern": {
                "code": "48:.*",
                "index": "last_price",
                "source_id": "trading_system"
              },
              "strategy": "ACTIVE",
              "cache_ttl": "5m",
              "refresh_interval": "30s",
              "allow_stale_data": true,
              "priority": 10
            }
            """);

        // 股票基础信息配置
        configCache.put("cache.strategy.stock_basic", """
            {
              "pattern": {
                "code": "[0-9]{6}",
                "index": "security_name",
                "source_id": ".*"
              },
              "strategy": "PASSIVE",
              "cache_ttl": "1h",
              "refresh_interval": "30m",
              "allow_stale_data": true,
              "priority": 20
            }
            """);

        // 实时交易数据配置
        configCache.put("cache.strategy.realtime_trade", """
            {
              "pattern": {
                "code": ".*",
                "index": "realtime_.*",
                "source_id": "realtime_system"
              },
              "strategy": "NO_CACHE",
              "cache_ttl": "0s",
              "refresh_interval": "0s",
              "allow_stale_data": false,
              "priority": 5
            }
            """);

        // 远程服务配置
        configCache.put("remote.service.config", """
            {
              "base_url": "https://remote-datacenter.example.com/api",
              "endpoint": "/v1/specific_data",
              "timeout": "5s",
              "max_retries": 2,
              "retry_delay": "500ms",
              "circuit_breaker": {
                "failure_threshold": 5,
                "recovery_timeout": "30s"
              }
            }
            """);

        // 缓存配置
        configCache.put("cache.memory.config", """
            {
              "max_size": 10000,
              "initial_capacity": 1000,
              "expire_after_write": "30m",
              "expire_after_access": "10m",
              "refresh_after_write": "5m",
              "record_stats": true
            }
            """);

        log.info("Nacos配置源初始化完成，加载{}项默认配置", configCache.size());
    }

    /**
     * 启动配置刷新任务（模拟Nacos的推送机制）
     */
    private void startConfigRefreshTask() {
        // 这里可以实现定期从Nacos拉取配置的逻辑
        // 实际项目中应该使用Nacos SDK的监听机制
        log.info("Nacos配置刷新任务已启动");
    }

    /**
     * 模拟配置变更通知
     */
    public void simulateConfigChange(String configKey, String newValue) {
        String oldValue = configCache.put(configKey, newValue);
        
        CopyOnWriteArrayList<ConfigChangeListener> configListeners = listeners.get(configKey);
        if (configListeners != null) {
            for (ConfigChangeListener listener : configListeners) {
                try {
                    listener.onConfigChange(configKey, oldValue, newValue);
                } catch (Exception e) {
                    log.error("配置变更通知失败: {}", configKey, e);
                }
            }
        }
        
        log.info("配置已更新: {} = {}", configKey, newValue);
    }
}