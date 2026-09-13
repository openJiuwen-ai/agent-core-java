/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for streaming read file operation.
 * 
 * @since 0.1.7
 */
@SuperBuilder
@NoArgsConstructor
public class ReadFileStreamResult extends BaseResult<ReadFileChunkData> {
    /**
     * ReadFileStreamResult.
     * 
     * @param code code
     * @param message message
     * @param data data
     * @since 0.1.7
     */
    public ReadFileStreamResult(int code, String message, ReadFileChunkData data) {
        super(code, message, data);
    }
}
