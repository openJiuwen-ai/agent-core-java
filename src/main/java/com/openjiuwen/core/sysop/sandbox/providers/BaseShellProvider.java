/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.providers;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;

import java.util.Iterator;
import java.util.Map;

/**
 * Abstract base class for shell command providers in the sandbox environment.
 * Defines the SPI contract for command execution, streaming, and background execution.
 *
 * @since 2026-01-01
 * @version 1.0
 */
public abstract class BaseShellProvider {
    /** The sandbox endpoint configuration. */
    protected final SandboxEndpoint endpoint;

    /** The sandbox gateway configuration. */
    protected final SandboxGatewayConfig config;

    /**
     * Constructs a BaseShellProvider with the given endpoint and config.
     *
     * @param endpoint the sandbox endpoint
     * @param config the sandbox gateway configuration
     */
    protected BaseShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        this.endpoint = endpoint;
        this.config = config;
    }

    /**
     * Executes a command in the sandbox and returns the result.
     *
     * @param command the command to execute
     * @param cwd the working directory for the command
     * @param timeout the timeout in seconds
     * @param environment the environment variables
     * @param options additional options map
     * @return the command execution result
     */
    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".executeCmd is not implemented");
    }

    /**
     * Executes a command in the sandbox and returns a stream of result chunks.
     *
     * @param command the command to execute
     * @param cwd the working directory for the command
     * @param timeout the timeout in seconds
     * @param environment the environment variables
     * @param options additional options map
     * @return an iterator of command execution stream results
     */
    public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd,
            int timeout, Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".executeCmdStream is not implemented");
    }

    /**
     * Executes a command in the sandbox in the background with a grace period.
     *
     * @param command the command to execute
     * @param cwd the working directory for the command
     * @param environment the environment variables
     * @param grace the grace period in seconds before termination
     * @param options additional options map
     * @return the background command execution result
     */
    public ExecuteCmdBackgroundResult executeCmdBackground(String command, String cwd,
            Map<String, String> environment, double grace, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".executeCmdBackground is not implemented");
    }
}
