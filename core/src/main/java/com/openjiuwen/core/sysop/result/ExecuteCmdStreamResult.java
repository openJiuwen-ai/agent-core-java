/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code ExecuteCmdStreamResult} in
 * {@code openjiuwen/core/sys_operation/result/shell_operation_result.py}.
 */
public class ExecuteCmdStreamResult extends BaseResult<ExecuteCmdChunkData> {

    public ExecuteCmdStreamResult() {
    }

    public ExecuteCmdStreamResult(int code, String message, ExecuteCmdChunkData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
