/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.defaults;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.openjiuwen.core.common.logging.LoggingUtils;
import org.slf4j.MDC;

/**
 * 上下文过滤器
 * 
 * <p>为日志记录添加上下文信息（trace_id 和 log_type）。
 * 适配异步环境，使用 ThreadLocal 获取上下文信息。
 * 
 * <p>对应 Python: default/default_impl.py::ContextFilter
 */
public class ContextFilter extends Filter<ILoggingEvent> {
    
    private String logType;
    
    /**
     * 创建上下文过滤器
     */
    public ContextFilter() {
        this("default");
    }
    
    /**
     * 创建上下文过滤器
     * 
     * @param logType 日志类型标识
     */
    public ContextFilter(String logType) {
        this.logType = logType;
    }
    
    /**
     * 设置日志类型
     */
    public void setLogType(String logType) {
        this.logType = logType;
    }
    
    /**
     * 获取日志类型
     */
    public String getLogType() {
        return logType;
    }
    
    @Override
    public FilterReply decide(ILoggingEvent event) {
        // 从上下文变量获取 trace_id（适配异步环境）
        String traceId = LoggingUtils.getSessionId();
        MDC.put("trace_id", traceId);
        
        // 设置日志类型，performance 类型特殊处理为 "perf"
        String effectiveLogType = "performance".equals(logType) ? "perf" : logType;
        MDC.put("log_type", effectiveLogType);
        
        return FilterReply.NEUTRAL;
    }
}

