/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import java.util.List;

/**
 * Package facade for agent-builder executor exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.executor} in
 * {@code openjiuwen/dev_tools/agent_builder/executor/__init__.py}.</p>
 */
public final class AgentBuilderExecutorPackage {

    public static final String PYTHON_MODULE = "openjiuwen/dev_tools/agent_builder/executor/__init__.py";
    public static final List<String> ALL = List.of(
            "AgentBuilderExecutor",
            "HistoryManager",
            "HistoryCache"
    );

    private AgentBuilderExecutorPackage() {
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }
}
