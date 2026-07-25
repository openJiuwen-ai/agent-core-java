/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for disabled observability configuration.
 *
 * <p>When {@link ObservabilityConfig#isEnabled()} is {@code false},
 * {@link ObservabilitySetup#initObservability(ObservabilityConfig)} is a
 * no-op: no provider is created, {@link ObservabilitySetup#isInitialized()}
 * returns {@code false}, and {@link ObservabilitySetup#getTracer(String)}
 * falls back to the global OpenTelemetry tracer (which may be a no-op
 * noop tracer).</p>
 *
 * @since 0.1.7
 */
@DisplayName("Disabled observability config tests")
class ObservabilityDisabledConfigTest {

    @BeforeEach
    void cleanUp() {
        if (ObservabilitySetup.isInitialized()) {
            ObservabilitySetup.shutdownObservability();
        }
        OtelSpanContext.resetAll();
    }

    @AfterEach
    void tearDown() {
        if (ObservabilitySetup.isInitialized()) {
            ObservabilitySetup.shutdownObservability();
        }
        OtelSpanContext.resetAll();
    }

    @Test
    @DisplayName("disabled config makes initObservability a no-op")
    void test_disabled_config_is_noop() {
        ObservabilityConfig disabledConfig = ObservabilityConfig.builder()
                .isEnabled(false)
                .build();

        ObservabilitySetup.initObservability(disabledConfig);

        assertFalse(ObservabilitySetup.isInitialized(),
                "isInitialized should be false when config is disabled");
    }

    @Test
    @DisplayName("null config makes initObservability a no-op")
    void test_null_config_is_noop() {
        ObservabilitySetup.initObservability(null);

        assertFalse(ObservabilitySetup.isInitialized(),
                "isInitialized should be false when config is null");
    }

    @Test
    @DisplayName("startTeamTrace is no-op when not initialized")
    void test_start_team_trace_noop_when_not_initialized() {
        // Ensure not initialized.
        assertFalse(ObservabilitySetup.isInitialized());

        // startTeamTrace should not throw and should not create a team span.
        ObservabilitySetup.startTeamTrace("disabled_team", "sess-disabled");

        // No team span should be set in the context.
        assertTrue(OtelSpanContext.getTeamSpan().isEmpty(),
                "team span should not be set when observability is not initialized");
        assertTrue(OtelSpanContext.getTeamName().isEmpty(),
                "team name should not be set when observability is not initialized");
    }

    @Test
    @DisplayName("finalizeTeamTrace is no-op when not initialized")
    void test_finalize_team_trace_noop_when_not_initialized() {
        assertFalse(ObservabilitySetup.isInitialized());

        // Should not throw.
        ObservabilitySetup.finalizeTeamTrace("nonexistent_team");
    }

    @Test
    @DisplayName("enabled config initializes observability")
    void test_enabled_config_initializes() {
        ObservabilityConfig enabledConfig = ObservabilityConfig.builder()
                .isEnabled(true)
                .exporter("console")
                .serviceName("disabled-test")
                .sampleRate(1.0)
                .build();

        ObservabilitySetup.initObservability(enabledConfig);

        assertTrue(ObservabilitySetup.isInitialized(),
                "isInitialized should be true when config is enabled");

        ObservabilitySetup.shutdownObservability();
    }

    @Test
    @DisplayName("disabled config does not create monitor handler")
    void test_disabled_config_no_monitor_handler() {
        ObservabilityConfig disabledConfig = ObservabilityConfig.builder()
                .isEnabled(false)
                .build();

        ObservabilitySetup.initObservability(disabledConfig);

        // getMonitorTracer should return empty when not initialized.
        assertTrue(ObservabilitySetup.getMonitorTracer().isEmpty(),
                "monitor tracer should be empty when observability is disabled");
    }
}
