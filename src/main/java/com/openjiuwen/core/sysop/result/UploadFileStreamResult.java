/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Result type for streaming upload file operation. */
@SuperBuilder
@NoArgsConstructor
public class UploadFileStreamResult extends BaseResult<UploadFileChunkData> {
    public UploadFileStreamResult(int code, String message, UploadFileChunkData data) { super(code, message, data); }
}
