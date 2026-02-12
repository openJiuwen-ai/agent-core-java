// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.session.tracer.TracerDecorator;

/**
 * 适配器类，将 AgentSession 适配到 TracerDecorator.AgentSession 接口
 * 
 * <p>解决接口不兼容问题：
 * <ul>
 *   <li>TracerDecorator.AgentSession.tracer() → AgentSession.getTracer()</li>
 *   <li>TracerDecorator.AgentSession.span() → AgentSession.getSpan() (需类型转换)</li>
 * </ul>
 */
public class AgentSessionAdapter implements TracerDecorator.AgentSession {
    
    private final AgentSession session;
    
    /**
     * 创建适配器
     * 
     * @param session 原始 AgentSession 实例
     */
    public AgentSessionAdapter(AgentSession session) {
        this.session = session;
    }
    
    /**
     * 获取 Tracer 实例
     * 
     * @return Tracer 实例，如果原始 session 为 null 则返回 null
     */
    @Override
    public Tracer tracer() {
        if (session == null) {
            return null;
        }
        return session.getTracer();
    }
    
    /**
     * 获取当前 TraceAgentSpan
     * 
     * @return TraceAgentSpan 实例，如果 span 不是 TraceAgentSpan 类型或 session 为 null 则返回 null
     */
    @Override
    public TraceAgentSpan span() {
        if (session == null) {
            return null;
        }
        Object span = session.getSpan();
        if (span instanceof TraceAgentSpan) {
            return (TraceAgentSpan) span;
        }
        return null;
    }
    
    /**
     * 从 AgentSession 创建适配器（静态工厂方法）
     * 
     * @param session AgentSession 实例（可为 null）
     * @return 适配器实例，如果 session 为 null 则返回 null
     */
    public static AgentSessionAdapter of(AgentSession session) {
        if (session == null) {
            return null;
        }
        return new AgentSessionAdapter(session);
    }
}

