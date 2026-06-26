/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.protocal;

import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Unified code-execution method signatures shared by operation and provider layers.
 *
 * <p>Mirrors Python's {@code BaseCodeProtocal} in
 * {@code openjiuwen/core/sys_operation/protocal/code_protocal.py}.</p>
 */
public abstract class BaseCodeProtocal {

    public static final String DEFAULT_LANGUAGE = "python";

    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public abstract CompletableFuture<ExecuteCodeResult> executeCode(
            String code,
            String language,
            int timeoutSeconds,
            Map<String, String> environment,
            String cwd,
            Map<String, Object> options);

    public abstract Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(
            String code,
            String language,
            int timeoutSeconds,
            Map<String, String> environment,
            String cwd,
            Map<String, Object> options);

    public CompletableFuture<ExecuteCodeResult> executeCode(String code) {
        return executeCode(code, DEFAULT_LANGUAGE, DEFAULT_TIMEOUT_SECONDS, null, null, null);
    }

    public CompletableFuture<ExecuteCodeResult> executeCode(
            String code,
            String language,
            int timeoutSeconds) {
        return executeCode(code, language, timeoutSeconds, null, null, null);
    }

    public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(String code) {
        return executeCodeStream(code, DEFAULT_LANGUAGE, DEFAULT_TIMEOUT_SECONDS, null, null, null);
    }

    public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(
            String code,
            String language,
            int timeoutSeconds) {
        return executeCodeStream(code, language, timeoutSeconds, null, null, null);
    }
}
