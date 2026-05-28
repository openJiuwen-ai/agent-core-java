/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.protocal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unified Code execution method signatures.
 *
 * <p>Mirrors Python's {@code BaseCodeProtocal} in
 * {@code openjiuwen.core.sys_operation.protocal.code_protocal}.</p>
 */
public interface BaseCodeProtocal {

    /**
     * Execute arbitrary code asynchronously.
     *
     * @param code        source code to execute
     * @param language    programming language (python or javascript)
     * @param timeout     maximum execution time in seconds
     * @param environment custom environment variables
     * @param options     additional execution options
     * @return execution result
     */
    CompletableFuture<Object> executeCode(
            String code,
            String language,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    );

    /**
     * Execute arbitrary code asynchronously by streaming.
     *
     * @param code        source code to execute
     * @param language    programming language (python or javascript)
     * @param timeout     maximum execution time in seconds
     * @param environment custom environment variables
     * @param options     additional execution options
     * @return streaming execution result
     */
    CompletableFuture<Object> executeCodeStream(
            String code,
            String language,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    );
}