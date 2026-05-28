/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Result type for read file operation. */
@SuperBuilder
@NoArgsConstructor
public class ReadFileResult extends BaseResult<ReadFileData> {
    public ReadFileResult(int code, String message, ReadFileData data) { super(code, message, data); }

    public static ReadFileResult success(ReadFileData data) {
        return new ReadFileResult(0, "success", data);
    }

    public static ReadFileResult failure(String message) {
        return new ReadFileResult(1, message, null);
    }
}
