/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/** Result type for list directories operation. */
@NoArgsConstructor
public class ListDirsResult extends BaseResult<FileSystemData> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public ListDirsResult(int code, String message, FileSystemData data) { setCode(code); setMessage(message); setData(data); }
}
