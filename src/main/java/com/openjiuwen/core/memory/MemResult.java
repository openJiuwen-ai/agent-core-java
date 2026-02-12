/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory;

/**
 * Memory search result record.
 * <p>
 * Corresponds to Python: long_term_memory.py - MemResult
 */
public record MemResult(
        MemInfo memInfo,
        double score
) {
    /**
     * Create a MemResult with default values.
     */
    public MemResult() {
        this(null, 0.0);
    }

    /**
     * Create a MemResult with specified values.
     *
     * @param memInfo Memory information
     * @param score   Relevance score
     */
    public MemResult {
        if (memInfo == null) {
            memInfo = new MemInfo();
        }
    }

    /**
     * Builder for MemResult.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemInfo memInfo = new MemInfo();
        private double score = 0.0;

        public Builder memInfo(MemInfo memInfo) {
            this.memInfo = memInfo;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public MemResult build() {
            return new MemResult(memInfo, score);
        }
    }
}

