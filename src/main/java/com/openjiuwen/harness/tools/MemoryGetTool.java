/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * <p>Mirrors Python's {@code MemoryGetTool} in
 * {@code openjiuwen/harness/tools/memory.py}.</p>
 */
public class MemoryGetTool extends MemoryOperationTool {
    public MemoryGetTool(MemoryOperation operation) {
        super("memory_get", "memory_get", "Read indexed memory by path.", operation);
    }

    @Override
    protected String validate(Map<String, Object> inputs) {
        return require(inputs, "path");
    }
}
