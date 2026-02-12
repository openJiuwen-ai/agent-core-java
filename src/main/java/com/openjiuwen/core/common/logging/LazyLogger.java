// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

import java.util.Map;
import java.util.function.Supplier;


/**
 * 延迟初始化日志器
 * 
 * <p>Logger只在实际使用时才会被初始化，提升启动性能。
 * 适用于模块级别的logger定义。
 * 
 * <p>对应 Python: __init__.py 中的 LazyLogger 类
 * 
 * @since 0.1.4
 */
public class LazyLogger implements LoggerProtocol {
    
    private final Supplier<LoggerProtocol> getterFunc;
    private volatile LoggerProtocol logger;
    
    /**
     * 初始化延迟日志器
     * 
     * @param getterFunc 获取logger的函数，在首次使用时调用
     */
    public LazyLogger(Supplier<LoggerProtocol> getterFunc) {
        this.getterFunc = getterFunc;
    }
    
    /**
     * 获取底层logger实例（延迟初始化）
     * 
     * @return logger实例
     */
    private LoggerProtocol getLoggerInstance() {
        if (logger == null) {
            synchronized (this) {
                if (logger == null) {
                    ensureInitialized();
                    logger = getterFunc.get();
                }
            }
        }
        return logger;
    }
    
    /**
     * 确保日志系统已初始化
     */
    private void ensureInitialized() {
        // 尝试加载默认Logger实现
        try {
            Class<?> defaultLoggerClass = Class.forName(
                "com.openjiuwen.core.common.logging.defaults.DefaultLogger"
            );
            @SuppressWarnings("unchecked")
            Class<? extends LoggerProtocol> loggerClass = 
                (Class<? extends LoggerProtocol>) defaultLoggerClass;
            LogManager.setDefaultLoggerClass(loggerClass);
        } catch (ClassNotFoundException e) {
            // 默认Logger不可用，使用简单实现
        }
        LogManager.initialize();
    }
    
    @Override
    public void debug(String msg, Object... args) {
        getLoggerInstance().debug(msg, args);
    }
    
    @Override
    public void info(String msg, Object... args) {
        getLoggerInstance().info(msg, args);
    }
    
    @Override
    public void warning(String msg, Object... args) {
        getLoggerInstance().warning(msg, args);
    }
    
    @Override
    public void error(String msg, Object... args) {
        getLoggerInstance().error(msg, args);
    }
    
    @Override
    public void critical(String msg, Object... args) {
        getLoggerInstance().critical(msg, args);
    }
    
    @Override
    public void exception(String msg, Throwable cause) {
        getLoggerInstance().exception(msg, cause);
    }
    
    @Override
    public void log(int level, String msg, Object... args) {
        getLoggerInstance().log(level, msg, args);
    }
    
    @Override
    public void setLevel(int level) {
        getLoggerInstance().setLevel(level);
    }
    
    @Override
    public Map<String, Object> getConfig() {
        return getLoggerInstance().getConfig();
    }
    
    @Override
    public void reconfigure(Map<String, Object> config) {
        getLoggerInstance().reconfigure(config);
    }
    
    // ==================== Structured logging proxy methods ====================
    
    @Override
    public void debug(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        getLoggerInstance().debug(msg, eventType, kwargs);
    }
    
    @Override
    public void debug(String msg, BaseLogEvent event) {
        getLoggerInstance().debug(msg, event);
    }
    
    @Override
    public void info(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        getLoggerInstance().info(msg, eventType, kwargs);
    }
    
    @Override
    public void info(String msg, BaseLogEvent event) {
        getLoggerInstance().info(msg, event);
    }
    
    @Override
    public void warning(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        getLoggerInstance().warning(msg, eventType, kwargs);
    }
    
    @Override
    public void warning(String msg, BaseLogEvent event) {
        getLoggerInstance().warning(msg, event);
    }
    
    @Override
    public void error(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        getLoggerInstance().error(msg, eventType, kwargs);
    }
    
    @Override
    public void error(String msg, BaseLogEvent event) {
        getLoggerInstance().error(msg, event);
    }
    
    @Override
    public void critical(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        getLoggerInstance().critical(msg, eventType, kwargs);
    }
    
    @Override
    public void critical(String msg, BaseLogEvent event) {
        getLoggerInstance().critical(msg, event);
    }
    
    @Override
    public void exception(String msg, Throwable cause, LogEventType eventType, Map<String, Object> kwargs) {
        getLoggerInstance().exception(msg, cause, eventType, kwargs);
    }
    
    @Override
    public void exception(String msg, Throwable cause, BaseLogEvent event) {
        getLoggerInstance().exception(msg, cause, event);
    }
    
    // ==================== Configuration proxy methods ====================
    
    @Override
    public void addHandler(Object handler) {
        getLoggerInstance().addHandler(handler);
    }
    
    @Override
    public void removeHandler(Object handler) {
        getLoggerInstance().removeHandler(handler);
    }
    
    @Override
    public void addFilter(Object filter) {
        getLoggerInstance().addFilter(filter);
    }
    
    @Override
    public void removeFilter(Object filter) {
        getLoggerInstance().removeFilter(filter);
    }
    
    @Override
    public Object getLogger() {
        return getLoggerInstance().getLogger();
    }
}

