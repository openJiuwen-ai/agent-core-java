/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for shell command execution.
 * 
 * @since 0.1.7
 */
@SuperBuilder
@NoArgsConstructor
public class ExecuteCmdResult extends BaseResult<ExecuteCmdData> {
    /**
     * ExecuteCmdResult.
     * 
     * @param code code
     * @param message message
     * @param data data
     * @since 0.1.7
     */
    public ExecuteCmdResult(int code, String message, ExecuteCmdData data) {
        super(code, message, data);
    }
}
