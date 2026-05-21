/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.workflow.component.ComponentConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Configuration for the Memory Write workflow component.
 * <p>
 * Mirrors Python's {@code MemoryWriteCompConfig}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MemoryWriteCompConfig extends ComponentConfig {

    private LongTermMemory memory;
    private String scopeId;
    private String userId;
    private String sessionId;
    private AgentMemoryConfig agentConfig;
    private Boolean genMem;
    private Integer genMemWithHistoryMsgNum;

    // Default values via builder
    public static class MemoryWriteCompConfigBuilder {
        private String scopeId = LongTermMemory.DEFAULT_VALUE;
        private String userId = LongTermMemory.DEFAULT_VALUE;
        private String sessionId = LongTermMemory.DEFAULT_VALUE;
        private AgentMemoryConfig agentConfig = new AgentMemoryConfig();
        private Boolean genMem = true;
        private Integer genMemWithHistoryMsgNum = 2;
    }
}