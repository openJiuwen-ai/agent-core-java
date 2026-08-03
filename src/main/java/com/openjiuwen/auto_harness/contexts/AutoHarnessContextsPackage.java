/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.contexts;

import java.util.List;

/**
 * Package facade for auto-harness execution context exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.contexts} in
 * {@code openjiuwen/auto_harness/contexts/__init__.py}.</p>
 */
public final class AutoHarnessContextsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/auto_harness/contexts/__init__.py";
    public static final List<String> ALL = List.of(
            "BaseExecutionContext",
            "SessionContext",
            "TaskContext",
            "TaskRuntime",
            "task_key"
    );

    private AutoHarnessContextsPackage() {
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }
}
