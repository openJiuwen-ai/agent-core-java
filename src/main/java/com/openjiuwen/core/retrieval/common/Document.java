/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Document model.
 */
@Getter
@Setter
public class Document {

    private String id = UUID.randomUUID().toString();
    private String text;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public Document() {
    }

    public Document(String text) {
        this(null, text, null);
    }

    public Document(String id, String text) {
        this(id, text, null);
    }

    public Document(String id, String text, Map<String, Object> metadata) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
        setText(text);
        setMetadata(metadata);
    }

    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "Document.text");
        this.text = text;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
