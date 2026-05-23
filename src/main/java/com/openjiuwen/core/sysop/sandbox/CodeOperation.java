/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.base.BaseOperation;
import com.openjiuwen.core.sysop.base.OperationMode;
import com.openjiuwen.core.sysop.protocal.BaseCodeProtocal;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
import com.openjiuwen.core.sysop.protocal.BaseShellProtocal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sandbox code operation implementation.
 *
 * <p>Mirrors Python's {@code CodeOperation} in
 * {@code openjiuwen.core.sys_operation.sandbox.code_operation}.</p>
 */
public class CodeOperation extends BaseOperation implements BaseCodeProtocal {

    /**
     * Create CodeOperation for sandbox.
     *
     * @param runConfig run configuration
     */
    public CodeOperation(Object runConfig) {
        super("code", OperationMode.SANDBOX, "sandbox code operation", runConfig);
    }

    @Override
    public CompletableFuture<Object> executeCode(
            String code,
            String language,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    ) {
        // Sandbox code execution placeholder
        com.openjiuwen.core.sysop.result.ExecuteCodeData data = com.openjiuwen.core.sysop.result.ExecuteCodeData.builder()
                .codeContent(code)
                .language(language)
                .exitCode(0)
                .stdout("[Sandbox execution simulated]")
                .stderr("")
                .build();

        return CompletableFuture.completedFuture(
                com.openjiuwen.core.sysop.result.ExecuteCodeResult.success(data));
    }

    @Override
    public CompletableFuture<Object> executeCodeStream(
            String code,
            String language,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    ) {
        return executeCode(code, language, timeout, environment, options);
    }

    @Override
    public List<ToolCard> listTools() {
        return List.of();
    }
}