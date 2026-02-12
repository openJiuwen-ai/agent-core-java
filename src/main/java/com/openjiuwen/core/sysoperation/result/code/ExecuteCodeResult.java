// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.code;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for execute code operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.code_operation_result.ExecuteCodeResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ExecuteCodeResult extends BaseResult<ExecuteCodeData> {

    public ExecuteCodeResult(int code, String message, ExecuteCodeData data) {
        super(code, message, data);
    }

    public static ExecuteCodeResult success(ExecuteCodeData data) {
        return new ExecuteCodeResult(0, "success", data);
    }

    public static ExecuteCodeResult failure(int code, String message) {
        return new ExecuteCodeResult(code, message, null);
    }
}

