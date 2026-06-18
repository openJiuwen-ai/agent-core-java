/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * <p>Mirrors Python's {@code WriteMemoryTool} in
 * {@code openjiuwen/harness/tools/memory.py}.</p>
 */
public class WriteMemoryTool extends MemoryOperationTool {
    public WriteMemoryTool(MemoryOperation operation) {
        super("write_memory", "WriteMemoryTool", "Write memory file content.", operation);
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
