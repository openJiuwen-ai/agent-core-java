/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Backward-compatible download-file chunk payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code DownloadFileChunkData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.DownloadFileChunkData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(since = "0.1.14", forRemoval = false)
public class DownloadFileChunkData {

    private String sourcePath;
    private String localPath;
    private int chunkSize;
    private int chunkIndex;
    private boolean isLastChunk;
}
