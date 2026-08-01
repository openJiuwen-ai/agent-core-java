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
 * @version 1.0
 * @since 0.1.7
 */
public abstract class BaseCodeProvider {
    /**
     * endpoint.
     * 
     * @since 0.1.7
     */
    protected final SandboxEndpoint endpoint;

    /**
     * config.
     * 
     * @since 0.1.7
     */
    protected final SandboxGatewayConfig config;

    /**
     * Constructs a BaseCodeProvider with the given endpoint and config.
     * 
     * @param endpoint the sandbox endpoint
     * @param config the sandbox gateway configuration
     * @since 0.1.7
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
     * @since 0.1.7
     */
    public ExecuteCodeResult executeCode(String code, String language, int timeout, Map<String, String> environment,
            Map<String, Object> options) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".executeCode is not implemented");
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
     * @since 0.1.7
     */
    public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".executeCodeStream is not implemented");
    }
}
