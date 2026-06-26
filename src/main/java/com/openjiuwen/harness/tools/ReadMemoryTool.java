/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * <p>Mirrors Python's {@code ReadMemoryTool} in
 * {@code openjiuwen/harness/tools/memory.py}.</p>
 */
public class ReadMemoryTool extends MemoryOperationTool {
    public ReadMemoryTool(MemoryOperation operation) {
        super("read_memory", "read_memory", "Read memory file content.", operation);
    }

    @Override
    protected String validate(Map<String, Object> inputs) {
        return require(inputs, "path");
    }
}
