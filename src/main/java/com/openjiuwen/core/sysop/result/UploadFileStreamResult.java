/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for streaming upload file operation.
 * 
 * @since 0.1.7
 */
@SuperBuilder
@NoArgsConstructor
public class UploadFileStreamResult extends BaseResult<UploadFileChunkData> {
    /**
     * UploadFileStreamResult.
     * 
     * @param code code
     * @param message message
     * @param data data
     * @since 0.1.7
     */
    public UploadFileStreamResult(int code, String message, UploadFileChunkData data) {
        super(code, message, data);
    }
}
