/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.protocal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unified Shell method signatures.
 *
 * <p>Mirrors Python's {@code BaseShellProtocal} in
 * {@code openjiuwen.core.sys_operation.protocal.shell_protocal}.</p>
 */
public interface BaseShellProtocal {

    /**
     * Execute shell command asynchronously.
     *
     * @param command     shell command to execute
     * @param cwd         current working directory
     * @param timeout     maximum execution time in seconds
     * @param environment custom environment variables
     * @param options     additional execution options
     * @return execution result
     */
    CompletableFuture<Object> executeCmd(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    );

    /**
     * Execute shell command asynchronously by streaming.
     *
     * @param command     shell command to execute
     * @param cwd         current working directory
     * @param timeout     maximum execution time in seconds
     * @param environment custom environment variables
     * @param options     additional execution options
     * @return streaming execution result
     */
    CompletableFuture<Object> executeCmdStream(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    );
}