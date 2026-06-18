/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.executor} in
 * {@code openjiuwen/dev_tools/agent_builder/executor/__init__.py}.
 */
class AgentBuilderExecutorPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(AgentBuilderExecutorPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/dev_tools/agent_builder/executor/__init__.py");
        assertThat(AgentBuilderExecutorPackage.ALL).containsExactly(
                "AgentBuilderExecutor",
                "HistoryManager",
                "HistoryCache"
        );
    }

    @Test
    void exportsOnlyPythonAllSymbols() {
        assertThat(AgentBuilderExecutorPackage.exports("AgentBuilderExecutor")).isTrue();
        assertThat(AgentBuilderExecutorPackage.exports("HistoryCache")).isTrue();
        assertThat(AgentBuilderExecutorPackage.exports("DialogueMessage")).isFalse();
    }

    @Test
    void historyCacheExportMapsToNestedJavaType() {
        assertThat(HistoryManager.HistoryCache.class.getSimpleName()).isEqualTo("HistoryCache");
    }
}
