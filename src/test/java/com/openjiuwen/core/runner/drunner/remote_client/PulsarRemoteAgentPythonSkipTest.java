/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestRunnerIntegration} in
 * {@code tests/unit_tests/extensions/runner/pulsar_mq/test_pulsar_remote_agent.py}.</p>
 */
class PulsarRemoteAgentPythonSkipTest {

    private static final String PYTHON_PULSAR_SKIP_REASON = "Skipped in Python source: Requires real Pulsar uv sync "
            + "--extra pulsar";

    private static final String PYTHON_PERFORMANCE_SKIP_REASON = "Skipped in Python source: Skip performance tests";

    @Disabled(PYTHON_PULSAR_SKIP_REASON)
    @Test
    void testAgentNormalLifecycle() {
    }

    @Disabled(PYTHON_PULSAR_SKIP_REASON)
    @Test
    void testAgentRequestCancellation() {
    }

    @Disabled(PYTHON_PULSAR_SKIP_REASON)
    @Test
    void testAgentRequestTimeout() {
    }

    @Disabled(PYTHON_PULSAR_SKIP_REASON)
    @Test
    void testAgentRunnerShutdownCancelsClients() {
    }

    @Disabled(PYTHON_PULSAR_SKIP_REASON)
    @Test
    void testAgentAdapterExceptionPropagation() {
    }

    @Disabled(PYTHON_PULSAR_SKIP_REASON)
    @Test
    void testAgentCallWithoutRunnerStartShouldRaiseException() {
    }

    @Disabled(PYTHON_PERFORMANCE_SKIP_REASON)
    @Test
    void testConcurrentVsSequentialPerformanceComparison() {
    }

    @Disabled(PYTHON_PERFORMANCE_SKIP_REASON)
    @Test
    void testConcurrentStreaming() {
    }
}
