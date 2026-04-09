/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContextStats}.
 */
class ContextStatsTest {

    @Test
    @DisplayName("Default ContextStats has zero values")
    void testDefaults() {
        ContextStats stats = new ContextStats();
        assertEquals(0, stats.getTotalMessages());
        assertEquals(0, stats.getTotalTokens());
        assertEquals(0, stats.getTotalDialogues());
        assertEquals(0, stats.getSystemMessages());
        assertEquals(0, stats.getUserMessages());
        assertEquals(0, stats.getAssistantMessages());
        assertEquals(0, stats.getToolMessages());
        assertEquals(0, stats.getTools());
    }

    @Test
    @DisplayName("Builder sets fields correctly")
    void testBuilder() {
        ContextStats stats = ContextStats.builder()
                .totalMessages(10)
                .totalTokens(500)
                .userMessages(3)
                .assistantMessages(3)
                .systemMessages(1)
                .toolMessages(3)
                .build();

        assertEquals(10, stats.getTotalMessages());
        assertEquals(500, stats.getTotalTokens());
        assertEquals(3, stats.getUserMessages());
    }
}
