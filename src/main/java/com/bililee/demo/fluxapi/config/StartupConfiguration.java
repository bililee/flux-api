package com.bililee.demo.fluxapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动配置类
 * 用于验证应用启动成功
 */
@Slf4j
@Configuration
public class StartupConfiguration {

    @Bean
    public ApplicationRunner applicationStartupRunner() {
        return args -> {
            log.info("🚀 智能缓存层应用启动成功！");
            log.info("📋 可用端点:");
            log.info("   - 健康检查: GET  http://localhost:8080/monitor/health");
            log.info("   - 数据查询: POST http://localhost:8080/v1/specific_data");
            log.info("   - 缓存统计: GET  http://localhost:8080/monitor/cache/stats");
            log.info("   - 系统指标: GET  http://localhost:8080/monitor/metrics");
            log.info("💡 使用 Source-Id 头部来指定业务来源");
        };
    }
}