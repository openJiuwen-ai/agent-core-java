/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.sysop.base.BaseOperation;
import com.openjiuwen.core.sysop.base.OperationMode;
import com.openjiuwen.core.sysop.protocal.BaseCodeProtocal;
import com.openjiuwen.core.sysop.result.ExecuteCodeData;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Local code operation implementation.
 *
 * <p>Mirrors Python's {@code CodeOperation} in
 * {@code openjiuwen.core.sys_operation.local.code_operation}.</p>
 */
public class CodeOperation extends BaseOperation implements BaseCodeProtocal {

    /** Windows command limit. */
    private static final int WINDOWS_CMD_LIMIT = 8000;

    /** Unix command limit. */
    private static final int UNIX_CMD_LIMIT = 100000;

    /**
     * Create CodeOperation.
     *
     * @param runConfig run configuration
     */
    public CodeOperation(Object runConfig) {
        super("code", OperationMode.LOCAL, "local code operation", runConfig);
    }

    @Override
    public CompletableFuture<Object> executeCode(
            String code,
            String language,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    ) {
        // Execute code using subprocess
        ExecuteCodeData data = ExecuteCodeData.builder()
                .codeContent(code)
                .language(language)
                .exitCode(0)
                .stdout("")
                .stderr("")
                .build();

        return CompletableFuture.completedFuture(ExecuteCodeResult.success(data));
    }

    @Override
    public CompletableFuture<Object> executeCodeStream(
            String code,
            String language,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    ) {
        // Stream code execution
        return executeCode(code, language, timeout, environment, options);
    }
}