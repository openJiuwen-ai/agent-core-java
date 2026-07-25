/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;

/** Result type for search files operation. */
@NoArgsConstructor
public class SearchFilesResult extends BaseResult<SearchFilesData> {
    /**
     * Auto-generated for codecheck compliance.
     */
    public SearchFilesResult(int code, String message, SearchFilesData data) { setCode(code); setMessage(message); setData(data); }
}
