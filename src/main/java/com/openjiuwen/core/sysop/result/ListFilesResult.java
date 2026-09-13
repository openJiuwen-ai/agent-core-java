/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for list files operation.
 * 
 * @since 0.1.7
 */
@SuperBuilder
@NoArgsConstructor
public class ListFilesResult extends BaseResult<FileSystemData> {
    /**
     * ListFilesResult.
     * 
     * @param code code
     * @param message message
     * @param data data
     * @since 0.1.7
     */
    public ListFilesResult(int code, String message, FileSystemData data) {
        super(code, message, data);
    }
}
