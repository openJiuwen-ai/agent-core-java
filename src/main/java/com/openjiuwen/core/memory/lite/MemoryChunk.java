/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

/**
 * A chunk of memory content.
 */
public record MemoryChunk(String text, int startLine, int endLine) {
}
