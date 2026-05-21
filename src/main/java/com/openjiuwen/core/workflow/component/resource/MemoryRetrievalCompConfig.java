/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.workflow.component.ComponentConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Configuration for the Memory Retrieval workflow component.
 * <p>
 * Mirrors Python's {@code MemoryRetrievalCompConfig}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MemoryRetrievalCompConfig extends ComponentConfig {

    private LongTermMemory memory;
    private String scopeId;
    private String userId;
    private Double threshold;

    // Default values via builder
    public static class MemoryRetrievalCompConfigBuilder {
        private String scopeId = LongTermMemory.DEFAULT_VALUE;
        private String userId = LongTermMemory.DEFAULT_VALUE;
        private Double threshold = 0.3;
    }
}