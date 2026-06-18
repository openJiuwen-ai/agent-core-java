/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * <p>Mirrors Python's {@code CodingMemoryReadTool} in
 * {@code openjiuwen/harness/tools/coding_memory.py}.</p>
 */
public class CodingMemoryReadTool extends MemoryOperationTool {
    public CodingMemoryReadTool(MemoryOperation operation) {
        super("coding_memory_read", "CodingMemoryReadTool", "Read coding memory file content.", operation);
    }

    @Override
    protected String validate(Map<String, Object> inputs) {
        return require(inputs, "path");
    }
}
