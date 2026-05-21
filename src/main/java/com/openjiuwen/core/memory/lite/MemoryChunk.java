/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

/**
 * A chunk of memory content.
 * <p>
 * Mirrors Python's {@code MemoryChunk} dataclass from
 * <code>memory/lite/types.py</code>.
 */
public class MemoryChunk {

    private final String text;
    private final int startLine;
    private final int endLine;

    public MemoryChunk(String text, int startLine, int endLine) {
        this.text = text;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String getText() { return text; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }
}
