/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Mirrors Python's {@code MemResult} in
 * {@code openjiuwen/core/memory/long_term_memory.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemResult {
    @JsonProperty("mem_info")
    private MemInfo memInfo;

    @JsonProperty("score")
    private double score;

    public MemResult() {
    }

    public MemResult(MemInfo memInfo, double score) {
        this.memInfo = memInfo;
        this.score = score;
    }

    public static Builder builder() {
        return new Builder();
    }

    public MemInfo getMemInfo() {
        return memInfo;
    }

    public void setMemInfo(MemInfo memInfo) {
        this.memInfo = memInfo;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemResult that)) {
            return false;
        }
        return Double.compare(score, that.score) == 0 && Objects.equals(memInfo, that.memInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memInfo, score);
    }

    public static final class Builder {
        private MemInfo memInfo;
        private double score;

        private Builder() {
        }

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
