/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for write file operation.
 * 
 * @since 0.1.7
 */
@SuperBuilder
@NoArgsConstructor
public class WriteFileResult extends BaseResult<WriteFileData> {
    /**
     * WriteFileResult.
     * 
     * @param code code
     * @param message message
     * @param data data
     * @since 0.1.7
     */
    public WriteFileResult(int code, String message, WriteFileData data) {
        super(code, message, data);
    }
}
