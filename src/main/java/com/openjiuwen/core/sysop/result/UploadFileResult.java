  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Result type for upload file operation. */
@SuperBuilder
@NoArgsConstructor
public class UploadFileResult extends BaseResult<UploadFileData> {
    public UploadFileResult(int code, String message, UploadFileData data) { super(code, message, data); }
}
