/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/** Result type for streaming upload file operation. */
@NoArgsConstructor
public class UploadFileStreamResult extends BaseResult<UploadFileChunkData> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public UploadFileStreamResult(int code, String message, UploadFileChunkData data) { setCode(code); setMessage(message); setData(data); }
}
