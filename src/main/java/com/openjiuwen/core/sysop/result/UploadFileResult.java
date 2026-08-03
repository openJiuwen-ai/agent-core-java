/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/** Result type for upload file operation. */
@NoArgsConstructor
public class UploadFileResult extends BaseResult<UploadFileData> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public UploadFileResult(int code, String message, UploadFileData data) { setCode(code); setMessage(message); setData(data); }
}
