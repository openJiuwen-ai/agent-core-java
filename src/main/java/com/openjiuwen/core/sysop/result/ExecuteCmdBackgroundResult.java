/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/**
 * Result type for background shell command execution.
 */
@NoArgsConstructor
public class ExecuteCmdBackgroundResult extends BaseResult<ExecuteCmdBackgroundData> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecuteCmdBackgroundResult(int code, String message, ExecuteCmdBackgroundData data) {
        setCode(code); setMessage(message); setData(data);
    }
}
