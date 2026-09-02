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
 * no-op: no provider is created and {@link ObservabilitySetup#isInitialized()}
 * returns {@code false}.</p>
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
    @DisplayName("disabled config does not set isInitialized")
    void test_disabled_config_does_not_set_initialized() {
        ObservabilityConfig disabledConfig = ObservabilityConfig.builder()
                .isEnabled(false)
                .build();

        ObservabilitySetup.initObservability(disabledConfig);

        assertFalse(ObservabilitySetup.isInitialized(),
                "isInitialized should remain false when config is disabled");
    }

    @Test
    @DisplayName("shutdownObservability is safe when not initialized")
    void test_shutdown_safe_when_not_initialized() {
        assertFalse(ObservabilitySetup.isInitialized());

        // Should not throw.
        ObservabilitySetup.shutdownObservability();
        ObservabilitySetup.shutdownObservability();
    }
}
