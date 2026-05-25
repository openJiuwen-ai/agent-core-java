/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for background shell command execution.
 * <p>
 * Mirrors Python's {@code ExecuteCmdBackgroundResult} from
 * <code>openjiuwen/core/sys_operation/result.py</code>.
 */
@SuperBuilder
@NoArgsConstructor
public class ExecuteCmdBackgroundResult extends BaseResult<ExecuteCmdBackgroundData> {

    public ExecuteCmdBackgroundResult(int code, String message, ExecuteCmdBackgroundData data) {
        super(code, message, data);
    }

    public static ExecuteCmdBackgroundResult success(ExecuteCmdBackgroundData data) {
        return new ExecuteCmdBackgroundResult(0, "success", data);
    }

    public static ExecuteCmdBackgroundResult failure(String message) {
        return new ExecuteCmdBackgroundResult(1, message, null);
    }
}
