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
 * 
 * @since 0.1.7
 */
@Getter
@Setter
public class TextChunk {
    private String id;
    private String text;
    private String docId;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private List<Float> embedding;

    /**
     * TextChunk.
     * 
     * @since 0.1.7
     */
    public TextChunk() {
    }

    /**
     * TextChunk.
     * 
     * @param id id
     * @param text text
     * @param docId docId
     * @since 0.1.7
     */
    public TextChunk(String id, String text, String docId) {
        this(id, text, docId, null, null);
    }

    /**
     * TextChunk.
     * 
     * @param id id
     * @param text text
     * @param docId docId
     * @param metadata metadata
     * @param embedding embedding
     * @since 0.1.7
     */
    public TextChunk(String id, String text, String docId, Map<String, Object> metadata, List<Float> embedding) {
        setId(id);
        setText(text);
        setDocId(docId);
        setMetadata(metadata);
        setEmbedding(embedding);
    }

    /**
     * fromDocument.
     * 
     * @param document document
     * @param chunkText chunkText
     * @return the result
     * @since 0.1.7
     */
    public static TextChunk fromDocument(Document document, String chunkText) {
        return fromDocument(document, chunkText, null);
    }

    /**
     * fromDocument.
     * 
     * @param document document
     * @param chunkText chunkText
     * @param id id
     * @return the result
     * @since 0.1.7
     */
    public static TextChunk fromDocument(Document document, String chunkText, String id) {
        return new TextChunk(id == null || id.isBlank() ? UUID.randomUUID().toString() : id, chunkText,
                document.getId(), document.getMetadata(), null);
    }

    /**
     * setId.
     * 
     * @param id id
     * @since 0.1.7
     */
    public void setId(String id) {
        RetrievalValidation.requireNonBlank(id, "TextChunk.id");
        this.id = id;
    }

    /**
     * setText.
     * 
     * @param text text
     * @since 0.1.7
     */
    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "TextChunk.text");
        this.text = text;
    }

    /**
     * setDocId.
     * 
     * @param docId docId
     * @since 0.1.7
     */
    public void setDocId(String docId) {
        RetrievalValidation.requireNonBlank(docId, "TextChunk.docId");
        this.docId = docId;
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
     * setEmbedding.
     * 
     * @param embedding embedding
     * @since 0.1.7
     */
    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding == null ? null : List.copyOf(embedding);
    }
}
