/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Backward-compatible upload-file chunk payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code UploadFileChunkData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.UploadFileChunkData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(since = "0.1.14", forRemoval = false)
public class UploadFileChunkData {

    private String localPath;
    private String targetPath;
    private int chunkSize;
    private int chunkIndex;
    private boolean isLastChunk;
}
