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
 * 
 * @since 0.1.7
 */
@Getter
@Setter
public class SearchResult {
    private String id;
    private String text;
    private double score;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * SearchResult.
     * 
     * @since 0.1.7
     */
    public SearchResult() {
    }

    /**
     * SearchResult.
     * 
     * @param id id
     * @param text text
     * @param score score
     * @since 0.1.7
     */
    public SearchResult(String id, String text, double score) {
        this(id, text, score, null);
    }

    /**
     * SearchResult.
     * 
     * @param id id
     * @param text text
     * @param score score
     * @param metadata metadata
     * @since 0.1.7
     */
    public SearchResult(String id, String text, double score, Map<String, Object> metadata) {
        setId(id);
        setText(text);
        setScore(score);
        setMetadata(metadata);
    }

    /**
     * setId.
     * 
     * @param id id
     * @since 0.1.7
     */
    public void setId(String id) {
        RetrievalValidation.requireNonBlank(id, "SearchResult.id");
        this.id = id;
    }

    /**
     * setText.
     * 
     * @param text text
     * @since 0.1.7
     */
    public void setText(String text) {
        RetrievalValidation.requireNonNull(text, "SearchResult.text");
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
}
