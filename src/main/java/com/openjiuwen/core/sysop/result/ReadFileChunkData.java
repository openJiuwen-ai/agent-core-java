/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure for chunked file read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadFileChunkData {

    /** File path of the read file. */
    private String path;

    /**
     * Current chunk content.
     * <p>When mode is "text", this is a {@code String}.
     * When mode is "bytes", this is a {@code byte[]} (raw binary content).
     * Mirrors Python's {@code Union[str, bytes]}.
     */
    private Object chunkContent;

    /** File read mode: "text" or "bytes". */
    private String mode;

    /**
     * Get chunk content as String (for text mode).
     */
    public String getChunkContentAsString() {
        if (chunkContent instanceof String s) {
            return s;
        }
        return chunkContent != null ? chunkContent.toString() : null;
    }

    /**
     * Get chunk content as byte[] (for bytes mode).
     */
    public byte[] getChunkContentAsBytes() {
        if (chunkContent instanceof byte[] b) {
            return b;
        }
        if (chunkContent instanceof String s) {
            return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }

    /** Size of each chunk (in bytes). */
    private int chunkSize;

    /** Index of current chunk (starting from 0). */
    private int chunkIndex;

    /** Whether current chunk is the last one. */
    private boolean lastChunk;
}
