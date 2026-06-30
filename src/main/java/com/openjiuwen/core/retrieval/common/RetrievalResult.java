/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User-facing retrieval result.
 */
public class RetrievalResult {

    private String text;
    private double score;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String docId;
    private String chunkId;

    /**
     * Auto-generated for codecheck compliance.
     */
    public RetrievalResult() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public RetrievalResult(String text, double score) {
        this(text, score, null, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "RetrievalResult.text");
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
    public String getText() {
        return text;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getScore() {
        return score;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setScore(double score) {
        this.score = score;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDocId() {
        return docId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDocId(String docId) {
        this.docId = docId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getChunkId() {
        return chunkId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }
}
