/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's harness config registry cache and disable behavior in
 * {@code openjiuwen/harness/harness_config/registry.py}.
 */
class HarnessConfigRegistryTest {

    @AfterEach
    void tearDown() {
        HarnessConfigRegistry.replaceCacheForTesting(null);
    }

    @Test
    void discoverFiltersDisabledConfigs() {
        HarnessConfigRegistry.replaceCacheForTesting(List.of(
                new HarnessConfigInfo("demo", "demo", "1.0.0", "pkg", Path.of("harness_config.yaml"))
        ));

        assertEquals("demo", HarnessConfigRegistry.get("demo").getId());
        HarnessConfigRegistry.disable("demo");

        assertNull(HarnessConfigRegistry.get("demo"));
        assertEquals(0, HarnessConfigRegistry.discover().size());

        HarnessConfigRegistry.enable("demo");
        assertEquals(1, HarnessConfigRegistry.discover().size());
    }

    @Test
    void inspectReturnsPackageConfigsEvenWhenDisabled() {
        HarnessConfigRegistry.replaceCacheForTesting(List.of(
                new HarnessConfigInfo("demo", "demo", "1.0.0", "pkg", Path.of("harness_config.yaml"))
        ));
        HarnessConfigRegistry.disable("demo");

        assertEquals(1, HarnessConfigRegistry.inspect("pkg").size());
    }

    @Test
    void loadMissingConfigRaisesKeyStyleError() {
        HarnessConfigRegistry.replaceCacheForTesting(List.of());

        assertThrows(NoSuchElementException.class, () -> HarnessConfigRegistry.load("missing", new Object()));
    }
}
