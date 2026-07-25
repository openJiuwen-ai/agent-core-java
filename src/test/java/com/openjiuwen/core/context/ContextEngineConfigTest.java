/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.context;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContextEngineConfig}.
 */
class ContextEngineConfigTest {

    @Test
    @DisplayName("Default config has sensible defaults")
    void testDefaults() {
        ContextEngineConfig config = ContextEngineConfig.builder().build();
        assertNull(config.getMaxContextMessageNum());
        assertNull(config.getDefaultWindowMessageNum());
        assertNull(config.getDefaultWindowRoundNum());
        assertFalse(config.isEnableKvCacheRelease());
        assertFalse(config.isEnableReload());
        assertFalse(config.isTiktokenCounterEnabled());
        assertNull(config.getContextWindowTokens());
        assertNull(config.getModelName());
        assertNull(config.getModelContextWindowTokens());
    }

    @Test
    @DisplayName("Builder sets all fields correctly")
    void testBuilderSetsFields() {
        ContextEngineConfig config = ContextEngineConfig.builder()
                .maxContextMessageNum(100)
                .defaultWindowMessageNum(20)
                .defaultWindowRoundNum(5)
                .enableKvCacheRelease(true)
                .enableReload(true)
                .enableTiktokenCounter(true)
                .contextWindowTokens(120000)
                .modelName("demo-model")
                .modelContextWindowTokens(Map.of("demo-model", 120000))
                .build();

        assertEquals(100, config.getMaxContextMessageNum());
        assertEquals(20, config.getDefaultWindowMessageNum());
        assertEquals(5, config.getDefaultWindowRoundNum());
        assertTrue(config.isEnableKvCacheRelease());
        assertTrue(config.isEnableReload());
        assertTrue(config.isTiktokenCounterEnabled());
        assertEquals(120000, config.getContextWindowTokens());
        assertEquals("demo-model", config.getModelName());
        assertEquals(Map.of("demo-model", 120000), config.getModelContextWindowTokens());
    }

    @Test
    @DisplayName("validate rejects non-positive context window tokens")
    void testValidateContextWindowTokens() {
        ContextEngineConfig config = ContextEngineConfig.builder()
                .contextWindowTokens(0)
                .build();

        assertThrows(IllegalArgumentException.class, config::validate);
    }
}
