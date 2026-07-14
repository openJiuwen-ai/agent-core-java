/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/**
 * Backward-compatible shell command result for the legacy sysop package.
 *
 * <p>Mirrors Python's {@code ExecuteCmdResult} in
 * {@code openjiuwen/core/sys_operation/result/shell_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.ExecuteCmdResult}.
 */
@NoArgsConstructor
@Deprecated(since = "0.1.14", forRemoval = false)
public class ExecuteCmdResult extends com.openjiuwen.core.sys_operation.result.BaseResult<ExecuteCmdData> {

    public ExecuteCmdResult(int code, String message, ExecuteCmdData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }

    public static ExecuteCmdResult fromNewResult(
            com.openjiuwen.core.sys_operation.result.ExecuteCmdResult result) {
        if (result == null) {
            return null;
        }
        return new ExecuteCmdResult(
                result.getCode(),
                result.getMessage(),
                ExecuteCmdData.fromNewData(result.getData()));
    }
}
