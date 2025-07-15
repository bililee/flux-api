package com.bililee.demo.fluxapi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class ReactorSchedulerConfig {
    @Bean
    public Scheduler customScheduler() {
        // 线程池大小建议为CPU核心数的2~4倍，或根据业务并发量调整
        int threadCount = Runtime.getRuntime().availableProcessors() * 2;
        return Schedulers.newParallel("custom-scheduler", threadCount);
    }
}
