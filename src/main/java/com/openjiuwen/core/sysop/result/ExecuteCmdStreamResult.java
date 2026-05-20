/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for streaming shell command execution.
 */
@SuperBuilder
@NoArgsConstructor
public class ExecuteCmdStreamResult extends BaseResult<ExecuteCmdChunkData> {

    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecuteCmdStreamResult(int code, String message, ExecuteCmdChunkData data) {
        super(code, message, data);
    }
}
