package com.bililee.demo.fluxapi.config.source;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 配置源抽象接口
 * 支持不同配置源的扩展（Nacos、MySQL、Apollo等）
 */
public interface ConfigSource {

    /**
     * 获取配置数据
     *
     * @param configKey 配置键
     * @return 配置数据
     */
    Mono<String> getConfig(String configKey);

    /**
     * 获取所有配置
     *
     * @param group 配置组
     * @return 所有配置数据
     */
    Mono<Map<String, String>> getAllConfigs(String group);

    /**
     * 监听配置变更
     *
     * @param configKey 配置键
     * @param listener 变更监听器
     */
    void addConfigListener(String configKey, ConfigChangeListener listener);

    /**
     * 移除配置监听器
     *
     * @param configKey 配置键
     * @param listener 变更监听器
     */
    void removeConfigListener(String configKey, ConfigChangeListener listener);

    /**
     * 配置源类型
     */
    ConfigSourceType getType();

    /**
     * 健康检查
     */
    Mono<Boolean> healthCheck();

    /**
     * 配置变更监听器
     */
    @FunctionalInterface
    interface ConfigChangeListener {
        void onConfigChange(String configKey, String oldValue, String newValue);
    }

    /**
     * 配置源类型枚举
     */
    enum ConfigSourceType {
        NACOS("nacos"),
        MYSQL("mysql"), 
        APOLLO("apollo"),
        LOCAL_FILE("local_file");

        private final String name;

        ConfigSourceType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}