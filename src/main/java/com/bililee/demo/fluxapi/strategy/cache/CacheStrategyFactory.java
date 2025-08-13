package com.bililee.demo.fluxapi.strategy.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 缓存策略工厂
 * 
 * <p>负责根据策略类型创建相应的缓存策略实例。
 * 支持自动发现和注册Spring容器中的所有策略实现。</p>
 * 
 * @author bililee
 * @since 1.0.0
 */
@Slf4j
@Component
public class CacheStrategyFactory {
    
    private final List<CacheStrategy<?, ?>> strategies;
    
    @Autowired
    public CacheStrategyFactory(List<CacheStrategy<?, ?>> strategies) {
        this.strategies = strategies;
        log.info("缓存策略工厂初始化完成，发现 {} 个策略: {}", 
                strategies.size(), 
                strategies.stream().map(CacheStrategy::getStrategyName).toList());
    }
    
    /**
     * 根据策略类型获取策略实例
     * 
     * @param strategyType 策略类型
     * @param requestType 请求类型（用于泛型推断）
     * @param responseType 响应类型（用于泛型推断）
     * @return 匹配的策略实例
     * @throws IllegalArgumentException 如果找不到匹配的策略
     */
    @SuppressWarnings("unchecked")
    public <TRequest, TResponse> CacheStrategy<TRequest, TResponse> getStrategy(
            Object strategyType, Class<TRequest> requestType, Class<TResponse> responseType) {
        
        Optional<CacheStrategy<?, ?>> strategy = strategies.stream()
                .filter(s -> s.supports(strategyType))
                .findFirst();
        
        if (strategy.isPresent()) {
            log.debug("找到匹配策略: {} for type: {}", strategy.get().getStrategyName(), strategyType);
            return (CacheStrategy<TRequest, TResponse>) strategy.get();
        }
        
        String availableStrategies = strategies.stream()
                .map(CacheStrategy::getStrategyName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("无");
        
        throw new IllegalArgumentException(
                String.format("未找到支持策略类型 '%s' 的实现。可用策略: %s", strategyType, availableStrategies));
    }
    
    /**
     * 获取所有可用的策略名称
     * 
     * @return 策略名称列表
     */
    public List<String> getAvailableStrategyNames() {
        return strategies.stream()
                .map(CacheStrategy::getStrategyName)
                .toList();
    }
    
    /**
     * 检查是否支持指定的策略类型
     * 
     * @param strategyType 策略类型
     * @return 如果支持则返回true
     */
    public boolean supportsStrategy(Object strategyType) {
        return strategies.stream()
                .anyMatch(s -> s.supports(strategyType));
    }
}
