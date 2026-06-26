/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * <p>Mirrors Python's {@code CodingMemoryEditTool} in
 * {@code openjiuwen/harness/tools/coding_memory.py}.</p>
 */
public class CodingMemoryEditTool extends MemoryOperationTool {
    public CodingMemoryEditTool(MemoryOperation operation) {
        super("coding_memory_edit", "coding_memory_edit", "Edit coding memory file content.", operation);
    }

    @Override
    protected String validate(Map<String, Object> inputs) {
        String pathError = require(inputs, "path");
        if (pathError != null) {
            return pathError;
        }
        if (inputs.get("old_text") == null) {
            return "old_text is required";
        }
        return inputs.get("new_text") == null ? "new_text is required" : null;
    }
}
