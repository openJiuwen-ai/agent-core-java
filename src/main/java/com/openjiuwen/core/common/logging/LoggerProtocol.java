// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging;

import java.util.List;
import java.util.Map;

/**
 * 日志记录器协议接口
 *
 * <p>定义所有日志记录器实现必须提供的方法。</p>
 */
public interface LoggerProtocol {

    /**
     * 记录调试级别日志
     *
     * @param msg 日志消息
     * @param args 消息参数
     */
    void debug(String msg, Object... args);

    /**
     * 记录信息级别日志
     *
     * @param msg 日志消息
     * @param args 消息参数
     */
    void info(String msg, Object... args);

    /**
     * 记录警告级别日志
     *
     * @param msg 日志消息
     * @param args 消息参数
     */
    void warning(String msg, Object... args);

    /**
     * 记录错误级别日志
     *
     * @param msg 日志消息
     * @param args 消息参数
     */
    void error(String msg, Object... args);

    /**
     * 记录严重级别日志
     *
     * @param msg 日志消息
     * @param args 消息参数
     */
    void critical(String msg, Object... args);

    /**
     * 记录异常（包含堆栈跟踪）
     *
     * @param msg 日志消息
     * @param args 消息参数
     */
    void exception(String msg, Object... args);

    /**
     * 通用日志记录方法
     *
     * @param level 日志级别
     * @param msg 日志消息
     * @param args 消息参数
     */
    void log(LogLevel level, String msg, Object... args);

    /**
     * 设置日志级别
     *
     * @param level 日志级别
     */
    void setLevel(LogLevel level);

    /**
     * 获取日志级别
     *
     * @return 日志级别
     */
    LogLevel getLevel();

    /**
     * 添加日志处理器
     *
     * @param handler 日志处理器
     */
    void addHandler(LogHandler handler);

    /**
     * 移除日志处理器
     *
     * @param handler 日志处理器
     */
    void removeHandler(LogHandler handler);

    /**
     * 获取内部日志记录器
     *
     * @return 内部日志记录器
     */
    Object logger();

    /**
     * 添加日志过滤器
     *
     * @param filter 日志过滤器
     */
    void addFilter(LogFilter filter);

    /**
     * 移除日志过滤器
     *
     * @param filter 日志过滤器
     */
    void removeFilter(LogFilter filter);

    /**
     * 获取日志配置
     *
     * @return 配置映射
     */
    Map<String, Object> getConfig();

    /**
     * 重新配置日志记录器
     *
     * @param config 配置映射
     */
    void reconfigure(Map<String, Object> config);
}