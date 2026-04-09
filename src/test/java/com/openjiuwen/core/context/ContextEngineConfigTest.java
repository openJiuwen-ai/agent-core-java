/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.context;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
                .build();

        assertEquals(100, config.getMaxContextMessageNum());
        assertEquals(20, config.getDefaultWindowMessageNum());
        assertEquals(5, config.getDefaultWindowRoundNum());
        assertTrue(config.isEnableKvCacheRelease());
        assertTrue(config.isEnableReload());
    }
}
