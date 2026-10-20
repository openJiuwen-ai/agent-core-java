/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link OffloadConfig} P2 config additions (T19/T20/T21): defaults, setter
 * clamping and builder wiring.
 */
class OffloadConfigP2FieldsTest {
    @Test
    void p2DefaultsAreConservative() {
        OffloadConfig config = new OffloadConfig();

        // T19 otel off by default (P0 Slf4j-only path unchanged).
        assertThat(config.isEnableOtel()).isFalse();
        // T20 file lock off by default (atomic move suffices in-process).
        assertThat(config.isEnableFileLock()).isFalse();
        // T21 orphan TTL defaults to 1h per the design (§7.10 C6).
        assertThat(config.getOrphanTtlSeconds()).isEqualTo(3600L);
    }

    @Test
    void orphanTtlSetterClampsNegativesToZero() {
        OffloadConfig config = new OffloadConfig();
        config.setOrphanTtlSeconds(-5L);
        assertThat(config.getOrphanTtlSeconds()).isZero();
    }

    @Test
    void builderCarriesP2Fields() {
        OffloadConfig config = OffloadConfig.builder()
                .enableOtel(true)
                .enableFileLock(true)
                .orphanTtlSeconds(600L)
                .build();

        assertThat(config.isEnableOtel()).isTrue();
        assertThat(config.isEnableFileLock()).isTrue();
        assertThat(config.getOrphanTtlSeconds()).isEqualTo(600L);
    }
}
