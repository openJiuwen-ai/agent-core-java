/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Common workspace-scoped state for MemoryIndexManager-backed tool surfaces.
 * <p>
 * Mirrors Python's {@code LiteMemoryToolContextBase} dataclass from
 * {@code core/memory/lite/memory_tool_context_base.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiteMemoryToolContextBase {

    private Object workspace;
    private MemorySettings settings;
    private String agentId = "default";
    private Object embeddingConfig;
    private Object sysOperation;
    private Object manager;
    private String nodeName = "memory";

    /**
     * Check if the manager is initialized and not closed.
     */
    public boolean hasActiveManager() {
        if (manager == null) {
            return false;
        }
        try {
            // Check for closed flag via reflection or interface
            var method = manager.getClass().getMethod("isClosed");
            return !((Boolean) method.invoke(manager));
        } catch (Exception e) {
            return manager != null;
        }
    }
}
