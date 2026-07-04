/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;

import java.util.Iterator;
import java.util.Map;

/**
 * Backward-compatible base class for moved local code operations.
 *
 * <p>Mirrors Python's {@code BaseCodeOperation} in
 * {@code openjiuwen/core/sys_operation/code.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.BaseCodeOperation}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public abstract class BaseCodeOperation extends BaseOperation {

    protected BaseCodeOperation(String name,
                                com.openjiuwen.core.sys_operation.OperationMode mode,
                                String description,
                                Object runConfig) {
        super(name, mode, description, runConfig);
    }

    public abstract ExecuteCodeResult executeCode(String code,
                                                  String language,
                                                  int timeout,
                                                  Map<String, String> environment,
                                                  Map<String, Object> options);

    public abstract Iterator<ExecuteCodeStreamResult> executeCodeStream(String code,
                                                                        String language,
                                                                        int timeout,
                                                                        Map<String, String> environment,
                                                                        Map<String, Object> options);
}
