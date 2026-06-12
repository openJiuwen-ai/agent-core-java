/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.workflow.component.ComponentConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for the Memory Retrieval workflow component.
 * <p>
 * Mirrors Python's {@code MemoryRetrievalCompConfig}.
 *
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemoryRetrievalCompConfig extends ComponentConfig {
    private LongTermMemory memory;
    private String scopeId = LongTermMemory.DEFAULT_VALUE;
    private String userId = LongTermMemory.DEFAULT_VALUE;
    private double threshold = 0.3;
}
