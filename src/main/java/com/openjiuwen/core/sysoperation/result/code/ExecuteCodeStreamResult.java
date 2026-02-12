// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.code;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for execute code stream operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.code_operation_result.ExecuteCodeStreamResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ExecuteCodeStreamResult extends BaseResult<ExecuteCodeChunkData> {

    public ExecuteCodeStreamResult(int code, String message, ExecuteCodeChunkData data) {
        super(code, message, data);
    }

    public static ExecuteCodeStreamResult success(ExecuteCodeChunkData data) {
        return new ExecuteCodeStreamResult(0, "success", data);
    }

    public static ExecuteCodeStreamResult failure(int code, String message) {
        return new ExecuteCodeStreamResult(code, message, null);
    }
}

