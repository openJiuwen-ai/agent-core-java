/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code BaseMessage} in
 * {@code openjiuwen/core/foundation/llm/schema/message.py}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseMessage {

    private String role;

    @Builder.Default
    private Object content = "";

    private String name;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public BaseMessage(String role, Object content) {
        this.role = role;
        this.content = content != null ? content : "";
        this.metadata = new LinkedHashMap<>();
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
