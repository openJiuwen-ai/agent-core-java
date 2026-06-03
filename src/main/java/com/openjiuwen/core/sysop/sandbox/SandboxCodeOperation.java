/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.BaseCodeOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sandbox code operation.
 * <p>
 * Mirrors Python's {@code CodeOperation} in {@code sandbox/code_operation.py}.
 */
@Operation(name = "code", mode = OperationMode.SANDBOX, description = "sandbox code operation")
public class SandboxCodeOperation extends BaseCodeOperation {

    private final BaseSandboxMixin sandboxMixin;
    private final String sessionId;
    private final boolean sandboxContextInitialized;

    public SandboxCodeOperation(Object runConfig) {
        this(runConfig, "default_session");
    }

    public SandboxCodeOperation(Object runConfig, String sessionId) {
        super("code", OperationMode.SANDBOX, "sandbox code operation", runConfig);
        this.sessionId = sessionId;
        this.sandboxMixin = new BaseSandboxMixin();
        if (runConfig instanceof SandboxRunConfig sandboxRunConfig) {
            this.sandboxMixin.initSandboxContext(sandboxRunConfig, "code");
            this.sandboxContextInitialized = true;
        } else {
            this.sandboxContextInitialized = false;
        }
    }

    @Override
    public ExecuteCodeResult executeCode(String code, String language, int timeout,
                                         Map<String, String> environment, Map<String, Object> options) {
        Map<String, Object> params = buildParams(code, language, timeout, environment, options);
        Object raw = invoke("execute_code", params);
        if (raw instanceof ExecuteCodeResult result) {
            return result;
        }
        throw new RuntimeException("Invalid execute_code result type: "
                + (raw == null ? "null" : raw.getClass().getName()));
    }

    @Override
    public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout,
                                                                Map<String, String> environment,
                                                                Map<String, Object> options) {
        Map<String, Object> params = buildParams(code, language, timeout, environment, options);
        try {
            requireSandboxContext();
            Iterator<?> iterator = sandboxMixin.invokeStream(sessionId, "execute_code_stream", params);
            return new Iterator<ExecuteCodeStreamResult>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public ExecuteCodeStreamResult next() {
                    Object value = iterator.next();
                    if (value instanceof ExecuteCodeStreamResult result) {
                        return result;
                    }
                    throw new RuntimeException("Invalid execute_code_stream item type: "
                            + (value == null ? "null" : value.getClass().getName()));
                }
            };
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("execute_code_stream failed", e);
        }
    }

    private Map<String, Object> buildParams(String code, String language, int timeout,
                                            Map<String, String> environment, Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("code", code);
        params.put("language", language != null ? language : "python");
        params.put("timeout", timeout > 0 ? timeout : 300);
        params.put("environment", environment);
        params.put("options", options);
        return params;
    }

    private Object invoke(String method, Map<String, Object> params) {
        requireSandboxContext();
        return sandboxMixin.invoke(sessionId, method, params);
    }

    private void requireSandboxContext() {
        if (!sandboxContextInitialized) {
            throw new UnsupportedOperationException("Sandbox code operation requires SandboxRunConfig.");
        }
    }
}
