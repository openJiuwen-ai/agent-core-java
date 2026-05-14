/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */

package com.openjiuwen.spi.store.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Result of a vector search operation.
 * <p>
 * Mirrors Python's {@code VectorSearchResult}.
 */
@Data
@Builder
@AllArgsConstructor
public class VectorSearchResult {

    /** Relevance score (higher is more relevant). */
    private final double score;

    /** All field values from the matched document. */
    @Builder.Default
    private final Map<String, Object> fields = Map.of();

    public static VectorSearchResultBuilder builder() {
        return new VectorSearchResultBuilder();
    }

    public double getScore() {
        return score;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public static final class VectorSearchResultBuilder {
        private double score;
        private Map<String, Object> fields = Map.of();

        public VectorSearchResultBuilder score(double score) {
            this.score = score;
            return this;
        }

        public VectorSearchResultBuilder fields(Map<String, Object> fields) {
            this.fields = fields;
            return this;
        }

        public VectorSearchResult build() {
            return new VectorSearchResult(score, fields != null ? fields : Map.of());
        }
    }
}
