/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Result type for streaming read file operation. */
@SuperBuilder
@NoArgsConstructor
public class ReadFileStreamResult extends BaseResult<ReadFileChunkData> {
    public ReadFileStreamResult(int code, String message, ReadFileChunkData data) { super(code, message, data); }
}
