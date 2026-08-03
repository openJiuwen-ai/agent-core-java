/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.schema.Param;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent memory configuration.
 *
 * <p>Mirrors Python's {@code AgentMemoryConfig} in
 * {@code openjiuwen/core/memory/config/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentMemoryConfig {

    @JsonProperty("mem_variables")
    private List<Param> memVariables = new ArrayList<>();

    @JsonProperty("enable_long_term_mem")
    private boolean enableLongTermMem = true;

    @JsonProperty("enable_user_profile")
    private boolean enableUserProfile = true;

    @JsonProperty("enable_semantic_memory")
    private boolean enableSemanticMemory = true;

    @JsonProperty("enable_episodic_memory")
    private boolean enableEpisodicMemory = true;

    @JsonProperty("enable_summary_memory")
    private boolean enableSummaryMemory = true;

    public AgentMemoryConfig() {
    }

    public AgentMemoryConfig(
            List<Param> memVariables,
            boolean enableLongTermMem,
            boolean enableUserProfile,
            boolean enableSemanticMemory,
            boolean enableEpisodicMemory,
            boolean enableSummaryMemory) {
        setMemVariables(memVariables);
        this.enableLongTermMem = enableLongTermMem;
        this.enableUserProfile = enableUserProfile;
        this.enableSemanticMemory = enableSemanticMemory;
        this.enableEpisodicMemory = enableEpisodicMemory;
        this.enableSummaryMemory = enableSummaryMemory;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Param> getMemVariables() {
        return memVariables;
    }

    public void setMemVariables(List<Param> memVariables) {
        this.memVariables = memVariables == null ? new ArrayList<>() : new ArrayList<>(memVariables);
    }

    public boolean isEnableLongTermMem() {
        return enableLongTermMem;
    }

    public void setEnableLongTermMem(boolean enableLongTermMem) {
        this.enableLongTermMem = enableLongTermMem;
    }

    public boolean isEnableUserProfile() {
        return enableUserProfile;
    }

    public void setEnableUserProfile(boolean enableUserProfile) {
        this.enableUserProfile = enableUserProfile;
    }

    public boolean isEnableSemanticMemory() {
        return enableSemanticMemory;
    }

    public void setEnableSemanticMemory(boolean enableSemanticMemory) {
        this.enableSemanticMemory = enableSemanticMemory;
    }

    public boolean isEnableEpisodicMemory() {
        return enableEpisodicMemory;
    }

    public void setEnableEpisodicMemory(boolean enableEpisodicMemory) {
        this.enableEpisodicMemory = enableEpisodicMemory;
    }

    public boolean isEnableSummaryMemory() {
        return enableSummaryMemory;
    }

    public void setEnableSummaryMemory(boolean enableSummaryMemory) {
        this.enableSummaryMemory = enableSummaryMemory;
    }

    /**
     * Compatibility helper used by existing translated memory call sites.
     *
     * @return true when any fragment-memory switch is enabled
     */
    public boolean isEnableFragmentMemory() {
        return enableUserProfile || enableSemanticMemory || enableEpisodicMemory;
    }

    public static final class Builder {
        private List<Param> memVariables = new ArrayList<>();
        private boolean enableLongTermMem = true;
        private boolean enableUserProfile = true;
        private boolean enableSemanticMemory = true;
        private boolean enableEpisodicMemory = true;
        private boolean enableSummaryMemory = true;

        private Builder() {
        }

        public Builder memVariables(List<Param> memVariables) {
            this.memVariables = memVariables == null ? new ArrayList<>() : new ArrayList<>(memVariables);
            return this;
        }

        public Builder enableLongTermMem(boolean enableLongTermMem) {
            this.enableLongTermMem = enableLongTermMem;
            return this;
        }

        public Builder enableUserProfile(boolean enableUserProfile) {
            this.enableUserProfile = enableUserProfile;
            return this;
        }

        public Builder enableSemanticMemory(boolean enableSemanticMemory) {
            this.enableSemanticMemory = enableSemanticMemory;
            return this;
        }

        public Builder enableEpisodicMemory(boolean enableEpisodicMemory) {
            this.enableEpisodicMemory = enableEpisodicMemory;
            return this;
        }

        public Builder enableSummaryMemory(boolean enableSummaryMemory) {
            this.enableSummaryMemory = enableSummaryMemory;
            return this;
        }

        public AgentMemoryConfig build() {
            return new AgentMemoryConfig(
                    memVariables,
                    enableLongTermMem,
                    enableUserProfile,
                    enableSemanticMemory,
                    enableEpisodicMemory,
                    enableSummaryMemory
            );
        }
    }
}
