/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code RetrievalResult} in
 * {@code openjiuwen/core/retrieval/common/retrieval_result.py}.
 */
public class RetrievalResult {

    private String text;
    private double score;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String docId;
    private String chunkId;

    public RetrievalResult() {
    }

    public RetrievalResult(String text) {
        this(text, null, null, null, null);
    }

    public RetrievalResult(String text, Double score) {
        this(text, score, null, null, null);
    }

    public RetrievalResult(String text, double score, Map<String, Object> metadata, String docId, String chunkId) {
        this(text, Double.valueOf(score), metadata, docId, chunkId);
    }

    public RetrievalResult(String text, Double score, Map<String, Object> metadata, String docId, String chunkId) {
        if (text == null || score == null) {
            throw new IllegalArgumentException("text and score are required");
        }
        this.text = text;
        this.score = score.doubleValue();
        setMetadata(metadata);
        this.docId = docId;
        this.chunkId = chunkId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public void setScore(Double score) {
        if (score == null) {
            throw new IllegalArgumentException("score is required");
        }
        this.score = score.doubleValue();
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }
}
