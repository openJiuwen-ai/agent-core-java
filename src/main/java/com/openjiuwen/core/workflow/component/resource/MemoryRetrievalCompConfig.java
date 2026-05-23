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

/**
 * Configuration for the Memory Retrieval workflow component.
 * <p>
 * Mirrors Python's {@code MemoryRetrievalCompConfig}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MemoryRetrievalCompConfig extends ComponentConfig {

    private LongTermMemory memory;
    private String scopeId = LongTermMemory.DEFAULT_VALUE;
    private String userId = LongTermMemory.DEFAULT_VALUE;
    private Double threshold = 0.3;

    public static MemoryRetrievalCompConfigBuilder builder() {
        return new MemoryRetrievalCompConfigBuilder();
    }

    public static class MemoryRetrievalCompConfigBuilder {
        private LongTermMemory memory;
        private String scopeId = LongTermMemory.DEFAULT_VALUE;
        private String userId = LongTermMemory.DEFAULT_VALUE;
        private Double threshold = 0.3;

        public MemoryRetrievalCompConfigBuilder memory(LongTermMemory memory) { this.memory = memory; return this; }
        public MemoryRetrievalCompConfigBuilder scopeId(String scopeId) { this.scopeId = scopeId; return this; }
        public MemoryRetrievalCompConfigBuilder userId(String userId) { this.userId = userId; return this; }
        public MemoryRetrievalCompConfigBuilder threshold(Double threshold) { this.threshold = threshold; return this; }

        public MemoryRetrievalCompConfig build() {
            MemoryRetrievalCompConfig config = new MemoryRetrievalCompConfig();
            config.setMemory(memory);
            config.setScopeId(scopeId);
            config.setUserId(userId);
            config.setThreshold(threshold);
            return config;
        }
    }
}