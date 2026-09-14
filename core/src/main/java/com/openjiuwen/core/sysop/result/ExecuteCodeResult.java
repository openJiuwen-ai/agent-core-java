/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code ExecuteCodeResult} in
 * {@code openjiuwen/core/sys_operation/result/code_operation_result.py}.
 */
public class ExecuteCodeResult extends BaseResult<ExecuteCodeData> {

    public ExecuteCodeResult() {
    }

    public ExecuteCodeResult(int code, String message, ExecuteCodeData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
