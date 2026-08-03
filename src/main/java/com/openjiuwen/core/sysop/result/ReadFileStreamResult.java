/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/** Result type for streaming read file operation. */
@NoArgsConstructor
public class ReadFileStreamResult extends BaseResult<ReadFileChunkData> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public ReadFileStreamResult(int code, String message, ReadFileChunkData data) { setCode(code); setMessage(message); setData(data); }
}
