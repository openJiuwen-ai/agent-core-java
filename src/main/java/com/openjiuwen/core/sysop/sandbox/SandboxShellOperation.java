/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.ShellType;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sysop.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sandbox mode shell operation.
 * <p>
 * Executes shell commands in a sandbox environment via gateway routing.
 * <p>
 * Mirrors Python's {@code ShellOperation} in {@code sandbox/shell_operation.py}.
 */
@Operation(name = "shell", mode = OperationMode.SANDBOX, description = "sandbox shell operation")
public class SandboxShellOperation extends BaseShellOperation {

    private final BaseSandboxMixin sandboxMixin;
    private final String sessionId;
    private final boolean sandboxContextInitialized;

    /**
     * Create a SandboxShellOperation with default configuration.
     *
     * @param runConfig sandbox run configuration
     */
    public SandboxShellOperation(Object runConfig) {
        this(runConfig, "default_session");
    }

    /**
     * Create a SandboxShellOperation with session ID.
     *
     * @param runConfig sandbox run configuration
     * @param sessionId session ID for isolation key resolution
     */
    public SandboxShellOperation(Object runConfig, String sessionId) {
        super("shell", OperationMode.SANDBOX, "sandbox shell operation", runConfig);
        this.sessionId = sessionId;
        this.sandboxMixin = new BaseSandboxMixin();
        if (runConfig instanceof SandboxRunConfig) {
            this.sandboxMixin.initSandboxContext((SandboxRunConfig) runConfig, "shell");
            this.sandboxContextInitialized = true;
        } else {
            this.sandboxContextInitialized = false;
        }
    }

    @Override
    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout,
                                        Map<String, String> environment, Map<String, Object> options) {
        requireSandboxContext();
        Map<String, Object> params = buildExecuteParams(command, cwd, timeout, environment, options, ShellType.AUTO);
        Object raw = sandboxMixin.invoke(sessionId, "execute_cmd", params);
        return convertToExecuteCmdResult(raw);
    }

    @Override
    public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout,
                                                              Map<String, String> environment, Map<String, Object> options) {
        requireSandboxContext();
        Map<String, Object> params = buildExecuteParams(command, cwd, timeout, environment, options, ShellType.AUTO);
        try {
            Iterator<?> rawIterator = sandboxMixin.invokeStream(sessionId, "execute_cmd_stream", params);
            return convertStreamIterator(rawIterator);
        } catch (Exception e) {
            throw new RuntimeException("execute_cmd_stream failed", e);
        }
    }

    @Override
    public ExecuteCmdBackgroundResult executeCmdBackground(String command, String cwd, String shellType) {
        // TODO: Implement background execution via sandbox
        return ExecuteCmdBackgroundResult.failure("Background execution not yet implemented for sandbox");
    }

    /**
     * Execute a shell command with explicit shell type.
     *
     * @param command    command to execute
     * @param cwd        working directory
     * @param timeout    timeout in seconds
     * @param environment environment variables
     * @param options    additional options
     * @param shellType  shell type to use
     * @return execution result
     */
    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout,
                                        Map<String, String> environment, Map<String, Object> options,
                                        ShellType shellType) {
        requireSandboxContext();
        Map<String, Object> params = buildExecuteParams(command, cwd, timeout, environment, options, shellType);
        Object raw = sandboxMixin.invoke(sessionId, "execute_cmd", params);
        return convertToExecuteCmdResult(raw);
    }

    /**
     * Execute a shell command in background mode.
     *
     * @param command    command to execute
     * @param cwd        working directory
     * @param environment environment variables
     * @param options    additional options
     * @return background execution result
     */
    public Object executeCmdBackground(String command, String cwd,
                                        Map<String, String> environment, Map<String, Object> options) {
        requireSandboxContext();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", command);
        if (cwd != null) params.put("cwd", cwd);
        if (environment != null) params.put("environment", environment);
        if (options != null) params.put("options", options);
        
        return sandboxMixin.invoke(sessionId, "execute_cmd_background", params);
    }

    private void requireSandboxContext() {
        if (!sandboxContextInitialized) {
            throw new UnsupportedOperationException("Sandbox shell operation requires SandboxRunConfig.");
        }
    }

    /**
     * Build parameters map for execute methods.
     */
    private Map<String, Object> buildExecuteParams(String command, String cwd, int timeout,
                                                    Map<String, String> environment, 
                                                    Map<String, Object> options, ShellType shellType) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", command);
        if (cwd != null) params.put("cwd", cwd);
        params.put("timeout", timeout);
        if (environment != null) params.put("environment", environment);
        if (options != null) params.put("options", options);
        params.put("shell_type", shellType.getValue());
        return params;
    }

    /**
     * Convert raw result to ExecuteCmdResult.
     */
    private ExecuteCmdResult convertToExecuteCmdResult(Object raw) {
        if (raw instanceof ExecuteCmdResult) {
            return (ExecuteCmdResult) raw;
        }
        if (raw instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) raw;
            ExecuteCmdData data = ExecuteCmdData.builder()
                    .exitCode(map.get("exit_code") != null ? ((Number) map.get("exit_code")).intValue() : 0)
                    .stdout((String) map.get("stdout"))
                    .stderr((String) map.get("stderr"))
                    .build();
            return ExecuteCmdResult.success(data);
        }
        throw new RuntimeException("Invalid execute_cmd result type: " + raw.getClass().getName());
    }

    /**
     * Convert raw iterator to ExecuteCmdStreamResult iterator.
     */
    private Iterator<ExecuteCmdStreamResult> convertStreamIterator(Iterator<?> rawIterator) {
        return new Iterator<ExecuteCmdStreamResult>() {
            @Override
            public boolean hasNext() {
                return rawIterator.hasNext();
            }

            @Override
            public ExecuteCmdStreamResult next() {
                Object item = rawIterator.next();
                if (item instanceof ExecuteCmdStreamResult) {
                    return (ExecuteCmdStreamResult) item;
                }
                if (item instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) item;
                    ExecuteCmdChunkData chunkData = ExecuteCmdChunkData.builder()
                            .text((String) map.get("chunk"))
                            .type(map.get("is_stdout") != null && (Boolean) map.get("is_stdout") ? "stdout" : "stderr")
                            .build();
                    return new ExecuteCmdStreamResult(0, "success", chunkData);
                }
                throw new RuntimeException("Invalid stream result type: " + item.getClass().getName());
            }
        };
    }
}
