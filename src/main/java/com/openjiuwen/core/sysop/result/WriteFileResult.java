/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/** Result type for write file operation. */
@NoArgsConstructor
public class WriteFileResult extends BaseResult<WriteFileData> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public WriteFileResult(int code, String message, WriteFileData data) { setCode(code); setMessage(message); setData(data); }
}
