/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ContextStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for context compression state schema classes.
 */
class ContextCompressionStateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("default context compression metric matches Python defaults")
    void testMetricDefaults() {
        ContextCompressionMetric metric = new ContextCompressionMetric();

        assertNull(metric.getTime());
        assertEquals(0, metric.getMessages());
        assertEquals(0, metric.getTokens());
        assertNull(metric.getContextPercent());
    }

    @Test
    @DisplayName("default context compression saved matches Python defaults")
    void testSavedDefaults() {
        ContextCompressionSaved saved = new ContextCompressionSaved();

        assertEquals(0, saved.getMessages());
        assertEquals(0, saved.getTokens());
        assertEquals(0.0f, saved.getPercent());
    }

    @Test
    @DisplayName("default compression state matches Python defaults")
    void testStateDefaults() {
        ContextCompressionState state = new ContextCompressionState();

        assertEquals(ContextCompressionState.CONTEXT_COMPRESSION_STATE_TYPE, state.getType());
        assertEquals("", state.getProcessor());
        assertEquals("", state.getModel());
        assertNotNull(state.getStatistic());
        assertEquals("", state.getSummary());
        assertNull(state.getAfter());
        assertNull(state.getSaved());
        assertNull(state.getDurationMs());
        assertNull(state.getContextMax());
        assertNull(state.getError());
    }

    @Test
    @DisplayName("builder and JSON property names align with Python schema")
    void testStateBuilderAndJsonProperties() throws Exception {
        ContextCompressionState state = ContextCompressionState.builder()
                .operationId("op-1")
                .status("completed")
                .phase("active_compress")
                .processor("RoundLevelCompressor")
                .model("demo-model")
                .before(ContextCompressionMetric.builder()
                        .time("2026-05-08T12:00:00.000+08:00")
                        .messages(10)
                        .tokens(2000)
                        .contextPercent(10)
                        .build())
                .after(ContextCompressionMetric.builder()
                        .messages(4)
                        .tokens(500)
                        .contextPercent(2)
                        .build())
                .statistic(ContextStats.builder().totalMessages(4).totalTokens(500).build())
                .saved(ContextCompressionSaved.builder().messages(6).tokens(1500).percent(75.0f).build())
                .durationMs(1234)
                .contextMax(20000)
                .summary("compressed")
                .error("none")
                .build();

        String json = MAPPER.writeValueAsString(state);

        assertTrue(json.contains("\"operation_id\":\"op-1\""));
        assertTrue(json.contains("\"duration_ms\":1234"));
        assertTrue(json.contains("\"context_max\":20000"));
        assertTrue(json.contains("\"context_percent\":10"));
        assertEquals("completed", state.getStatus());
        assertEquals("active_compress", state.getPhase());
        assertEquals(75.0f, state.getSaved().getPercent());
    }
}
