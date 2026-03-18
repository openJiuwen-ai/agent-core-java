/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Result type for download file operation. */
@SuperBuilder
@NoArgsConstructor
public class DownloadFileResult extends BaseResult<DownloadFileData> {
    public DownloadFileResult(int code, String message, DownloadFileData data) { super(code, message, data); }
}
