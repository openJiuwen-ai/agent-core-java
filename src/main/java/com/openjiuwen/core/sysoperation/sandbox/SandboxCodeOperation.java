// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.sandbox;

import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.code.BaseCodeOperation;
import com.openjiuwen.core.sysoperation.registry.Operation;
import com.openjiuwen.core.sysoperation.registry.OperationRegistry;
import com.openjiuwen.core.sysoperation.result.Language;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeResult;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeStreamResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Sandbox code execution operation placeholder.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.sandbox.code_operation.CodeOperation
 * 
 * <p>Note: This is a placeholder implementation. Sandbox mode operations
 * are not yet implemented in the Java version.
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@Operation(name = "code", mode = OperationMode.SANDBOX, description = "sandbox code operation")
public class SandboxCodeOperation extends BaseCodeOperation {

    static {
        OperationRegistry.register(SandboxCodeOperation.class, "code", OperationMode.SANDBOX, "sandbox code operation");
    }

    public SandboxCodeOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public CompletableFuture<ExecuteCodeResult> executeCode(
            String code, Language language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public Stream<ExecuteCodeStreamResult> executeCodeStream(
            String code, Language language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }
}

