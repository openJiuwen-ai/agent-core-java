// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging;

/**
 * 日志过滤器接口
 *
 * <p>用于过滤日志消息，控制哪些日志应该被输出。</p>
 */
public interface LogFilter {

    /**
     * 判断是否应该记录该日志
     *
     * @param level 日志级别
     * @param message 日志消息
     * @return 如果应该记录返回true，否则返回false
     */
    boolean shouldLog(LogLevel level, String message);
}