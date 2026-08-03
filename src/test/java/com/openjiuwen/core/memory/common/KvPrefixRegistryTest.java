/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class KvPrefixRegistryTest {

    @Test
    void registryTracksCurrentAndLegacyPrefixes() {
        KvPrefixRegistry registry = new KvPrefixRegistry();

        registry.registerCurrent("UMD_NEW");
        registry.registerLegacy("UMD");

        assertThat(registry.getAllPrefixes()).containsExactlyInAnyOrder("UMD_NEW", "UMD");

        registry.unregister("UMD");

        assertThat(registry.getAllPrefixes()).containsExactly("UMD_NEW");
    }

    @Test
    void registerCurrentRejectsBlankPrefixes() {
        KvPrefixRegistry registry = new KvPrefixRegistry();

        assertThatThrownBy(() -> registry.registerCurrent("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prefix cannot be empty");
    }

    @Test
    void getAllPrefixesReturnsCopyAndSharedInstanceIsAccessible() {
        KvPrefixRegistry registry = KvPrefixRegistry.getInstance();
        String prefix = "TEST_SHARED_PREFIX";

        registry.unregister(prefix);
        registry.registerLegacy(prefix);
        Set<String> snapshot = registry.getAllPrefixes();
        snapshot.clear();

        assertThat(registry.getAllPrefixes()).contains(prefix);

        registry.unregister(prefix);
    }
}
