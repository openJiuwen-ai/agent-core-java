/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.events;

import com.openjiuwen.core.common.logging.BaseLogEvent;
import com.openjiuwen.core.common.logging.EventStatus;
import com.openjiuwen.core.common.logging.LogEventType;
import com.openjiuwen.core.common.logging.LogLevel;
import com.openjiuwen.core.common.logging.ModuleType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Session 管理相关事件
 * 
 * <p>记录会话创建、更新和删除的日志事件。
 * 
 * <p>对应 Python: events.py::SessionEvent
 */
public record SessionEvent(
    // 基础事件信息
    String eventId,
    LogEventType eventType,
    LogLevel logLevel,
    Instant timestamp,
    ModuleType moduleType,
    String moduleId,
    String moduleName,
    
    // 上下文信息
    String sessionId,
    String conversationId,
    String traceId,
    String correlationId,
    String parentEventId,
    
    // 状态和结果
    EventStatus status,
    String errorCode,
    String errorMessage,
    
    // 消息和堆栈跟踪
    String message,
    String stacktrace,
    String exception,
    
    // 扩展字段
    Map<String, Object> metadata,
    
    // Session 特定字段
    String sessionType,
    String userId,
    String agentId,
    String workflowId,
    Map<String, Object> sessionConfig,
    Integer messageCount
) implements BaseLogEvent {
    
    /**
     * 创建带默认值的 SessionEvent
     */
    public SessionEvent {
        if (eventId == null) eventId = UUID.randomUUID().toString();
        if (eventType == null) eventType = LogEventType.SESSION_CREATE;
        if (logLevel == null) logLevel = LogLevel.INFO;
        if (timestamp == null) timestamp = Instant.now();
        if (moduleType == null) moduleType = ModuleType.SESSION;
        if (status == null) status = EventStatus.SUCCESS;
        if (metadata == null) metadata = new HashMap<>();
    }
    
    /**
     * Builder 类
     */
    public static class Builder {
        private String eventId;
        private LogEventType eventType = LogEventType.SESSION_CREATE;
        private LogLevel logLevel = LogLevel.INFO;
        private Instant timestamp;
        private ModuleType moduleType = ModuleType.SESSION;
        private String moduleId;
        private String moduleName;
        private String sessionId;
        private String conversationId;
        private String traceId;
        private String correlationId;
        private String parentEventId;
        private EventStatus status = EventStatus.SUCCESS;
        private String errorCode;
        private String errorMessage;
        private String message;
        private String stacktrace;
        private String exception;
        private Map<String, Object> metadata = new HashMap<>();
        private String sessionType;
        private String userId;
        private String agentId;
        private String workflowId;
        private Map<String, Object> sessionConfig;
        private Integer messageCount;
        
        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder eventType(LogEventType eventType) { this.eventType = eventType; return this; }
        public Builder logLevel(LogLevel logLevel) { this.logLevel = logLevel; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder moduleId(String moduleId) { this.moduleId = moduleId; return this; }
        public Builder moduleName(String moduleName) { this.moduleName = moduleName; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder parentEventId(String parentEventId) { this.parentEventId = parentEventId; return this; }
        public Builder status(EventStatus status) { this.status = status; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder stacktrace(String stacktrace) { this.stacktrace = stacktrace; return this; }
        public Builder exception(String exception) { this.exception = exception; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder sessionType(String sessionType) { this.sessionType = sessionType; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder workflowId(String workflowId) { this.workflowId = workflowId; return this; }
        public Builder sessionConfig(Map<String, Object> sessionConfig) { this.sessionConfig = sessionConfig; return this; }
        public Builder messageCount(Integer messageCount) { this.messageCount = messageCount; return this; }
        
        public SessionEvent build() {
            return new SessionEvent(
                eventId, eventType, logLevel, timestamp, moduleType,
                moduleId, moduleName, sessionId, conversationId, traceId,
                correlationId, parentEventId, status, errorCode, errorMessage,
                message, stacktrace, exception, metadata,
                sessionType, userId, agentId, workflowId, sessionConfig, messageCount
            );
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // BaseLogEvent接口方法实现（适配record的accessor方法）
    @Override
    public String getEventId() { return eventId(); }
    
    @Override
    public LogEventType getEventType() { return eventType(); }
    
    @Override
    public LogLevel getLogLevel() { return logLevel(); }
    
    @Override
    public Instant getTimestamp() { return timestamp(); }
    
    @Override
    public ModuleType getModuleType() { return moduleType(); }
    
    @Override
    public String getSessionId() { return sessionId(); }
    
    @Override
    public String getModuleId() { return moduleId(); }
    
    @Override
    public String getModuleName() { return moduleName(); }
    
    @Override
    public String getConversationId() { return conversationId(); }
    
    @Override
    public String getTraceId() { return traceId(); }
    
    @Override
    public String getCorrelationId() { return correlationId(); }
    
    @Override
    public String getParentEventId() { return parentEventId(); }
    
    @Override
    public EventStatus getStatus() { return status(); }
    
    @Override
    public String getErrorCode() { return errorCode(); }
    
    @Override
    public String getErrorMessage() { return errorMessage(); }
    
    @Override
    public String getMessage() { return message(); }
    
    @Override
    public String getStacktrace() { return stacktrace(); }
    
    @Override
    public String getException() { return exception(); }
    
    @Override
    public Map<String, Object> getMetadata() { return metadata(); }
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        
        // 基础事件信息
        result.put("event_id", eventId);
        result.put("event_type", eventType != null ? eventType.getValue() : null);
        result.put("log_level", logLevel != null ? logLevel.getValue() : null);
        result.put("timestamp", timestamp != null ? timestamp.toString() : null);
        result.put("module_type", moduleType != null ? moduleType.getValue() : null);
        result.put("module_id", moduleId);
        result.put("module_name", moduleName);
        
        // 上下文信息
        result.put("session_id", sessionId);
        result.put("conversation_id", conversationId);
        result.put("trace_id", traceId);
        result.put("correlation_id", correlationId);
        result.put("parent_event_id", parentEventId);
        
        // 状态和结果
        result.put("status", status != null ? status.getValue() : null);
        result.put("error_code", errorCode);
        result.put("error_message", errorMessage);
        
        // 消息和堆栈跟踪
        result.put("message", message);
        result.put("stacktrace", stacktrace);
        result.put("exception", exception);
        
        // 扩展字段
        result.put("metadata", metadata);
        
        // Session 特定字段
        result.put("session_type", sessionType);
        result.put("user_id", userId);
        result.put("agent_id", agentId);
        result.put("workflow_id", workflowId);
        result.put("session_config", sessionConfig);
        result.put("message_count", messageCount);
        
        // 递归转换嵌套结构中的 Enum/Instant
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            Object v = entry.getValue();
            if (v instanceof Map || v instanceof java.util.List) {
                entry.setValue(BaseLogEvent.convertSingleValue(v));
            }
        }
        
        return result;
    }
}

