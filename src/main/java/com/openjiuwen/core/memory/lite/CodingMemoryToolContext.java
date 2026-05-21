/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Holds state for coding_memory_* tools (node "coding_memory").
 * <p>
 * Mirrors Python's {@code CodingMemoryToolContext} dataclass from
 * {@code core/memory/lite/coding_memory_tool_context.py}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CodingMemoryToolContext extends LiteMemoryToolContextBase {

    private Workspace workspace;
    private Object sysOperation;
    private String codingMemoryDir = "";

    public CodingMemoryToolContext(String codingMemoryDir) {
        this.codingMemoryDir = codingMemoryDir;
        setNodeName("coding_memory");
    }

    public CodingMemoryToolContext(Workspace workspace, Object sysOperation, String codingMemoryDir) {
        this.workspace = workspace;
        this.sysOperation = sysOperation;
        this.codingMemoryDir = codingMemoryDir;
        setNodeName("coding_memory");
    }
}
