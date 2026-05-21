/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parameters for creating or retrieving a MemoryIndexManager instance.
 * <p>
 * Mirrors Python's {@code MemoryManagerParams} dataclass from
 * {@code core/memory/lite/manager.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryManagerParams {

    @Builder.Default
    private String agentId = "default";

    /**
     * Workspace reference. Stored as Object to avoid tight coupling
     * with harness.workspace.Workspace which may not be loaded in all contexts.
     */
    private Object workspace;

    private MemorySettings settings;

    /**
     * Embedding configuration. Stored as Object to avoid tight coupling.
     */
    private Object embeddingConfig;

    /**
     * System operation reference. Stored as Object to avoid tight coupling.
     */
    private Object sysOperation;

    @Builder.Default
    private String nodeName = "memory";
}
