/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User-facing retrieval result.
 */
@Getter
@Setter
public class RetrievalResult {

    private String text;
    private double score;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String docId;
    private String chunkId;

    public RetrievalResult() {
    }

    public RetrievalResult(String text, double score) {
        this(text, score, null, null, null);
    }

    public RetrievalResult(String text, double score, Map<String, Object> metadata, String docId, String chunkId) {
        setText(text);
        setScore(score);
        setMetadata(metadata);
        this.docId = docId;
        this.chunkId = chunkId;
    }

    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "RetrievalResult.text");
        this.text = text;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
