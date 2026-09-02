/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import java.util.List;
import java.util.Iterator;
import java.util.Map;

/**
 * Sandbox shell operation routed through the sandbox gateway/provider chain.
 * 
 * @since 0.1.7
 */
@Operation(name = "shell", mode = OperationMode.SANDBOX, description = "sandbox shell operation")
public class SandboxShellOperation extends BaseShellOperation {
    private static final String OP_TYPE = "shell";

    private final SandboxGatewayClient gatewayClient;

    /**
     * SandboxShellOperation.
     * 
     * @param runConfig runConfig
     * @since 0.1.7
     */
    public SandboxShellOperation(Object runConfig) {
        super("shell", OperationMode.SANDBOX, "sandbox shell operation", runConfig);
        this.gatewayClient = new SandboxGatewayClient(getSandboxConfig(),
                SandboxOperationSupport.resolveIsolationKey(getSandboxConfig()));
    }

    /**
     * executeCmd.
     * 
     * @param command command
     * @param cwd cwd
     * @param timeout timeout
     * @param environment environment
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout, Map<String, String> environment,
            Map<String, Object> options) {
        try {
            return invoke("executeCmd", ExecuteCmdResult.class, SandboxOperationSupport.paramsOf("command", command,
                    "cwd", cwd, "timeout", timeout, "environment", environment, "options", options));
        } catch (IllegalArgumentException ex) {
            return SandboxOperationSupport.buildShellError("execute_cmd", ex.getMessage(), command, cwd);
        }
    }

    /**
     * executeCmdStream.
     * 
     * @param command command
     * @param cwd cwd
     * @param timeout timeout
     * @param environment environment
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        try {
            @SuppressWarnings("unchecked")
            Iterator<ExecuteCmdStreamResult> iterator =
                invoke("executeCmdStream", Iterator.class, SandboxOperationSupport.paramsOf("command", command, "cwd",
                        cwd, "timeout", timeout, "environment", environment, "options", options));
            return iterator;
        } catch (IllegalArgumentException ex) {
            return List.of(
                    SandboxOperationSupport.buildShellStreamError("execute_cmd_stream", ex.getMessage(), command, cwd))
                    .iterator();
        }
    }

    /**
     * executeCmdBackground.
     * 
     * @param command command
     * @param cwd cwd
     * @param environment environment
     * @param grace grace
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ExecuteCmdBackgroundResult executeCmdBackground(String command, String cwd, Map<String, String> environment,
            double grace, Map<String, Object> options) {
        try {
            return invoke("executeCmdBackground", ExecuteCmdBackgroundResult.class, SandboxOperationSupport.paramsOf(
                    "command", command, "cwd", cwd, "environment", environment, "grace", grace, "options", options));
        } catch (IllegalArgumentException ex) {
            return BaseShellOperationErrorBridge.backgroundError("execute_cmd_background", ex.getMessage(), command,
                    cwd);
        }
    }

    /**
     * invoke.
     * 
     * @param method method
     * @param type type
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private <T> T invoke(String method, Class<T> type, Map<String, Object> params) {
        Object result = gatewayClient.invoke(OP_TYPE, method, params);
        if (type.isInstance(result)) {
            return type.cast(result);
        }
        throw new IllegalArgumentException("Unexpected sandbox shell response data type");
    }
}
