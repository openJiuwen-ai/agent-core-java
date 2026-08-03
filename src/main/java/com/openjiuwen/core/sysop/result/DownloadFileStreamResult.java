/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/** Result type for streaming download file operation. */
@NoArgsConstructor
public class DownloadFileStreamResult extends BaseResult<DownloadFileChunkData> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public DownloadFileStreamResult(int code, String message, DownloadFileChunkData data) { setCode(code); setMessage(message); setData(data); }
}
