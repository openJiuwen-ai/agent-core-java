/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Result type for streaming download file operation. */
@SuperBuilder
@NoArgsConstructor
public class DownloadFileStreamResult extends BaseResult<DownloadFileChunkData> {
    public DownloadFileStreamResult(int code, String message, DownloadFileChunkData data) { super(code, message, data); }
}
