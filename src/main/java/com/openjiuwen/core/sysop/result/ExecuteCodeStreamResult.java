  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for streaming code execution.
 */
@SuperBuilder
@NoArgsConstructor
public class ExecuteCodeStreamResult extends BaseResult<ExecuteCodeChunkData> {

    public ExecuteCodeStreamResult(int code, String message, ExecuteCodeChunkData data) {
        super(code, message, data);
    }
}
