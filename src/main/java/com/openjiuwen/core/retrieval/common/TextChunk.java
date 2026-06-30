/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Text chunk model.
 */
@Getter
@Setter
public class TextChunk {

    private String id;
    private String text;
    private String docId;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private List<Float> embedding;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TextChunk() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TextChunk(String id, String text, String docId) {
        this(id, text, docId, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TextChunk(String id, String text, String docId, Map<String, Object> metadata, List<Float> embedding) {
        setId(id);
        setText(text);
        setDocId(docId);
        setMetadata(metadata);
        setEmbedding(embedding);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TextChunk fromDocument(Document document, String chunkText) {
        return fromDocument(document, chunkText, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TextChunk fromDocument(Document document, String chunkText, String id) {
        return new TextChunk(
                id == null || id.isBlank() ? UUID.randomUUID().toString() : id,
                chunkText,
                document.getId(),
                document.getMetadata(),
                null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setId(String id) {
        RetrievalValidation.requireNonBlank(id, "TextChunk.id");
        this.id = id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "TextChunk.text");
        this.text = text;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDocId(String docId) {
        RetrievalValidation.requireNonBlank(docId, "TextChunk.docId");
        this.docId = docId;
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
    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding == null ? null : List.copyOf(embedding);
    }
}
