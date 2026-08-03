/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * <p>Mirrors Python's {@code CodingMemoryWriteTool} in
 * {@code openjiuwen/harness/tools/coding_memory.py}.</p>
 */
public class CodingMemoryWriteTool extends MemoryOperationTool {
    public CodingMemoryWriteTool(MemoryOperation operation) {
        super("coding_memory_write", "coding_memory_write", "Write coding memory file content.", operation);
    }

    @Override
    protected String validate(Map<String, Object> inputs) {
        String pathError = require(inputs, "path");
        if (pathError != null) {
            return pathError;
        }
        return inputs.get("content") == null ? "content is required" : null;
    }
}
