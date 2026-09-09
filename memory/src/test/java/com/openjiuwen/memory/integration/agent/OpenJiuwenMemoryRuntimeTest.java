/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.integration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.spi.memory.MemoryRuntime;
import com.openjiuwen.spi.memory.MemoryRuntimeResolver;

import org.junit.jupiter.api.Test;

/**
 * Tests Memory runtime service discovery.
 *
 * @since 0.1.15
 */
class OpenJiuwenMemoryRuntimeTest {
    @Test
    void resolverShouldDiscoverMemoryRuntimeFromMemoryArtifact() {
        MemoryRuntime runtime = MemoryRuntimeResolver.require();

        assertThat(runtime).isInstanceOf(OpenJiuwenMemoryRuntime.class);
    }
}
