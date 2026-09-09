/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests Core-only Memory runtime resolution.
 *
 * @since 0.1.15
 */
class MemoryRuntimeResolverTest {
    @Test
    void findShouldReturnEmptyWithoutMemoryArtifact() {
        assertThat(MemoryRuntimeResolver.find()).isEmpty();
    }

    @Test
    void requireShouldExplainMissingMemoryArtifact() {
        assertThatThrownBy(MemoryRuntimeResolver::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent-core-memory-java");
    }
}
