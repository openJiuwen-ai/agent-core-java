/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Document model.
 * 
 * @since 0.1.7
 */
public class Document {
    private String id = UUID.randomUUID().toString();
    private String text;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * Document.
     * 
     * @since 0.1.7
     */
    public Document() {
    }

    /**
     * Document.
     * 
     * @param text text
     * @since 0.1.7
     */
    public Document(String text) {
        this(null, text, null);
    }

    /**
     * Document.
     * 
     * @param id id
     * @param text text
     * @since 0.1.7
     */
    public Document(String id, String text) {
        this(id, text, null);
    }

    /**
     * Document.
     * 
     * @param id id
     * @param text text
     * @param metadata metadata
     * @since 0.1.7
     */
    public Document(String id, String text, Map<String, Object> metadata) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
        setText(text);
        setMetadata(metadata);
    }

    /**
     * setText.
     * 
     * @param text text
     * @since 0.1.7
     */
    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "Document.text");
        this.text = text;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    /**
     * getId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getId() {
        return id;
    }

    /**
     * setId.
     * 
     * @param id id
     * @since 0.1.7
     */
    public void setId(String id) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
    }

    /**
     * getText.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getText() {
        return text;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
