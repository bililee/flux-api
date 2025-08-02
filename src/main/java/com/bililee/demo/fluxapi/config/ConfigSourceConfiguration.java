package com.bililee.demo.fluxapi.config;

import com.bililee.demo.fluxapi.config.source.ConfigSource;
import com.bililee.demo.fluxapi.config.source.NacosConfigSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置源配置类
 * 确保至少有一个ConfigSource Bean被创建
 */
@Slf4j
@Configuration
public class ConfigSourceConfiguration {

    /**
     * 默认配置源Bean
     * 当没有其他ConfigSource实现时使用
     */
    @Bean
    @ConditionalOnMissingBean(ConfigSource.class)
    public ConfigSource defaultConfigSource() {
        log.info("创建默认配置源（Nacos模拟实现）");
        return new NacosConfigSource();
    }
}