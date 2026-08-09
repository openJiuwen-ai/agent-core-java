/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code ExecuteCmdBackgroundResult} in
 * {@code openjiuwen/core/sys_operation/result/shell_operation_result.py}.
 */
public class ExecuteCmdBackgroundResult extends BaseResult<ExecuteCmdBackgroundData> {

    public ExecuteCmdBackgroundResult() {
    }

    public ExecuteCmdBackgroundResult(int code, String message, ExecuteCmdBackgroundData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
