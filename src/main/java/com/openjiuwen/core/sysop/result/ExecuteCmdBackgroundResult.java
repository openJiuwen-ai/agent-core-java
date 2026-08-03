/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/**
 * Result type for background shell command execution.
 * 
 * @since 0.1.7
 */
@NoArgsConstructor
public class ExecuteCmdBackgroundResult extends BaseResult<ExecuteCmdBackgroundData> {
    /**
     * ExecuteCmdBackgroundResult.
     * 
     * @param code code
     * @param message message
     * @param data data
     * @since 0.1.7
     */
    public ExecuteCmdBackgroundResult(int code, String message, ExecuteCmdBackgroundData data) {
        setCode(code); setMessage(message); setData(data);
    }
}
