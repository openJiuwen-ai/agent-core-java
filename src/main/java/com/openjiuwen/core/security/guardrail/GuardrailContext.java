/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Guardrail detection context.
 * <p>
 * Mirrors Python's {@code GuardrailContext} in
 * {@code openjiuwen/core/security/guardrail/context.py}.
 */
public final class GuardrailContext {

    private final GuardrailContentType contentType;
    private final Object content;
    private final String event;
    private final Map<String, Object> metadata;

    public GuardrailContext(
            GuardrailContentType contentType,
            Object content,
            String event,
            Map<String, Object> metadata) {
        this.contentType = contentType;
        this.content = content;
        this.event = event;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public GuardrailContext(GuardrailContentType contentType, Object content, String event) {
        this(contentType, content, event, null);
    }

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
        return new LinkedHashMap<>(metadata);
    }

    public Optional<String> getText() {
        if (contentType == GuardrailContentType.TEXT && content instanceof String text) {
            return Optional.of(text);
        }
        return Optional.empty();
    }

    public Optional<List<?>> getMessages() {
        if (contentType == GuardrailContentType.MESSAGES && content instanceof List<?> messages) {
            return Optional.of(messages);
        }
        return Optional.empty();
    }

    public Optional<String> getToolName() {
        Object value = metadata.get("tool_name");
        return value instanceof String toolName ? Optional.of(toolName) : Optional.empty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private GuardrailContentType contentType;
        private Object content;
        private String event;
        private Map<String, Object> metadata = new LinkedHashMap<>();

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
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
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
