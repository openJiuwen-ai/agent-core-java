/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User-facing retrieval result.
 * 
 * @since 0.1.7
 */
public class RetrievalResult {
    private String text;
    private double score;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String docId;
    private String chunkId;

    /**
     * RetrievalResult.
     * 
     * @since 0.1.7
     */
    public RetrievalResult() {
    }

    /**
     * RetrievalResult.
     * 
     * @param text text
     * @param score score
     * @since 0.1.7
     */
    public RetrievalResult(String text, double score) {
        this(text, score, null, null, null);
    }

    /**
     * RetrievalResult.
     * 
     * @param text text
     * @param score score
     * @param metadata metadata
     * @param docId docId
     * @param chunkId chunkId
     * @since 0.1.7
     */
    public RetrievalResult(String text, double score, Map<String, Object> metadata, String docId, String chunkId) {
        RetrievalValidation.requireNonNull(text, "RetrievalResult.text");
        this.text = text;
        this.score = score;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        this.docId = docId;
        this.chunkId = chunkId;
    }

    /**
     * setText.
     * 
     * @param text text
     * @since 0.1.7
     */
    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "RetrievalResult.text");
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
     * getText.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getText() {
        return text;
    }

    /**
     * getScore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getScore() {
        return score;
    }

    /**
     * setScore.
     * 
     * @param score score
     * @since 0.1.7
     */
    public void setScore(double score) {
        this.score = score;
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

    /**
     * getDocId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDocId() {
        return docId;
    }

    /**
     * setDocId.
     * 
     * @param docId docId
     * @since 0.1.7
     */
    public void setDocId(String docId) {
        this.docId = docId;
    }

    /**
     * getChunkId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getChunkId() {
        return chunkId;
    }

    /**
     * setChunkId.
     * 
     * @param chunkId chunkId
     * @since 0.1.7
     */
    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }
}
