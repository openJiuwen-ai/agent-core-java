/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory configuration for application agents.
 * <p>
 * Mirrors Python's {@code AgentMemoryConfig} used in ReActAgentConfig.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryConfig {

    @Builder.Default
    private boolean enableLongTermMem = false;

    @Builder.Default
    private boolean enableFragmentMemory = false;

    @Builder.Default
    private boolean enableSummaryMemory = false;

    @Builder.Default
    private List<MemVariable> memVariables = new ArrayList<>();

    /**
     * Memory variable definition.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemVariable {
        private String name;
        private String description;
    }
}
