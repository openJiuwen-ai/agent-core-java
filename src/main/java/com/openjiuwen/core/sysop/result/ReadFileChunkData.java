/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

    /** Current chunk content. */
    private String chunkContent;

    /** File read mode: "text" or "bytes". */
    private String mode;

    /** Size of each chunk (in bytes). */
    private int chunkSize;

    /** Index of current chunk (starting from 0). */
    private int chunkIndex;

    /** Whether current chunk is the last one. */
    private boolean lastChunk;
}
