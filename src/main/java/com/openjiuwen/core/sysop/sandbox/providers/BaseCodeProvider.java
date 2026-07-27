/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.providers;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;

import java.util.Iterator;
import java.util.Map;

/**
 * Abstract base class for code execution providers in the sandbox environment.
 * Defines the SPI contract for code execution and streaming execution.
 *
 * @since 2026-01-01
 * @version 1.0
 */
public abstract class BaseCodeProvider {
    /** The sandbox endpoint configuration. */
    protected final SandboxEndpoint endpoint;

    /** The sandbox gateway configuration. */
    protected final SandboxGatewayConfig config;

    /**
     * Constructs a BaseCodeProvider with the given endpoint and config.
     *
     * @param endpoint the sandbox endpoint
     * @param config the sandbox gateway configuration
     */
    protected BaseCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        this.endpoint = endpoint;
        this.config = config;
    }

    /**
     * Executes code in the sandbox and returns the result.
     *
     * @param code the source code to execute
     * @param language the programming language
     * @param timeout the timeout in seconds
     * @param environment the environment variables
     * @param options additional options map
     * @return the code execution result
     */
    public ExecuteCodeResult executeCode(String code, String language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".executeCode is not implemented");
    }

    /**
     * Executes code in the sandbox and returns a stream of result chunks.
     *
     * @param code the source code to execute
     * @param language the programming language
     * @param timeout the timeout in seconds
     * @param environment the environment variables
     * @param options additional options map
     * @return an iterator of code execution stream results
     */
    public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language,
            int timeout, Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".executeCodeStream is not implemented");
    }
}
