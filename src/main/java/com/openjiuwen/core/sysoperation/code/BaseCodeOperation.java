// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.code;

import com.openjiuwen.core.sysoperation.base.BaseOperation;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.result.Language;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeResult;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeStreamResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Base code execution operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.code.BaseCodeOperation
 * 
 * <p>Defines the abstract interface for code execution operations:
 * <ul>
 *   <li>executeCode - Execute code and return result</li>
 *   <li>executeCodeStream - Execute code with streaming output</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public abstract class BaseCodeOperation extends BaseOperation {

    /**
     * Default timeout in seconds (5 minutes).
     */
    public static final int DEFAULT_TIMEOUT = 300;

    /**
     * Constructs a BaseCodeOperation.
     */
    public BaseCodeOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    /**
     * Execute arbitrary code asynchronously.
     * 
     * @param code Non-empty string containing the source code to execute (required)
     * @param language Programming language (PYTHON or JAVASCRIPT, default: PYTHON)
     * @param timeout Maximum execution time in seconds (default: 300)
     * @param environment Custom environment variables
     * @param options Additional execution configuration options
     * @return CompletableFuture containing the execution result
     */
    public abstract CompletableFuture<ExecuteCodeResult> executeCode(
        String code,
        Language language,
        int timeout,
        Map<String, String> environment,
        Map<String, Object> options
    );

    /**
     * Default executeCode with Python and default timeout.
     */
    public CompletableFuture<ExecuteCodeResult> executeCode(String code) {
        return executeCode(code, Language.PYTHON, DEFAULT_TIMEOUT, null, null);
    }

    /**
     * Execute code with specified language.
     */
    public CompletableFuture<ExecuteCodeResult> executeCode(String code, Language language) {
        return executeCode(code, language, DEFAULT_TIMEOUT, null, null);
    }

    /**
     * Execute arbitrary code asynchronously with streaming output.
     * 
     * @param code Non-empty string containing the source code to execute (required)
     * @param language Programming language (PYTHON or JAVASCRIPT, default: PYTHON)
     * @param timeout Maximum execution time in seconds (default: 300)
     * @param environment Custom environment variables
     * @param options Additional execution configuration options
     * @return Stream of execution chunk results
     */
    public abstract Stream<ExecuteCodeStreamResult> executeCodeStream(
        String code,
        Language language,
        int timeout,
        Map<String, String> environment,
        Map<String, Object> options
    );

    /**
     * Default executeCodeStream with Python and default timeout.
     */
    public Stream<ExecuteCodeStreamResult> executeCodeStream(String code) {
        return executeCodeStream(code, Language.PYTHON, DEFAULT_TIMEOUT, null, null);
    }
}

