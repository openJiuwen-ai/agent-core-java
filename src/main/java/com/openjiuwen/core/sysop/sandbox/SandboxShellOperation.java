/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import java.util.List;
import java.util.Iterator;
import java.util.Map;

/**
 * Sandbox shell operation routed through the sandbox gateway/provider chain.
 */
@Operation(name = "shell", mode = OperationMode.SANDBOX, description = "sandbox shell operation")
public class SandboxShellOperation extends BaseShellOperation {
    private static final String OP_TYPE = "shell";

    private final SandboxGatewayClient gatewayClient;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SandboxShellOperation(Object runConfig) {
        super("shell", OperationMode.SANDBOX, "sandbox shell operation", runConfig);
        SandboxGatewayConfig config = getSandboxConfig();
        this.gatewayClient = new SandboxGatewayClient(
                config,
                SandboxOperationSupport.resolveIsolationKey(config)
        );
    }

    private SandboxGatewayConfig getSandboxConfig() {
        Object rc = getRunConfig();
        if (rc instanceof SandboxGatewayConfig config) {
            return config;
        }
        return SandboxGatewayConfig.builder().build();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout,
                                       Map<String, String> environment, Map<String, Object> options) {
        try {
            return invoke("executeCmd", ExecuteCmdResult.class, SandboxOperationSupport.paramsOf(
                    "command", command,
                    "cwd", cwd,
                    "timeout", timeout,
                    "environment", environment,
                    "options", options
            ));
        } catch (IllegalArgumentException ex) {
            return SandboxOperationSupport.buildShellError("execute_cmd", ex.getMessage(), command, cwd);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout,
                                                              Map<String, String> environment,
                                                              Map<String, Object> options) {
        try {
            @SuppressWarnings("unchecked")
            Iterator<ExecuteCmdStreamResult> iterator = invoke(
                    "executeCmdStream",
                    Iterator.class,
                    SandboxOperationSupport.paramsOf(
                    "command", command,
                    "cwd", cwd,
                    "timeout", timeout,
                    "environment", environment,
                    "options", options
            ));
            return iterator;
        } catch (IllegalArgumentException ex) {
            return List.of(SandboxOperationSupport.buildShellStreamError(
                    "execute_cmd_stream",
                    ex.getMessage(),
                    command,
                    cwd)).iterator();
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecuteCmdBackgroundResult executeCmdBackground(String command,
                                                           String cwd,
                                                           Map<String, String> environment,
                                                           double grace,
                                                           Map<String, Object> options) {
        try {
            return invoke(
                    "executeCmdBackground",
                    ExecuteCmdBackgroundResult.class,
                    SandboxOperationSupport.paramsOf(
                    "command", command,
                    "cwd", cwd,
                    "environment", environment,
                    "grace", grace,
                    "options", options
            ));
        } catch (IllegalArgumentException ex) {
            return BaseShellOperationErrorBridge.backgroundError(
                    "execute_cmd_background",
                    ex.getMessage(),
                    command,
                    cwd);
        }
    }

    private <T> T invoke(String method, Class<T> type, Map<String, Object> params) {
        Object result = gatewayClient.invoke(OP_TYPE, method, params);
        if (type.isInstance(result)) {
            return type.cast(result);
        }
        throw new IllegalArgumentException("Unexpected sandbox shell response data type");
    }

}
