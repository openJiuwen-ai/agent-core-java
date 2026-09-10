/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openjiuwen.core.common.utils.SerializationUtils;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code BaseMessage} in
 * {@code openjiuwen/core/foundation/llm/schema/message.py}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseMessage implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;
    private static final String CONTEXT_MESSAGE_ID_KEY = "context_message_id";

    private String role;

    @Builder.Default
    private Object content = "";

    private String name;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public BaseMessage(String role, Object content) {
        this.role = role;
        this.content = normalizeContent(content, false);
        this.metadata = new LinkedHashMap<>();
    }

    public BaseMessage(String role, String content, String name) {
        this(role, (Object) content);
        this.name = name;
    }

    public BaseMessage(String role, Object content, String name, Map<String, Object> metadata) {
        this.role = role;
        this.content = normalizeContent(content, true);
        this.name = name;
        this.metadata = copyMetadata(metadata);
    }

    public void setContent(Object content) {
        this.content = normalizeContent(content, true);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = copyMetadata(metadata);
    }

    private static Object normalizeContent(Object content, boolean allowNull) {
        if (content == null) {
            return allowNull ? null : "";
        }
        return SerializationUtils.requireSerializable(content, "content");
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null ? null : new LinkedHashMap<>(metadata);
    }

    public Map<String, Object> modelDump() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", getRole());
        result.put("content", content);
        if (name != null) {
            result.put("name", name);
        }
        if (metadata != null && !metadata.isEmpty()) {
            result.put("metadata", metadata);
        }
        return result;
    }

    public Map<String, Object> model_dump() {
        return modelDump();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        BaseMessage that = (BaseMessage) other;
        return Objects.equals(getRole(), that.getRole())
                && Objects.equals(comparisonContent(content), comparisonContent(that.content))
                && Objects.equals(name, that.name)
                && Objects.equals(comparisonMetadata(metadata), comparisonMetadata(that.metadata));
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), getRole(), comparisonContent(content), name, comparisonMetadata(metadata));
    }

    private static Object comparisonContent(Object source) {
        if (!(source instanceof String text)) {
            return source;
        }
        return unwrapResponseEnvelope(text);
    }

    private static String unwrapResponseEnvelope(String text) {
        String prefix = "{\"response\": \"";
        if (!text.startsWith(prefix) || !text.endsWith("\"}")) {
            return text;
        }
        String inner = text.substring(prefix.length(), text.length() - 2);
        return inner.replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static Map<String, Object> comparisonMetadata(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> comparable = new LinkedHashMap<>(source);
        comparable.remove(CONTEXT_MESSAGE_ID_KEY);
        return comparable;
    }

    public String getContentAsString() {
        if (content instanceof String value) {
            return value;
        }
        return content != null ? content.toString() : "";
    }

    @SuppressWarnings("unchecked")
    public List<Object> getContentAsList() {
        if (content instanceof List<?> list) {
            return (List<Object>) list;
        }
        return null;
    }
}
