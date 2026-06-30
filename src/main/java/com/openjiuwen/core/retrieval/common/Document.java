/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Document model.
 */
public class Document {

    private String id = UUID.randomUUID().toString();
    private String text;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public Document() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Document(String text) {
        this(null, text, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Document(String id, String text) {
        this(id, text, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Document(String id, String text, Map<String, Object> metadata) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
        setText(text);
        setMetadata(metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "Document.text");
        this.text = text;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getId() {
        return id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setId(String id) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getText() {
        return text;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
