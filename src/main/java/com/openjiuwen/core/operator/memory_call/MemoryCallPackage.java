/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.memory_call;

import java.util.List;

/**
 * Package bridge for memory-call operator exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/operator/memory_call/__init__.py}.
 * </p>
 */
public final class MemoryCallPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/operator/memory_call/__init__.py";
    public static final String DESCRIPTION =
            "Memory invocation operator: MemoryCallOperator with enabled/retries tunables.";
    public static final Class<MemoryCallOperator> MEMORY_CALL_OPERATOR = MemoryCallOperator.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("MemoryCallOperator");

    private MemoryCallPackage() {
    }
}
