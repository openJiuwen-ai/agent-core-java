/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.workflow.component.resource;

import com.openjiuwen.core.application.schema.AgentMemoryConfig;
import com.openjiuwen.core.workflow.component.ComponentConfig;
import com.openjiuwen.memory.LongTermMemory;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for the Memory Write workflow component.
 * <p>
 * Mirrors Python's {@code MemoryWriteCompConfig}.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemoryWriteCompConfig extends ComponentConfig {
    private LongTermMemory memory;
    private String scopeId = LongTermMemory.DEFAULT_VALUE;
    private String userId = LongTermMemory.DEFAULT_VALUE;
    private String sessionId = LongTermMemory.DEFAULT_VALUE;

    /**
     * AgentMemoryConfig.
     * 
     * @since 0.1.7
     */
    private AgentMemoryConfig agentConfig = new AgentMemoryConfig();
    private boolean isGenMem = true;
    private int genMemWithHistoryMsgNum = 2;
}
