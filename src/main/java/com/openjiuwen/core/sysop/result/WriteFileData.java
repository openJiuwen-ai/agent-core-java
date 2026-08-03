/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Backward-compatible write-file payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code WriteFileData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.WriteFileData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(since = "0.1.14", forRemoval = false)
public class WriteFileData {

    private String path;
    private int size;
    private String mode;
}
