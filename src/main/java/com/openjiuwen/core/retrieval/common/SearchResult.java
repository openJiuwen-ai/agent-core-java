/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw search result.
 */
@Getter
@Setter
public class SearchResult {

    private String id;
    private String text;
    private double score;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public SearchResult() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SearchResult(String id, String text, double score) {
        this(id, text, score, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SearchResult(String id, String text, double score, Map<String, Object> metadata) {
        setId(id);
        setText(text);
        setScore(score);
        setMetadata(metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setId(String id) {
        RetrievalValidation.requireNonBlank(id, "SearchResult.id");
        this.id = id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "SearchResult.text");
        this.text = text;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
