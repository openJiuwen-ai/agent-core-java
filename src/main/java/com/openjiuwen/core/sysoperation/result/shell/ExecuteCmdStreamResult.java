// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.shell;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for execute command stream operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.shell_operation_result.ExecuteCmdStreamResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ExecuteCmdStreamResult extends BaseResult<ExecuteCmdChunkData> {

    public ExecuteCmdStreamResult(int code, String message, ExecuteCmdChunkData data) {
        super(code, message, data);
    }

    public static ExecuteCmdStreamResult success(ExecuteCmdChunkData data) {
        return new ExecuteCmdStreamResult(0, "success", data);
    }

    public static ExecuteCmdStreamResult failure(int code, String message) {
        return new ExecuteCmdStreamResult(code, message, null);
    }
}

