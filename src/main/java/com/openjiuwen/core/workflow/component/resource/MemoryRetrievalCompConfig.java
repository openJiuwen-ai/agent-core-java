/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.workflow.component.ComponentConfig;

/**
 * Configuration for the long-term memory retrieval workflow component.
 *
 * <p>Mirrors Python's {@code MemoryRetrievalCompConfig} in
 * {@code openjiuwen/core/workflow/components/resource/memory_retrieval_comp.py}.</p>
 */
public class MemoryRetrievalCompConfig extends ComponentConfig {

    @JsonProperty("memory")
    private LongTermMemory memory;

    @JsonProperty("scope_id")
    private String scopeId = LongTermMemory.DEFAULT_VALUE;

    @JsonProperty("user_id")
    private String userId = LongTermMemory.DEFAULT_VALUE;

    @JsonProperty("threshold")
    private double threshold = 0.3d;

    public MemoryRetrievalCompConfig() {
    }

    public MemoryRetrievalCompConfig(LongTermMemory memory) {
        this.memory = memory;
    }

    public MemoryRetrievalCompConfig(LongTermMemory memory, String scopeId, String userId, double threshold) {
        this.memory = memory;
        setScopeId(scopeId);
        setUserId(userId);
        this.threshold = threshold;
    }

    public MemoryRetrievalCompConfig(LongTermMemory memory, String scopeId, String userId, Double threshold) {
        this(memory, scopeId, userId, threshold == null ? 0.3d : threshold);
    }

    public static Builder builder() {
        return new Builder();
    }

    public LongTermMemory getMemory() {
        return memory;
    }

    public void setMemory(LongTermMemory memory) {
        this.memory = memory;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId == null ? LongTermMemory.DEFAULT_VALUE : scopeId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId == null ? LongTermMemory.DEFAULT_VALUE : userId;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold == null ? 0.3d : threshold;
    }

    /**
     * Builder mirroring Python keyword-only construction.
     */
    public static final class Builder {
        private LongTermMemory memory;
        private String scopeId = LongTermMemory.DEFAULT_VALUE;
        private String userId = LongTermMemory.DEFAULT_VALUE;
        private double threshold = 0.3d;

        private Builder() {
        }

        public Builder memory(LongTermMemory memory) {
            this.memory = memory;
            return this;
        }

        public Builder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder threshold(Double threshold) {
            this.threshold = threshold == null ? 0.3d : threshold;
            return this;
        }

        public MemoryRetrievalCompConfig build() {
            return new MemoryRetrievalCompConfig(memory, scopeId, userId, threshold);
        }
    }
}
