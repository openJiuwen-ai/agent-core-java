/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.schema.Param;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent memory configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryConfig {

    @Builder.Default
    @JsonProperty("mem_variables")
    private List<Param> memVariables = new ArrayList<>();

    @Builder.Default
    @JsonProperty("enable_long_term_mem")
    private boolean enableLongTermMem = true;

    @Builder.Default
    @JsonProperty("enable_fragment_memory")
    private boolean enableFragmentMemory = true;

    @Builder.Default
    @JsonProperty("enable_summary_memory")
    private boolean enableSummaryMemory = true;

    public AgentMemoryConfig(List<Param> memVariables,
                             boolean enableLongTermMem,
                             boolean enableFragmentMemory,
                             boolean enableSummaryMemory) {
        this.memVariables = memVariables == null ? new ArrayList<>() : memVariables;
        this.enableLongTermMem = enableLongTermMem;
        this.enableFragmentMemory = enableFragmentMemory;
        this.enableSummaryMemory = enableSummaryMemory;
    }

    public static AgentMemoryConfigBuilder builder() {
        return new AgentMemoryConfigBuilder();
    }

    public List<Param> getMemVariables() {
        return memVariables;
    }

    public void setMemVariables(List<Param> memVariables) {
        this.memVariables = memVariables;
    }

    public boolean isEnableLongTermMem() {
        return enableLongTermMem;
    }

    public void setEnableLongTermMem(boolean enableLongTermMem) {
        this.enableLongTermMem = enableLongTermMem;
    }

    public boolean isEnableFragmentMemory() {
        return enableFragmentMemory;
    }

    public void setEnableFragmentMemory(boolean enableFragmentMemory) {
        this.enableFragmentMemory = enableFragmentMemory;
    }

    public boolean isEnableSummaryMemory() {
        return enableSummaryMemory;
    }

    public void setEnableSummaryMemory(boolean enableSummaryMemory) {
        this.enableSummaryMemory = enableSummaryMemory;
    }

    public static final class AgentMemoryConfigBuilder {
        private List<Param> memVariables = new ArrayList<>();
        private boolean enableLongTermMem = true;
        private boolean enableFragmentMemory = true;
        private boolean enableSummaryMemory = true;

        public AgentMemoryConfigBuilder memVariables(List<Param> memVariables) {
            this.memVariables = memVariables;
            return this;
        }

        public AgentMemoryConfigBuilder enableLongTermMem(boolean enableLongTermMem) {
            this.enableLongTermMem = enableLongTermMem;
            return this;
        }

        public AgentMemoryConfigBuilder enableFragmentMemory(boolean enableFragmentMemory) {
            this.enableFragmentMemory = enableFragmentMemory;
            return this;
        }

        public AgentMemoryConfigBuilder enableSummaryMemory(boolean enableSummaryMemory) {
            this.enableSummaryMemory = enableSummaryMemory;
            return this;
        }

        public AgentMemoryConfig build() {
            return new AgentMemoryConfig(memVariables, enableLongTermMem, enableFragmentMemory, enableSummaryMemory);
        }
    }
}
