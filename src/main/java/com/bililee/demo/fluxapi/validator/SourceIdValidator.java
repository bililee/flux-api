package com.bililee.demo.fluxapi.validator;

import com.bililee.demo.fluxapi.config.source.ConfigSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Source-Id 校验器
 * 支持格式校验、白名单校验、动态配置
 */
@Slf4j
@Component
public class SourceIdValidator {

    @Autowired(required = false)
    private ConfigSource configSource;

    /**
     * Source-Id 格式校验正则表达式
     * 允许字母、数字、下划线，长度3-32位
     */
    private static final Pattern SOURCE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");

    /**
     * 默认允许的 Source-Id 白名单
     */
    private Set<String> allowedSourceIds = new HashSet<>();

    /**
     * 是否启用白名单校验
     */
    private boolean enableWhitelist = true;

    /**
     * 是否允许空的 Source-Id
     */
    private boolean allowEmpty = false;

    @PostConstruct
    public void init() {
        try {
            log.info("初始化 Source-Id 校验器...");
            
            // 加载默认白名单
            loadDefaultWhitelist();
            
            // 从配置源加载动态配置
            loadDynamicConfig();
            
            // 设置配置监听器
            setupConfigListener();
            
            log.info("Source-Id 校验器初始化完成，白名单项目数: {}", allowedSourceIds.size());
            
        } catch (Exception e) {
            log.error("Source-Id 校验器初始化失败", e);
        }
    }

    /**
     * 校验 Source-Id
     */
    public ValidationResult validate(String sourceId, String requestUri) {
        try {
            // 1. 检查是否为空
            if (!StringUtils.hasText(sourceId)) {
                if (allowEmpty) {
                    log.debug("允许空的 Source-Id");
                    return ValidationResult.success();
                } else {
                    return ValidationResult.error("缺少必需的 Source-Id 头部");
                }
            }

            // 2. 格式校验
            if (!SOURCE_ID_PATTERN.matcher(sourceId).matches()) {
                return ValidationResult.error("Source-Id 格式不正确，应为3-32位字母数字下划线");
            }

            // 3. 白名单校验
            if (enableWhitelist && !allowedSourceIds.contains(sourceId)) {
                return ValidationResult.error("Source-Id 不在允许的白名单中: " + sourceId);
            }

            return ValidationResult.success();
            
        } catch (Exception e) {
            log.error("Source-Id 校验过程出错: {}", e.getMessage(), e);
            return ValidationResult.error("Source-Id 校验服务异常");
        }
    }

    /**
     * 加载默认白名单
     */
    private void loadDefaultWhitelist() {
        // 默认测试和开发环境的 Source-Id
        allowedSourceIds.add("test_system");
        allowedSourceIds.add("dev_client");
        allowedSourceIds.add("mobile_app");
        allowedSourceIds.add("web_portal");
        allowedSourceIds.add("admin_console");
        allowedSourceIds.add("api_gateway");
        allowedSourceIds.add("data_sync");
        
        log.info("加载默认 Source-Id 白名单: {}", allowedSourceIds);
    }

    /**
     * 从配置源加载动态配置
     */
    private void loadDynamicConfig() {
        if (configSource == null) {
            log.warn("配置源不可用，使用默认配置");
            return;
        }

        try {
            // 加载白名单设置
            configSource.getConfig("source.id.whitelist.enabled")
                    .subscribe(whitelistEnabled -> {
                        if (StringUtils.hasText(whitelistEnabled)) {
                            enableWhitelist = Boolean.parseBoolean(whitelistEnabled);
                            log.info("白名单校验设置: {}", enableWhitelist);
                        }
                    });

            // 加载空值允许设置
            configSource.getConfig("source.id.allow.empty")
                    .subscribe(allowEmptyConfig -> {
                        if (StringUtils.hasText(allowEmptyConfig)) {
                            allowEmpty = Boolean.parseBoolean(allowEmptyConfig);
                            log.info("允许空 Source-Id 设置: {}", allowEmpty);
                        }
                    });

            // 加载自定义白名单
            configSource.getConfig("source.id.whitelist.custom")
                    .subscribe(customWhitelist -> {
                        if (StringUtils.hasText(customWhitelist)) {
                            String[] customIds = customWhitelist.split(",");
                            for (String id : customIds) {
                                String trimmedId = id.trim();
                                if (StringUtils.hasText(trimmedId)) {
                                    allowedSourceIds.add(trimmedId);
                                }
                            }
                            log.info("加载自定义白名单: {}", java.util.Arrays.toString(customIds));
                        }
                    });

        } catch (Exception e) {
            log.error("加载动态配置失败", e);
        }
    }

    /**
     * 设置配置监听器
     */
    private void setupConfigListener() {
        if (configSource == null) {
            return;
        }

        try {
            // 监听白名单配置变化
            configSource.addConfigListener("source.id.whitelist.custom", 
                    (configKey, oldValue, newValue) -> {
                        log.info("检测到白名单配置变化: {} -> {}", oldValue, newValue);
                        updateWhitelist(newValue);
                    });

            // 监听白名单启用状态变化
            configSource.addConfigListener("source.id.whitelist.enabled", 
                    (configKey, oldValue, newValue) -> {
                        log.info("检测到白名单启用状态变化: {} -> {}", oldValue, newValue);
                        enableWhitelist = Boolean.parseBoolean(newValue);
                    });

            // 监听空值允许状态变化
            configSource.addConfigListener("source.id.allow.empty", 
                    (configKey, oldValue, newValue) -> {
                        log.info("检测到空值允许状态变化: {} -> {}", oldValue, newValue);
                        allowEmpty = Boolean.parseBoolean(newValue);
                    });

        } catch (Exception e) {
            log.error("设置配置监听器失败", e);
        }
    }

    /**
     * 更新白名单
     */
    private void updateWhitelist(String newWhitelist) {
        try {
            Set<String> newAllowedIds = new HashSet<>();
            
            // 保留默认白名单
            loadDefaultWhitelist();
            newAllowedIds.addAll(allowedSourceIds);

            // 添加新的自定义白名单
            if (StringUtils.hasText(newWhitelist)) {
                String[] customIds = newWhitelist.split(",");
                for (String id : customIds) {
                    String trimmedId = id.trim();
                    if (StringUtils.hasText(trimmedId)) {
                        newAllowedIds.add(trimmedId);
                    }
                }
            }

            allowedSourceIds = newAllowedIds;
            log.info("白名单已更新，当前项目数: {}", allowedSourceIds.size());

        } catch (Exception e) {
            log.error("更新白名单失败", e);
        }
    }

    /**
     * 校验结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}