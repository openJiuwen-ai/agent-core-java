/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for code execution.
 */
@SuperBuilder
@NoArgsConstructor
public class ExecuteCodeResult extends BaseResult<ExecuteCodeData> {

    public ExecuteCodeResult(int code, String message, ExecuteCodeData data) {
        super(code, message, data);
    }
}
