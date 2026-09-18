/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code ExecuteCodeStreamResult} in
 * {@code openjiuwen/core/sys_operation/result/code_operation_result.py}.
 */
public class ExecuteCodeStreamResult extends BaseResult<ExecuteCodeChunkData> {

    public ExecuteCodeStreamResult() {
    }

    public ExecuteCodeStreamResult(int code, String message, ExecuteCodeChunkData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
