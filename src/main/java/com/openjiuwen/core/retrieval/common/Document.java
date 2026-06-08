/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code Document} in
 * {@code openjiuwen/core/retrieval/common/document.py}.
 */
public class Document extends com.openjiuwen.core.foundation.store.base_reranker.Document {

    public Document() {
        throw validation("missing_text", "Document.text is required", Map.of("field", "text"));
    }

    public Document(String text) {
        this(null, text, null);
    }

    public Document(String id, String text) {
        this(id, text, null);
    }

    public Document(String id, String text, Map<String, Object> metadata) {
        if (text == null) {
            throw validation("missing_text", "Document.text is required", Map.of("field", "text"));
        }
        setId(id == null || id.isBlank() ? getId() : id);
        setText(text);
        setMetadata(metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata));
    }

    public String getId_() {
        return getId();
    }

    public void setId_(String id) {
        setId(id);
    }

    static ValidationError validation(String errorType, String message, Map<String, Object> context) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reason", errorType + ": " + message);
        params.put("data", context == null ? "" : context.toString());
        return new ValidationError(StatusCode.SCHEMA_VALIDATE_INVALID, errorType + ": " + message, null, null, params);
    }
}
