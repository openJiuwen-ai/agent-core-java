/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Result type for list files operation. */
@SuperBuilder
@NoArgsConstructor
public class ListFilesResult extends BaseResult<FileSystemData> {
    public ListFilesResult(int code, String message, FileSystemData data) { super(code, message, data); }
}
