/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RoundLevelCompressor}.
 */
class RoundLevelCompressorTest {

    @Test
    @DisplayName("config defaults match Python current config")
    void configDefaultsMatchPythonCurrentConfig() {
        RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder().build();

        assertEquals(230000, config.getTriggerTotalTokens());
        assertEquals(160000, config.getTargetTotalTokens());
        assertEquals(0, config.getKeepRecentMessages());
        assertEquals(250000, config.getCompressionCallMaxTokens());
        assertEquals(30000, config.getFirstPassTargetTokens());
        assertEquals(20000, config.getSecondPassTargetTokens());
        assertEquals(10000, config.getThirdPassTargetTokens());
        assertEquals(0.2, config.getTruncateHeadRatio());
        assertNotNull(config.getCompressionMarker());
    }

    @Test
    @DisplayName("round level fallback marker is accessible")
    void roundLevelFallbackMarkerIsAccessible() {
        assertNotNull(RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER);
        assertTrue(RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER.startsWith("[ROUND_LEVEL"));
    }

    @Test
    @DisplayName("processor type returns correct name and state is stateless")
    void processorTypeAndStateAreStable() {
        RoundLevelCompressor compressor = new RoundLevelCompressor(RoundLevelCompressorConfig.builder().build());

        assertEquals("RoundLevelCompressor", compressor.processorType());
        assertTrue(compressor.saveState().isEmpty());
        compressor.loadState(Map.of());
    }

    @Test
    @DisplayName("config builder can override trigger and target tokens")
    void configBuilderCanOverrideTokens() {
        RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder()
                .triggerTotalTokens(100)
                .targetTotalTokens(50)
                .build();
        assertEquals(100, config.getTriggerTotalTokens());
        assertEquals(50, config.getTargetTotalTokens());
    }

    @Test
    @DisplayName("config builder keeps recent messages")
    void configBuilderKeepsRecentMessages() {
        RoundLevelCompressorConfig config = RoundLevelCompressorConfig.builder()
                .keepRecentMessages(6)
                .build();
        assertEquals(6, config.getKeepRecentMessages());
    }
}
