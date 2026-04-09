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
import java.util.Map;

/**
 * Sandbox code operation stub — not yet implemented.
 * <p>
 * Mirrors Python's {@code sandbox/code_operation.py}.
 */
@Operation(name = "code", mode = OperationMode.SANDBOX, description = "sandbox code operation")
public class SandboxCodeOperation extends BaseCodeOperation {

    public SandboxCodeOperation(Object runConfig) {
        super("code", OperationMode.SANDBOX, "sandbox code operation", runConfig);
    }

    @Override
    public ExecuteCodeResult executeCode(String code, String language, int timeout,
                                         Map<String, String> environment, Map<String, Object> options) {
        throw new UnsupportedOperationException("Code operation sandbox mode is not implemented yet.");
    }

    @Override
    public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout,
                                                                Map<String, String> environment,
                                                                Map<String, Object> options) {
        throw new UnsupportedOperationException("Code operation sandbox mode is not implemented yet.");
    }
}
