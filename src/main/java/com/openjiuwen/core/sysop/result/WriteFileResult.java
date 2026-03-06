/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Result type for write file operation. */
@SuperBuilder
@NoArgsConstructor
public class WriteFileResult extends BaseResult<WriteFileData> {
    public WriteFileResult(int code, String message, WriteFileData data) { super(code, message, data); }
}
