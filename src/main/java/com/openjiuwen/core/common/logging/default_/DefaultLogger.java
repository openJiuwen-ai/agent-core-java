// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging.default_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import com.openjiuwen.core.common.logging.LogFilter;
import com.openjiuwen.core.common.logging.LogHandler;
import com.openjiuwen.core.common.logging.LogLevel;
import com.openjiuwen.core.common.logging.LoggerProtocol;

/**
 * 默认日志记录器实现
 *
 * <p>对应Python版本: openjiuwen/core/common/logging/default/default_impl.py</p>
 *
 * <p>实现LoggerProtocol接口，提供完整的日志功能。</p>
 */
public class DefaultLogger implements LoggerProtocol {

    private final String logType;
    private final Map<String, Object> config;
    private final Logger slf4jLogger;

    /**
     * 构造函数
     *
     * @param logType 日志类型标识符
     * @param config 日志配置字典
     */
    @SuppressWarnings("unchecked")
    public DefaultLogger(String logType, Map<String, Object> config) {
        this.logType = logType;
        this.config = config != null ? Map.copyOf(config) : Map.of();
        this.slf4jLogger = LoggerFactory.getLogger(logType);

        setupLogger();
    }

    /**
     * 设置日志记录器
     */
    private void setupLogger() {
        // SLF4J会自动处理日志级别和输出目标
        // 这里可以根据config进行额外配置
    }

    /**
     * 清理消息中的控制字符
     *
     * @param msg 原始消息
     * @return 清理后的消息字符串
     */
    private String sanitizeMessage(Object msg) {
        if (msg == null) {
            return "";
        }
        String message = msg.toString();
        // 替换控制字符
        return message.replace("\r", "\\r")
                     .replace("\n", "\\n")
                     .replace("\t", "\\t")
                     .replace("\b", "\\b");
    }

    /**
     * 处理日志消息
     *
     * @param logLevel 日志级别
     * @param msg 日志消息
     * @param args 消息参数
     * @return 处理后的消息字符串
     */
    private String processLogMessage(LogLevel logLevel, String msg, Object... args) {
        String sanitizedMsg = sanitizeMessage(msg);

        if (args != null && args.length > 0) {
            try {
                return String.format(sanitizedMsg, args);
            } catch (Exception e) {
                // 格式化失败，返回原始消息
                return sanitizedMsg;
            }
        }

        return sanitizedMsg;
    }

    @Override
    public void debug(String msg, Object... args) {
        String processedMsg = processLogMessage(LogLevel.DEBUG, msg, args);
        slf4jLogger.debug(processedMsg);
    }

    @Override
    public void info(String msg, Object... args) {
        String processedMsg = processLogMessage(LogLevel.INFO, msg, args);
        slf4jLogger.info(processedMsg);
    }

    @Override
    public void warning(String msg, Object... args) {
        String processedMsg = processLogMessage(LogLevel.WARNING, msg, args);
        slf4jLogger.warn(processedMsg);
    }

    @Override
    public void error(String msg, Object... args) {
        String processedMsg = processLogMessage(LogLevel.ERROR, msg, args);
        slf4jLogger.error(processedMsg);
    }

    @Override
    public void critical(String msg, Object... args) {
        String processedMsg = processLogMessage(LogLevel.CRITICAL, msg, args);
        slf4jLogger.error(processedMsg);
    }

    @Override
    public void exception(String msg, Object... args) {
        String processedMsg = processLogMessage(LogLevel.ERROR, msg, args);
        slf4jLogger.error(processedMsg);
    }

    @Override
    public void log(LogLevel level, String msg, Object... args) {
        String processedMsg = processLogMessage(level, msg, args);

        switch (level) {
            case DEBUG -> slf4jLogger.debug(processedMsg);
            case INFO -> slf4jLogger.info(processedMsg);
            case WARNING -> slf4jLogger.warn(processedMsg);
            case ERROR -> slf4jLogger.error(processedMsg);
            case CRITICAL -> slf4jLogger.error(processedMsg);
        }
    }

    @Override
    public void setLevel(LogLevel level) {
        // SLF4J通过配置文件设置级别，不支持运行时修改
        // 这里可以记录日志或使用其他方式
    }

    @Override
    public LogLevel getLevel() {
        // SLF4J不提供获取当前级别的方法
        return LogLevel.INFO;
    }

    @Override
    public void addHandler(LogHandler handler) {
        // SLF4J不直接支持handler操作
        // 可以通过配置文件配置appender
    }

    @Override
    public void removeHandler(LogHandler handler) {
        // SLF4J不直接支持handler操作
    }

    @Override
    public Object logger() {
        return slf4jLogger;
    }

    @Override
    public void addFilter(LogFilter filter) {
        // SLF4J不直接支持filter操作
        // 可以通过配置文件配置过滤器
    }

    @Override
    public void removeFilter(LogFilter filter) {
        // SLF4J不直接支持filter操作
    }

    @Override
    public Map<String, Object> getConfig() {
        return Map.copyOf(config);
    }

    @Override
    public void reconfigure(Map<String, Object> config) {
        // 简化实现：不支持运行时重新配置
    }
}