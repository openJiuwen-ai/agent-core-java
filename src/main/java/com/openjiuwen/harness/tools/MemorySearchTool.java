/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * <p>Mirrors Python's {@code MemorySearchTool} in
 * {@code openjiuwen/harness/tools/memory.py}.</p>
 */
public class MemorySearchTool extends MemoryOperationTool {
    public MemorySearchTool(MemoryOperation operation) {
        super("memory_search", "memory_search", "Search long-term memory.", operation);
    }

    @Override
    protected String validate(Map<String, Object> inputs) {
        return require(inputs, "query");
    }
}
