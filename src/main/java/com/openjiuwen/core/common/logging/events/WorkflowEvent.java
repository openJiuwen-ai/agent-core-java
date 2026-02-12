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
 * Workflow 相关事件
 * 
 * <p>记录 Workflow 执行和状态的日志事件。
 * 
 * <p>对应 Python: events.py::WorkflowEvent
 */
public record WorkflowEvent(
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
    
    // Workflow 特定字段
    String workflowId,
    String workflowName,
    String componentId,
    String componentName,
    String componentTypeStr,
    String branchCondition,
    String selectedBranch,
    Map<String, Object> inputData,
    Map<String, Object> outputData,
    Double executionTimeMs
) implements BaseLogEvent {
    
    /**
     * 创建带默认值的 WorkflowEvent
     */
    public WorkflowEvent {
        if (eventId == null) eventId = UUID.randomUUID().toString();
        if (eventType == null) eventType = LogEventType.WORKFLOW_START;
        if (logLevel == null) logLevel = LogLevel.INFO;
        if (timestamp == null) timestamp = Instant.now();
        // 根据 componentId 判断模块类型
        if (moduleType == null) {
            moduleType = (componentId != null && !componentId.isEmpty()) 
                ? ModuleType.WORKFLOW_COMPONENT 
                : ModuleType.WORKFLOW;
        }
        if (status == null) status = EventStatus.SUCCESS;
        if (metadata == null) metadata = new HashMap<>();
    }
    
    /**
     * Builder 类
     */
    public static class Builder {
        private String eventId;
        private LogEventType eventType = LogEventType.WORKFLOW_START;
        private LogLevel logLevel = LogLevel.INFO;
        private Instant timestamp;
        private ModuleType moduleType;
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
        private String workflowId;
        private String workflowName;
        private String componentId;
        private String componentName;
        private String componentTypeStr;
        private String branchCondition;
        private String selectedBranch;
        private Map<String, Object> inputData;
        private Map<String, Object> outputData;
        private Double executionTimeMs;
        
        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder eventType(LogEventType eventType) { this.eventType = eventType; return this; }
        public Builder logLevel(LogLevel logLevel) { this.logLevel = logLevel; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder moduleType(ModuleType moduleType) { this.moduleType = moduleType; return this; }
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
        public Builder workflowId(String workflowId) { this.workflowId = workflowId; return this; }
        public Builder workflowName(String workflowName) { this.workflowName = workflowName; return this; }
        public Builder componentId(String componentId) { this.componentId = componentId; return this; }
        public Builder componentName(String componentName) { this.componentName = componentName; return this; }
        public Builder componentTypeStr(String componentTypeStr) { this.componentTypeStr = componentTypeStr; return this; }
        public Builder branchCondition(String branchCondition) { this.branchCondition = branchCondition; return this; }
        public Builder selectedBranch(String selectedBranch) { this.selectedBranch = selectedBranch; return this; }
        public Builder inputData(Map<String, Object> inputData) { this.inputData = inputData; return this; }
        public Builder outputData(Map<String, Object> outputData) { this.outputData = outputData; return this; }
        public Builder executionTimeMs(Double executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
        
        public WorkflowEvent build() {
            return new WorkflowEvent(
                eventId, eventType, logLevel, timestamp, moduleType,
                moduleId, moduleName, sessionId, conversationId, traceId,
                correlationId, parentEventId, status, errorCode, errorMessage,
                message, stacktrace, exception, metadata,
                workflowId, workflowName, componentId, componentName, componentTypeStr,
                branchCondition, selectedBranch, inputData, outputData, executionTimeMs
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
        
        // Workflow 特定字段
        result.put("workflow_id", workflowId);
        result.put("workflow_name", workflowName);
        result.put("component_id", componentId);
        result.put("component_name", componentName);
        result.put("component_type_str", componentTypeStr);
        result.put("branch_condition", branchCondition);
        result.put("selected_branch", selectedBranch);
        result.put("input_data", inputData);
        result.put("output_data", outputData);
        result.put("execution_time_ms", executionTimeMs);
        
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

