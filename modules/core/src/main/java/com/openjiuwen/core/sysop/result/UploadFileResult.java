/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for upload file operation.
 * 
 * @since 0.1.7
 */
@SuperBuilder
@NoArgsConstructor
public class UploadFileResult extends BaseResult<UploadFileData> {
    /**
     * UploadFileResult.
     * 
     * @param code code
     * @param message message
     * @param data data
     * @since 0.1.7
     */
    public UploadFileResult(int code, String message, UploadFileData data) {
        super(code, message, data);
    }
}
