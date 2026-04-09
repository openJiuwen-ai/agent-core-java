  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Result type for search files operation. */
@SuperBuilder
@NoArgsConstructor
public class SearchFilesResult extends BaseResult<SearchFilesData> {
    public SearchFilesResult(int code, String message, SearchFilesData data) { super(code, message, data); }
}
