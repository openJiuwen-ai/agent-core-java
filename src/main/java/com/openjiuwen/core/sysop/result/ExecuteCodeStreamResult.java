/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Backward-compatible execute-code stream result for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code ExecuteCodeStreamResult} in
 * {@code openjiuwen/core/sys_operation/result/code_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class ExecuteCodeStreamResult extends BaseResult<ExecuteCodeChunkData> {
}
