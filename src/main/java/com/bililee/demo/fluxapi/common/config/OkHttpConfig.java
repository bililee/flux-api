package com.bililee.demo.fluxapi.common.config;

import okhttp3.ConnectionPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpConfig {
    @Bean
    public ConnectionPool connectionPool() {
        // 最大空闲连接数200，连接最大空闲存活时间300秒
        return new ConnectionPool(200, 300, TimeUnit.SECONDS);
    }
}