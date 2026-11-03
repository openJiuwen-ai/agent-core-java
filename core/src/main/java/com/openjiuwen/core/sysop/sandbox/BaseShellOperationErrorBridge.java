/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.result.BaseResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;

import java.util.Map;

/**
 * BaseShellOperationErrorBridge
 *
 * @since 0.1.7
 */
final class BaseShellOperationErrorBridge {
    private BaseShellOperationErrorBridge() {
    }

    static ExecuteCmdBackgroundResult backgroundError(String execution, String errorMsg, String command, String cwd) {
        ExecuteCmdBackgroundData data = ExecuteCmdBackgroundData.builder()
                .command(command)
                .cwd(cwd == null ? "." : cwd)
                .build();
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                Map.of("execution", execution, "error_msg", errorMsg),
                ExecuteCmdBackgroundResult.class,
                data);
    }
}
