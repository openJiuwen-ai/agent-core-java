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
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryConfig {
    @Builder.Default
    @JsonProperty("mem_variables")
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Param> memVariables = new ArrayList<>();

    @Builder.Default
    @JsonProperty("enable_long_term_mem")
    private boolean enableLongTermMem = true;

    @Builder.Default
    @JsonProperty("enable_fragment_memory")
    private boolean enableFragmentMemory = true;

    @Builder.Default
    @JsonProperty("enable_user_profile")
    private boolean enableUserProfile = true;

    @Builder.Default
    @JsonProperty("enable_semantic_memory")
    private boolean enableSemanticMemory = true;

    @Builder.Default
    @JsonProperty("enable_episodic_memory")
    private boolean enableEpisodicMemory = true;

    @Builder.Default
    @JsonProperty("enable_summary_memory")
    private boolean enableSummaryMemory = true;

    /**
     * isMemoryTypeEnabled.
     * 
     * @param memoryType memoryType
     * @return the result
     * @since 0.1.7
     */
    public boolean isMemoryTypeEnabled(String memoryType) {
        if (!enableFragmentMemory) {
            return false;
        }
        return switch (memoryType) {
            case "user_profile" -> enableUserProfile;
            case "semantic_memory" -> enableSemanticMemory;
            case "episodic_memory" -> enableEpisodicMemory;
            default -> true;
        };
    }
}
