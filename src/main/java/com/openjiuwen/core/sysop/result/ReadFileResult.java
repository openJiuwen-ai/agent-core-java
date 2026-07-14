/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Backward-compatible read-file result for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code ReadFileResult} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.ReadFileResult}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class ReadFileResult extends BaseResult<ReadFileData> {
}
