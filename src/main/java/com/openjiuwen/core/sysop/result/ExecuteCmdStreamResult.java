/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/**
 * Result type for streaming shell command execution.
 * 
 * @since 0.1.7
 */
@NoArgsConstructor
public class ExecuteCmdStreamResult extends BaseResult<ExecuteCmdChunkData> {
    /**
     * ExecuteCmdStreamResult.
     * 
     * @param code code
     * @param message message
     * @param data data
     * @since 0.1.7
     */
    public ExecuteCmdStreamResult(int code, String message, ExecuteCmdChunkData data) {
        setCode(code); setMessage(message); setData(data);
    }
}
