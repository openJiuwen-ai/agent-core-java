/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import java.util.List;

/**
 * Package bridge for tool-call operator exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/operator/tool_call/__init__.py}.
 * </p>
 */
public final class ToolCallPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/operator/tool_call/__init__.py";
    public static final String DESCRIPTION =
            "Tool invocation operator: ToolCallOperator with enabled/retries tunables.";
    public static final Class<ToolCallOperator> TOOL_CALL_OPERATOR = ToolCallOperator.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("ToolCallOperator");

    private ToolCallPackage() {
    }
}
