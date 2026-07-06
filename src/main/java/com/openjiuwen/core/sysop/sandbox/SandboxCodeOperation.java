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
import java.util.List;
import java.util.Map;

/**
 * Sandbox code operation routed through the sandbox gateway/provider chain.
 *
 * @since 2026-01-01
 */
@Operation(name = "code", mode = OperationMode.SANDBOX, description = "sandbox code operation")
public class SandboxCodeOperation extends BaseCodeOperation {
    private static final String OP_TYPE = "code";

    private final SandboxGatewayClient gatewayClient;

    /**
     * Auto-generated for codecheck compliance.
     *
     * @param runConfig 运行配置对象，包含沙箱执行所需的配置信息
     */
    public SandboxCodeOperation(Object runConfig) {
        super("code", OperationMode.SANDBOX, "sandbox code operation", runConfig);
        this.gatewayClient = new SandboxGatewayClient(getSandboxConfig(),
                SandboxOperationSupport.resolveIsolationKey(getSandboxConfig()));
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public ExecuteCodeResult executeCode(String code, String language, int timeout, Map<String, String> environment,
            Map<String, Object> options) {
        try {
            return invoke("executeCode", ExecuteCodeResult.class, SandboxOperationSupport.paramsOf("code", code,
                    "language", language, "timeout", timeout, "environment", environment, "options", options));
        } catch (IllegalArgumentException ex) {
            return SandboxOperationSupport.buildCodeError("execute_code", ex.getMessage(), code, language);
        }
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        try {
            @SuppressWarnings("unchecked")
            Iterator<ExecuteCodeStreamResult> iterator = invoke("executeCodeStream", Iterator.class,
                    SandboxOperationSupport.paramsOf("code", code, "language", language, "timeout", timeout,
                            "environment", environment, "options", options));
            return iterator;
        } catch (IllegalArgumentException ex) {
            return List.of(SandboxOperationSupport.buildCodeStreamError("execute_code_stream", ex.getMessage(), code,
                    language)).iterator();
        }
    }

    private <T> T invoke(String method, Class<T> type, Map<String, Object> params) {
        Object result = gatewayClient.invoke(OP_TYPE, method, params);
        if (type.isInstance(result)) {
            return type.cast(result);
        }
        throw new IllegalArgumentException("Unexpected sandbox code response data type");
    }
}
