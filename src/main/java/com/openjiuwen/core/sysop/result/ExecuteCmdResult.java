/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for shell command execution.
 */
@SuperBuilder
@NoArgsConstructor
public class ExecuteCmdResult extends BaseResult<ExecuteCmdData> {

    public ExecuteCmdResult(int code, String message, ExecuteCmdData data) {
        super(code, message, data);
    }

    public static ExecuteCmdResult success(ExecuteCmdData data) {
        return new ExecuteCmdResult(0, "success", data);
    }

    public static ExecuteCmdResult failure(String message) {
        return new ExecuteCmdResult(1, message, null);
    }
}
