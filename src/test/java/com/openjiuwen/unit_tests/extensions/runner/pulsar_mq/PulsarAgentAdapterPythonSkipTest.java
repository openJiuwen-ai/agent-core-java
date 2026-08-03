/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.runner.pulsar_mq;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestAdapterTest} in
 * {@code tests/unit_tests/extensions/runner/pulsar_mq/test_pulsar_agent_adapter.py}.</p>
 */
class PulsarAgentAdapterPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: Requires real uv sync --extra pulsar and llm";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void adapterInvoke() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void adapterStream() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void reactAgentInvokeWithAdapter() {
    }
}
