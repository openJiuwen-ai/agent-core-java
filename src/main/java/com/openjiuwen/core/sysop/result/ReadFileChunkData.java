/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Backward-compatible read-file chunk payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code ReadFileChunkData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.ReadFileChunkData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(since = "0.1.14", forRemoval = false)
public class ReadFileChunkData {

    private String path;
    private Object chunkContent;
    private String mode;
    private int chunkSize;
    private int chunkIndex;
    private boolean isLastChunk;
}
