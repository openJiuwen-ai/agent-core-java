/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Search result data model.
 * <p>
 * Placeholder implementation for memory module dependency.
 * Will be completed when retrieval module is converted.
 */
public class SearchResult {

    private final String id;
    private final String text;
    private final double score;
    private final Map<String, Object> metadata;

    private SearchResult(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id is required");
        this.text = Objects.requireNonNull(builder.text, "text is required");
        this.score = builder.score;
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public double getScore() {
        return score;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String text;
        private double score;
        private Map<String, Object> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public SearchResult build() {
            return new SearchResult(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchResult that = (SearchResult) o;
        return Double.compare(that.score, score) == 0 &&
               Objects.equals(id, that.id) &&
               Objects.equals(text, that.text) &&
               Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, score, metadata);
    }

    @Override
    public String toString() {
        return "SearchResult{" +
               "id='" + id + '\'' +
               ", text='" + (text.length() > 50 ? text.substring(0, 50) + "..." : text) + '\'' +
               ", score=" + score +
               ", metadata=" + metadata +
               '}';
    }
}

