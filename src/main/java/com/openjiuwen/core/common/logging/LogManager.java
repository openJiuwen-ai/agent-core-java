// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.openjiuwen.core.common.logging.default_.LogConfig;

/**
 * 日志管理器
 *
 * <p>提供日志记录器的创建、注册和检索功能。</p>
 *
 * <p>针对异步环境优化，使用线程安全的并发结构。</p>
 */
public final class LogManager {

    private static final Map<String, LoggerProtocol> LOGGERS = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    private static Class<? extends LoggerProtocol> defaultLoggerClass = null;

    /**
     * 私有构造函数，防止实例化
     */
    private LogManager() {
    }

    /**
     * 设置默认日志记录器类
     *
     * @param loggerClass 默认日志记录器类
     */
    public static synchronized void setDefaultLoggerClass(Class<? extends LoggerProtocol> loggerClass) {
        defaultLoggerClass = loggerClass;
    }

    /**
     * 初始化日志管理器
     *
     * <p>在异步环境中，通常只在应用程序启动时调用一次。</p>
     * <p>如果多次调用，已初始化的部分将被跳过（幂等操作）。</p>
     *
     * <p>根据Python版本行为，从LogConfig获取所有配置，预创建logger实例。</p>
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        try {
            Class<? extends LoggerProtocol> loggerClass = getDefaultLoggerClass();
            LogConfig logConfig = LogConfig.getInstance();

            Map<String, Map<String, Object>> allConfigs = logConfig.getAllConfigs();
            for (Map.Entry<String, Map<String, Object>> entry : allConfigs.entrySet()) {
                String logType = entry.getKey();
                if (!LOGGERS.containsKey(logType)) {
                    Map<String, Object> config = entry.getValue();
                    createLoggerWithConfig(logType, loggerClass, config);
                }
            }
        } catch (Exception e) {
            // 配置不可用时继续，允许延迟创建logger
            // Python版本在配置不可用时会抛出RuntimeError，但在Java中我们采用更宽松的策略
        }

        initialized = true;
    }

    /**
     * 注册自定义日志记录器
     *
     * @param logType 日志类型标识符
     * @param logger 日志记录器实例，必须实现LoggerProtocol
     * @throws IllegalArgumentException 如果logger不支持基本方法
     */
    public static void registerLogger(String logType, LoggerProtocol logger) {
        if (logger == null) {
            throw new IllegalArgumentException("Logger cannot be null");
        }

        // 基本类型检查
        if (!supportsBasicMethods(logger)) {
            throw new IllegalArgumentException(
                "Logger must implement basic LoggerProtocol methods, got: " + logger.getClass().getName()
            );
        }

        LOGGERS.put(logType, logger);
    }

    /**
     * 获取日志记录器
     *
     * <p>根据Python版本行为，从LogConfig获取配置并传递给logger构造函数。</p>
     *
     * @param logType 日志类型标识符
     * @return 日志记录器实例
     */
    public static LoggerProtocol getLogger(String logType) {
        if (!initialized) {
            initialize();
        }

        return LOGGERS.computeIfAbsent(logType, key -> createDefaultLogger(key));
    }

    /**
     * 获取所有已注册的日志记录器
     *
     * @return 日志记录器映射的副本
     */
    public static Map<String, LoggerProtocol> getAllLoggers() {
        if (!initialized) {
            initialize();
        }
        return Map.copyOf(LOGGERS);
    }

    /**
     * 重置日志管理器
     *
     * <p>清除所有日志记录器和初始化状态。</p>
     * <p>主要用于测试场景。</p>
     */
    public static synchronized void reset() {
        LOGGERS.clear();
        initialized = false;
        defaultLoggerClass = null;
    }

    /**
     * 创建默认日志记录器
     *
     * <p>根据Python版本行为，从LogConfig获取配置并传递给logger。</p>
     *
     * @param logType 日志类型
     * @return 日志记录器实例
     */
    private static LoggerProtocol createDefaultLogger(String logType) {
        Class<? extends LoggerProtocol> loggerClass = getDefaultLoggerClass();
        Map<String, Object> config = null;

        try {
            LogConfig logConfig = LogConfig.getInstance();
            config = logConfig.getCustomConfig(logType, null);
        } catch (Exception e) {
            // 配置获取失败时使用空配置
            config = Map.of();
        }

        return createLoggerWithConfig(logType, loggerClass, config);
    }

    /**
     * 使用配置创建日志记录器
     *
     * @param logType 日志类型
     * @param loggerClass 日志记录器类
     * @param config 日志配置
     * @return 日志记录器实例
     */
    private static LoggerProtocol createLoggerWithConfig(
        String logType,
        Class<? extends LoggerProtocol> loggerClass,
        Map<String, Object> config
    ) {
        try {
            // 使用带config的构造函数
            return loggerClass.getConstructor(String.class, Map.class).newInstance(logType, config);
        } catch (NoSuchMethodException e) {
            // 如果没有带config的构造函数，使用单参数构造函数
            try {
                return loggerClass.getConstructor(String.class).newInstance(logType);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create default logger for type: " + logType, ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default logger for type: " + logType, e);
        }
    }

    /**
     * 获取默认日志记录器类
     *
     * @return 默认日志记录器类
     */
    private static Class<? extends LoggerProtocol> getDefaultLoggerClass() {
        if (defaultLoggerClass == null) {
            // 尝试加载默认实现（从default子包）
            try {
                @SuppressWarnings("unchecked")
                Class<? extends LoggerProtocol> clazz = (Class<? extends LoggerProtocol>)
                    Class.forName("com.openjiuwen.core.common.logging.default_.DefaultLogger");
                defaultLoggerClass = clazz;
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                    "No default logger class set and cannot load DefaultLogger. " +
                    "Please set default logger class using setDefaultLoggerClass().",
                    e
                );
            }
        }
        return defaultLoggerClass;
    }

    /**
     * 检查日志记录器是否支持基本方法
     *
     * @param logger 日志记录器
     * @return 是否支持
     */
    private static boolean supportsBasicMethods(LoggerProtocol logger) {
        try {
            // 检查是否能获取方法（协议接口已定义，Java类型系统保证）
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}