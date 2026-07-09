/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for streaming download file operation.
 * 
 * @since 0.1.7
 */
@SuperBuilder
@NoArgsConstructor
public class DownloadFileStreamResult extends BaseResult<DownloadFileChunkData> {
    /**
     * DownloadFileStreamResult.
     * 
     * @param code code
     * @param message message
     * @param data data
     * @since 0.1.7
     */
    public DownloadFileStreamResult(int code, String message, DownloadFileChunkData data) {
        super(code, message, data);
    }
}
