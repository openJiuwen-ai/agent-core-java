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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LLM 调用相关事件
 * 
 * <p>记录 LLM 调用和响应的日志事件。
 * 
 * <p>对应 Python: events.py::LLMEvent
 */
public record LLMEvent(
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
    
    // LLM 特定字段
    String modelName,
    String modelProvider,
    String query,
    List<Map<String, Object>> messages,
    List<Map<String, Object>> tools,
    Double temperature,
    Integer maxTokens,
    Double topP,
    String responseContent,
    List<Map<String, Object>> toolCalls,
    Map<String, Object> usage,
    Double latencyMs,
    boolean isStream,
    Integer chunkIndex,
    Map<String, Object> extraParams
) implements BaseLogEvent {
    
    /**
     * 创建带默认值的 LLMEvent
     */
    public LLMEvent {
        if (eventId == null) eventId = UUID.randomUUID().toString();
        if (eventType == null) eventType = LogEventType.LLM_CALL_START;
        if (logLevel == null) logLevel = LogLevel.INFO;
        if (timestamp == null) timestamp = Instant.now();
        if (moduleType == null) moduleType = ModuleType.LLM;
        if (status == null) status = EventStatus.SUCCESS;
        if (metadata == null) metadata = new HashMap<>();
    }
    
    /**
     * Builder 类
     */
    public static class Builder {
        private String eventId;
        private LogEventType eventType = LogEventType.LLM_CALL_START;
        private LogLevel logLevel = LogLevel.INFO;
        private Instant timestamp;
        private ModuleType moduleType = ModuleType.LLM;
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
        private String modelName;
        private String modelProvider;
        private String query;
        private List<Map<String, Object>> messages;
        private List<Map<String, Object>> tools;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private String responseContent;
        private List<Map<String, Object>> toolCalls;
        private Map<String, Object> usage;
        private Double latencyMs;
        private boolean isStream;
        private Integer chunkIndex;
        private Map<String, Object> extraParams;
        
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
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder modelProvider(String modelProvider) { this.modelProvider = modelProvider; return this; }
        public Builder query(String query) { this.query = query; return this; }
        public Builder messages(List<Map<String, Object>> messages) { this.messages = messages; return this; }
        public Builder tools(List<Map<String, Object>> tools) { this.tools = tools; return this; }
        public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder topP(Double topP) { this.topP = topP; return this; }
        public Builder responseContent(String responseContent) { this.responseContent = responseContent; return this; }
        public Builder toolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; return this; }
        public Builder usage(Map<String, Object> usage) { this.usage = usage; return this; }
        public Builder latencyMs(Double latencyMs) { this.latencyMs = latencyMs; return this; }
        public Builder isStream(boolean isStream) { this.isStream = isStream; return this; }
        public Builder chunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; return this; }
        public Builder extraParams(Map<String, Object> extraParams) { this.extraParams = extraParams; return this; }
        
        public LLMEvent build() {
            return new LLMEvent(
                eventId, eventType, logLevel, timestamp, moduleType,
                moduleId, moduleName, sessionId, conversationId, traceId,
                correlationId, parentEventId, status, errorCode, errorMessage,
                message, stacktrace, exception, metadata,
                modelName, modelProvider, query, messages, tools, temperature, maxTokens, topP,
                responseContent, toolCalls, usage, latencyMs, isStream, chunkIndex, extraParams
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
        
        // LLM 特定字段
        result.put("model_name", modelName);
        result.put("model_provider", modelProvider);
        result.put("query", query);
        result.put("messages", messages);
        result.put("tools", tools);
        result.put("temperature", temperature);
        result.put("max_tokens", maxTokens);
        result.put("top_p", topP);
        result.put("response_content", responseContent);
        result.put("tool_calls", toolCalls);
        result.put("usage", usage);
        result.put("latency_ms", latencyMs);
        result.put("is_stream", isStream);
        result.put("chunk_index", chunkIndex);
        result.put("extra_params", extraParams);
        
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

