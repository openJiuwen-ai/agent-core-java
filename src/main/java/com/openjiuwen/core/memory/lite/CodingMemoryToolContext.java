/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Holds state for coding_memory tools on the coding_memory node.
 *
 * <p>Mirrors Python's {@code CodingMemoryToolContext} in
 * {@code openjiuwen/core/memory/lite/coding_memory_tool_context.py}.</p>
 */
public class CodingMemoryToolContext extends LiteMemoryToolContextBase {

    public static final String DEFAULT_NODE_NAME = "coding_memory";

    @JsonProperty("coding_memory_dir")
    private String codingMemoryDir = "";

    public CodingMemoryToolContext() {
        setNodeName(DEFAULT_NODE_NAME);
    }

    public CodingMemoryToolContext(String codingMemoryDir) {
        this();
        this.codingMemoryDir = codingMemoryDir;
    }

    public CodingMemoryToolContext(String codingMemoryDir, String nodeName) {
        this.codingMemoryDir = codingMemoryDir;
        setNodeName(nodeName);
    }

    public String getCodingMemoryDir() {
        return codingMemoryDir;
    }

    public void setCodingMemoryDir(String codingMemoryDir) {
        this.codingMemoryDir = codingMemoryDir;
    }
}
