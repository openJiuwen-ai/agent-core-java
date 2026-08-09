/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code ExecuteCmdResult} in
 * {@code openjiuwen/core/sys_operation/result/shell_operation_result.py}.
 */
public class ExecuteCmdResult extends BaseResult<ExecuteCmdData> {

    public ExecuteCmdResult() {
    }

    public ExecuteCmdResult(int code, String message, ExecuteCmdData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
