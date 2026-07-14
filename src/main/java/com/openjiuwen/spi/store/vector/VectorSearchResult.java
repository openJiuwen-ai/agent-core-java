/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of a vector search operation.
 *
 * <p>Mirrors Python's {@code VectorSearchResult} in
 * {@code openjiuwen/core/foundation/store/base_vector_store.py}.</p>
 */
public class VectorSearchResult {

    private double score;
    private Map<String, Object> fields = new LinkedHashMap<>();

    public VectorSearchResult() {
    }

    public VectorSearchResult(double score, Map<String, Object> fields) {
        this.score = score;
        this.fields = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
    }

    static VectorSearchResult fromCore(com.openjiuwen.core.foundation.store.VectorSearchResult result) {
        if (result == null) {
            return null;
        }
        return new VectorSearchResult(result.getScore(), result.getFields());
    }

    com.openjiuwen.core.foundation.store.VectorSearchResult toCore() {
        return new com.openjiuwen.core.foundation.store.VectorSearchResult(score, fields);
    }

    public static Builder builder() {
        return new Builder();
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
    }

    public static class Builder {
        private double score;
        private Map<String, Object> fields = new LinkedHashMap<>();

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder fields(Map<String, Object> fields) {
            this.fields = fields;
            return this;
        }

        public VectorSearchResult build() {
            return new VectorSearchResult(score, fields);
        }
    }
}
