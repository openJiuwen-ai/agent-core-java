/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code SearchResult} in
 * {@code openjiuwen/core/retrieval/common/retrieval_result.py}.
 */
public class SearchResult {

    private String id;
    private String text;
    private double score;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public SearchResult() {
    }

    public SearchResult(String id, String text, double score, Map<String, Object> metadata) {
        this.id = id;
        this.text = text;
        this.score = score;
        setMetadata(metadata);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
