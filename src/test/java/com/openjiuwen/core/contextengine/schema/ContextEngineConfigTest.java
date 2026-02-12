/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.contextengine.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContextEngineConfig}.
 * 
 * <p>Converted from Python: test_context_engine_config.py</p>
 */
class ContextEngineConfigTest {

    /**
     * Test that configuration validates positive constraints.
     * 
     * <p>Python: test_context_engine_config_validates_positive_constraints</p>
     * <p>Assertions: 3</p>
     */
    @Test
    void testValidatesPositiveConstraints() {
        // default_window_message_num must be > 0
        assertThrows(IllegalArgumentException.class, () -> 
            ContextEngineConfig.builder()
                .defaultWindowMessageNum(0)
                .build()
        );
        
        // default_window_token_num must be > 0 if set
        assertThrows(IllegalArgumentException.class, () -> 
            ContextEngineConfig.builder()
                .defaultWindowTokenNum(-1)
                .build()
        );
        
        // memory_message_num must be > 0
        assertThrows(IllegalArgumentException.class, () -> 
            ContextEngineConfig.builder()
                .memoryMessageNum(0)
                .build()
        );
    }

    /**
     * Test that default values are stable.
     * 
     * <p>Python: test_context_engine_config_defaults_are_stable</p>
     * <p>Assertions: 1</p>
     */
    @Test
    void testDefaultsAreStable() {
        ContextEngineConfig cfg = ContextEngineConfig.builder().build();
        assertTrue(cfg.getMemoryMessageNum() > 0);
    }
}









