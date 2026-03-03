// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging;

/**
 * 日志处理器接口
 *
 * <p>用于处理日志输出的处理器，可以附加到日志记录器上。</p>
 */
public interface LogHandler {

    /**
     * 处理日志消息
     *
     * @param level 日志级别
     * @param message 日志消息
     */
    void handle(LogLevel level, String message);
}