/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Guardrail detection context.
 * 
 * This is the contract between ConcreteGuardrail (event binding and
 * data preprocessing) and GuardrailBackend (detection logic).
 * 
 * Compared to AgentCallbackContext:
 * - AgentCallbackContext: Contains full agent context (agent, session, etc.)
 * - GuardrailContext: Focused only on data needed for security detection
 * 
 * Mirrors Python's openjiuwen.core.security.guardrail.context.GuardrailContext
 */
public class GuardrailContext {
    
    // ========== Core: Content to check ==========
    private final GuardrailContentType contentType;
    private final Object content;
    
    // ========== Event information ==========
    private final String event;
    
    // ========== Metadata (populated based on event type) ==========
    private final Map<String, Object> metadata;
    
    /**
     * Creates a new GuardrailContext.
     * 
     * @param contentType Type of the content to check
     * @param content The actual content (type varies based on contentType)
     * @param event Original event name (e.g., "llm_invoke_input")
     * @param metadata Additional metadata specific to the event type
     */
    public GuardrailContext(
            GuardrailContentType contentType,
            Object content,
            String event,
            Map<String, Object> metadata) {
        this.contentType = contentType;
        this.content = content;
        this.event = event;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    /**
     * Creates a new GuardrailContext with empty metadata.
     * 
     * @param contentType Type of the content to check
     * @param content The actual content
     * @param event Original event name
     */
    public GuardrailContext(
            GuardrailContentType contentType,
            Object content,
            String event) {
        this(contentType, content, event, new HashMap<>());
    }
    
    // ========== Getter methods ==========
    
    public GuardrailContentType getContentType() {
        return contentType;
    }
    
    public Object getContent() {
        return content;
    }
    
    public String getEvent() {
        return event;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    // ========== Helper methods ==========
    
    /**
     * Get text content if contentType is TEXT.
     * 
     * @return The text content, or empty if not TEXT type
     */
    public Optional<String> getText() {
        if (contentType == GuardrailContentType.TEXT && content instanceof String) {
            return Optional.of((String) content);
        }
        return Optional.empty();
    }
    
    /**
     * Get message list if contentType is MESSAGES.
     * 
     * @return The message list, or empty if not MESSAGES type
     */
    public Optional<List<?>> getMessages() {
        if (contentType == GuardrailContentType.MESSAGES && content instanceof List) {
            return Optional.of((List<?>) content);
        }
        return Optional.empty();
    }
    
    /**
     * Get tool name from metadata if contentType is TOOL_CALL.
     * 
     * @return The tool name from metadata, or empty if not found
     */
    public Optional<String> getToolName() {
        Object toolName = metadata.get("tool_name");
        if (toolName instanceof String) {
            return Optional.of((String) toolName);
        }
        return Optional.empty();
    }
    
    // ========== Builder pattern ==========
    
    /**
     * Creates a builder for GuardrailContext.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for GuardrailContext.
     */
    public static class Builder {
        private GuardrailContentType contentType;
        private Object content;
        private String event;
        private Map<String, Object> metadata = new HashMap<>();
        
        public Builder contentType(GuardrailContentType contentType) {
            this.contentType = contentType;
            return this;
        }
        
        public Builder content(Object content) {
            this.content = content;
            return this;
        }
        
        public Builder event(String event) {
            this.event = event;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public GuardrailContext build() {
            return new GuardrailContext(contentType, content, event, metadata);
        }
    }
}