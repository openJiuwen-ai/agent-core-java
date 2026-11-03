/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Timeout config assembly, defaults, validation, per-call override
 * isolation, and legacy timeout explicitness semantics.
 *
 * @since 2026-10-09
 */
class ModelTimeoutConfigTest {
    private static final double TOLERANCE = 0.0001D;

    @Test
    void defaultsApplyWhenUnconfigured() {
        // Given an unconfigured client config
        ModelClientConfig config = ModelClientConfig.builder().build();

        // When resolving the three layers
        double connect = ModelTimeoutResolver.resolveConnectTimeoutSeconds(config);
        double idle = ModelTimeoutResolver.resolveStreamIdleSeconds(config);
        double headerWait = ModelTimeoutResolver.resolveHeaderWaitSeconds(config);

        // Then the tiered defaults apply with no total cap semantics involved
        assertThat(connect).isCloseTo(10.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(idle).isCloseTo(600.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(headerWait).isCloseTo(600.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(config.isTimeoutExplicit()).isFalse();
    }

    @Test
    void layersConfiguredIndependently() {
        // Given only the connect layer configured
        ModelClientConfig connectOnly = ModelClientConfig.builder()
                .connectTimeoutSeconds(8.0D)
                .build();
        // When resolving
        // Then connect takes the configured value while idle falls back to the 600s default
        assertThat(ModelTimeoutResolver.resolveConnectTimeoutSeconds(connectOnly))
                .isCloseTo(8.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(ModelTimeoutResolver.resolveStreamIdleSeconds(connectOnly))
                .isCloseTo(600.0D, org.assertj.core.data.Offset.offset(TOLERANCE));

        // Given only the idle layer configured
        ModelClientConfig idleOnly = ModelClientConfig.builder()
                .streamIdleSeconds(450.0D)
                .build();
        // Then idle takes the configured value while connect falls back to the 10s default
        assertThat(ModelTimeoutResolver.resolveConnectTimeoutSeconds(idleOnly))
                .isCloseTo(10.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(ModelTimeoutResolver.resolveStreamIdleSeconds(idleOnly))
                .isCloseTo(450.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        // And the header wait follows the idle layer, never the legacy 60s field
        assertThat(ModelTimeoutResolver.resolveHeaderWaitSeconds(idleOnly))
                .isCloseTo(450.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0D, -1.0D, 3.0D, 20.0D})
    void invalidConnectValuesRejected(double value) {
        // Given an out-of-range connect timeout
        // When building the config
        // Then construction rejects with IllegalArgumentException
        assertThatThrownBy(() -> ModelClientConfig.builder().connectTimeoutSeconds(value).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeoutSeconds");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0D, -5.0D, 100.0D, 1000.0D})
    void invalidIdleValuesRejected(double value) {
        // Given an out-of-range stream idle timeout
        // When building the config
        // Then construction rejects with IllegalArgumentException
        assertThatThrownBy(() -> ModelClientConfig.builder().streamIdleSeconds(value).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamIdleSeconds");
    }

    @Test
    void perCallOverrideAppliesToIdleLayer() {
        // Given a default client config
        ModelClientConfig config = ModelClientConfig.builder().build();

        // When a per-call override relaxes the idle window
        double effective = ModelTimeoutResolver.resolvePerCallIdleSeconds(1200.0F, config);

        // Then the override wins for this call and the config stays untouched
        assertThat(effective).isCloseTo(1200.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(config.getStreamIdleSeconds()).isNull();
    }

    @Test
    void perCallOverrideDoesNotPolluteSharedConfig() {
        // Given one shared client config
        ModelClientConfig config = ModelClientConfig.builder()
                .streamIdleSeconds(600.0D)
                .build();

        // When two concurrent calls resolve independently
        double relaxed = ModelTimeoutResolver.resolvePerCallIdleSeconds(1200.0F, config);
        double inherited = ModelTimeoutResolver.resolvePerCallIdleSeconds(null, config);

        // Then the relaxed call gets its override while the other keeps the configured window
        assertThat(relaxed).isCloseTo(1200.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(inherited).isCloseTo(600.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(config.getStreamIdleSeconds())
                .isCloseTo(600.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    void legacyTimeoutExplicitnessRules() {
        // Given a legacy timeout set to a non-default value via builder
        ModelClientConfig explicit = ModelClientConfig.builder().timeout(30.0D).build();
        // Then explicitness is marked and the header wait follows the legacy value
        assertThat(explicit.isTimeoutExplicit()).isTrue();
        assertThat(ModelTimeoutResolver.resolveHeaderWaitSeconds(explicit))
                .isCloseTo(30.0D, org.assertj.core.data.Offset.offset(TOLERANCE));

        // Given a legacy timeout set to the default value explicitly
        ModelClientConfig explicitDefault = ModelClientConfig.builder().timeout(60.0D).build();
        // Then it is treated as unconfigured (edge rule) and header wait follows the idle layer
        assertThat(explicitDefault.isTimeoutExplicit()).isFalse();
        assertThat(ModelTimeoutResolver.resolveHeaderWaitSeconds(explicitDefault))
                .isCloseTo(600.0D, org.assertj.core.data.Offset.offset(TOLERANCE));

        // Given a legacy timeout set via the setter (Jackson path)
        ModelClientConfig viaSetter = new ModelClientConfig();
        viaSetter.setTimeout(45.0D);
        // Then the setter marks explicitness by the same rule
        assertThat(viaSetter.isTimeoutExplicit()).isTrue();
    }

    @Test
    void legacyExplicitnessSurvivesJsonRoundTrip() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // Given a config with an explicit non-default legacy timeout
        ModelClientConfig explicit = ModelClientConfig.builder().timeout(120.0D).build();
        // When serialized and deserialized
        ModelClientConfig explicitCopy = mapper.readValue(
                mapper.writeValueAsString(explicit), ModelClientConfig.class);
        // Then explicitness is re-derived from the persisted value
        assertThat(explicitCopy.isTimeoutExplicit()).isTrue();
        assertThat(ModelTimeoutResolver.resolveHeaderWaitSeconds(explicitCopy))
                .isCloseTo(120.0D, org.assertj.core.data.Offset.offset(TOLERANCE));

        // Given a config with the default legacy timeout (not explicit)
        ModelClientConfig notExplicit = ModelClientConfig.builder().build();
        // When serialized and deserialized
        ModelClientConfig notExplicitCopy = mapper.readValue(
                mapper.writeValueAsString(notExplicit), ModelClientConfig.class);
        // Then the round trip never upgrades the default value to explicit
        assertThat(notExplicitCopy.isTimeoutExplicit()).isFalse();
        assertThat(ModelTimeoutResolver.resolveHeaderWaitSeconds(notExplicitCopy))
                .isCloseTo(600.0D, org.assertj.core.data.Offset.offset(TOLERANCE));

        // And the transient explicitness flag itself is never persisted
        assertThat(mapper.writeValueAsString(explicit)).doesNotContain("timeoutExplicit");
    }

    @Test
    void invalidConnectSystemPropertyFallsBackToDefault() {
        String property = "openjiuwen.llm.http.connect-timeout-seconds";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "not-a-number");
            assertThat(ModelTimeoutResolver.resolveConnectTimeoutSeconds(null))
                    .isCloseTo(10.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
            System.setProperty(property, "-5");
            assertThat(ModelTimeoutResolver.resolveConnectTimeoutSeconds(null))
                    .isCloseTo(10.0D, org.assertj.core.data.Offset.offset(TOLERANCE));
            System.setProperty(property, "7.5");
            assertThat(ModelTimeoutResolver.resolveConnectTimeoutSeconds(null))
                    .isCloseTo(7.5D, org.assertj.core.data.Offset.offset(TOLERANCE));
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}
