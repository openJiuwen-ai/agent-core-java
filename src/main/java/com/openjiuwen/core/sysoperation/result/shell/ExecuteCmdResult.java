// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.shell;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for execute command operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.shell_operation_result.ExecuteCmdResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ExecuteCmdResult extends BaseResult<ExecuteCmdData> {

    public ExecuteCmdResult(int code, String message, ExecuteCmdData data) {
        super(code, message, data);
    }

    public static ExecuteCmdResult success(ExecuteCmdData data) {
        return new ExecuteCmdResult(0, "success", data);
    }

    public static ExecuteCmdResult failure(int code, String message) {
        return new ExecuteCmdResult(code, message, null);
    }
}

