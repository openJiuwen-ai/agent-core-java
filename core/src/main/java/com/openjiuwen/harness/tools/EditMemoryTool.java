/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * <p>Mirrors Python's {@code EditMemoryTool} in
 * {@code openjiuwen/harness/tools/memory.py}.</p>
 */
public class EditMemoryTool extends MemoryOperationTool {
    public EditMemoryTool(MemoryOperation operation) {
        super("edit_memory", "edit_memory", "Edit memory file content.", operation);
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
