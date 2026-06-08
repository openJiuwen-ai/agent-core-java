/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors Python's {@code TextChunk} in
 * {@code openjiuwen/core/retrieval/common/document.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextChunk {

    @JsonProperty("id_")
    private String id;
    private String text;
    @JsonProperty("doc_id")
    private String docId;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private List<Double> embedding;

    public TextChunk() {
        throw Document.validation("missing_required_fields", "TextChunk requires id_, text, and doc_id", Map.of());
    }

    public TextChunk(String id, String text, String docId) {
        this(id, text, docId, null, null);
    }

    public TextChunk(String id, String text, String docId, Map<String, Object> metadata) {
        this(id, text, docId, metadata, null);
    }

    public TextChunk(String id, String text, String docId, Map<String, Object> metadata, List<Double> embedding) {
        if (id == null || text == null || docId == null) {
            throw Document.validation("missing_required_fields", "TextChunk requires id_, text, and doc_id", Map.of());
        }
        this.id = id;
        this.text = text;
        this.docId = docId;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        this.embedding = embedding == null ? null : new ArrayList<>(embedding);
    }

    public static TextChunk fromDocument(Document doc, String chunkText) {
        return fromDocument(doc, chunkText, "");
    }

    public static TextChunk fromDocument(Document doc, String chunkText, String id) {
        return new TextChunk(
                id == null || id.isBlank() ? UUID.randomUUID().toString() : id,
                chunkText,
                doc.getId_(),
                doc.getMetadata(),
                null
        );
    }

    public String getId_() {
        return id;
    }

    public void setId_(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDocId() {
        return docId;
    }

    public String getDoc_id() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public void setDoc_id(String docId) {
        this.docId = docId;
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public List<Double> getEmbedding() {
        return embedding == null ? null : new ArrayList<>(embedding);
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding == null ? null : new ArrayList<>(embedding);
    }
}
