package com.bililee.demo.fluxapi.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池管理器
 * 为远程调用提供隔离的线程池，防止阻塞主业务线程
 * 支持监控和动态调整
 */
@Slf4j
@Component
public class ThreadPoolManager {

    // 远程调用专用线程池
    private ThreadPoolExecutor remoteCallExecutor;
    
    // Reactor调度器
    private Scheduler remoteCallScheduler;
    
    // 线程池配置
    private final ThreadPoolConfig config = new ThreadPoolConfig();

    @PostConstruct
    public void init() {
        createRemoteCallThreadPool();
        log.info("线程池管理器初始化完成，配置: {}", config);
    }

    @PreDestroy
    public void destroy() {
        shutdownThreadPools();
    }

    /**
     * 在远程调用线程池中执行任务
     */
    public <T> Mono<T> executeOnRemoteCallPool(java.util.function.Supplier<Mono<T>> supplier) {
        return Mono.defer(supplier)
                .subscribeOn(remoteCallScheduler)
                .timeout(java.time.Duration.ofSeconds(3)) // 3秒总超时
                .doOnError(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        log.warn("远程调用线程池执行超时");
                    }
                });
    }

    /**
     * 获取线程池统计信息
     */
    public ThreadPoolStats getThreadPoolStats() {
        if (remoteCallExecutor == null) {
            return new ThreadPoolStats(0, 0, 0, 0, 0);
        }

        return new ThreadPoolStats(
                remoteCallExecutor.getCorePoolSize(),
                remoteCallExecutor.getMaximumPoolSize(),
                remoteCallExecutor.getActiveCount(),
                remoteCallExecutor.getQueue().size(),
                remoteCallExecutor.getCompletedTaskCount()
        );
    }

    /**
     * 创建远程调用线程池
     */
    private void createRemoteCallThreadPool() {
        // 自定义线程工厂
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "remote-call-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        };

        // 创建线程池
        remoteCallExecutor = new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTime(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy() // 饱和策略：调用者执行
        );

        // 创建Reactor调度器
        remoteCallScheduler = Schedulers.fromExecutor(remoteCallExecutor);

        log.info("远程调用线程池创建完成 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}", 
                config.getCorePoolSize(), config.getMaxPoolSize(), config.getQueueCapacity());
    }

    /**
     * 关闭线程池
     */
    private void shutdownThreadPools() {
        log.info("开始关闭线程池...");
        
        if (remoteCallScheduler != null) {
            remoteCallScheduler.dispose();
        }
        
        if (remoteCallExecutor != null) {
            remoteCallExecutor.shutdown();
            try {
                if (!remoteCallExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("线程池未能在10秒内正常关闭，强制关闭");
                    remoteCallExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("等待线程池关闭时被中断", e);
                remoteCallExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("线程池关闭完成");
    }

    /**
     * 线程池配置
     */
    public static class ThreadPoolConfig {
        /**
         * 核心线程数
         */
        private final int corePoolSize = 10;
        
        /**
         * 最大线程数
         */
        private final int maxPoolSize = 50;
        
        /**
         * 线程空闲存活时间（秒）
         */
        private final long keepAliveTime = 60;
        
        /**
         * 队列容量
         */
        private final int queueCapacity = 200;

        public int getCorePoolSize() { return corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public long getKeepAliveTime() { return keepAliveTime; }
        public int getQueueCapacity() { return queueCapacity; }

        @Override
        public String toString() {
            return String.format("ThreadPoolConfig{core=%d, max=%d, keepAlive=%ds, queue=%d}", 
                    corePoolSize, maxPoolSize, keepAliveTime, queueCapacity);
        }
    }

    /**
     * 线程池统计信息
     */
    public record ThreadPoolStats(
            int corePoolSize,
            int maxPoolSize,
            int activeCount,
            int queueSize,
            long completedTaskCount
    ) {
        @Override
        public String toString() {
            return String.format("ThreadPoolStats{core=%d, max=%d, active=%d, queue=%d, completed=%d}", 
                    corePoolSize, maxPoolSize, activeCount, queueSize, completedTaskCount);
        }
    }
}
