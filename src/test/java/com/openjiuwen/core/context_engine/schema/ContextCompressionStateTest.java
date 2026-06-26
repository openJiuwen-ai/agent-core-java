/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.schema;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies translated context compression state schema behavior.
 *
 * <p>Mirrors Python's context state models in
 * {@code openjiuwen/core/context_engine/schema/context_state.py}.</p>
 */
class ContextCompressionStateTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void defaultsMirrorPythonContextCompressionState() {
        ContextCompressionMetric before = new ContextCompressionMetric(null, 2, 32, null);
        ContextCompressionState state = new ContextCompressionState(
                "op-1", ContextCompressionStatus.STARTED, ContextCompressionPhase.ADD_MESSAGES, before);

        assertEquals(ContextCompressionState.CONTEXT_COMPRESSION_STATE_TYPE, state.getType());
        assertEquals("", state.getProcessor());
        assertEquals("", state.getModel());
        assertEquals("", state.getSummary());
        assertEquals("", state.getCompactSummary());
        assertNotNull(state.getStatistic());
        assertNull(state.getAfter());
        assertNull(state.getSaved());
        assertNull(state.getCompressionUsage());
        assertNull(state.getDurationMs());
        assertNull(state.getContextMax());
        assertNull(state.getError());

        Map<String, Object> dump = state.modelDump();
        assertEquals("context.compression_state", dump.get("type"));
        assertEquals("op-1", dump.get("operation_id"));
        assertEquals("started", dump.get("status"));
        assertEquals("add_messages", dump.get("phase"));
        assertTrue(dump.containsKey("compact_summary"));
        assertTrue(dump.containsKey("compression_usage"));
        assertEquals(32, ((Map<String, Object>) dump.get("before")).get("tokens"));
        assertTrue(((Map<String, Object>) dump.get("statistic")).containsKey("total_messages"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void usageDefaultsAndSnakeCaseSerializationMirrorPythonModel() throws Exception {
        ContextCompressionUsage usage = ContextCompressionUsage.fromMap(Map.of(
                "calls", 1,
                "input_tokens", 11,
                "total_tokens", 17,
                "model_name", "model-a",
                "details", List.of(Map.of("stage", "compact"))
        ));

        assertEquals(1, usage.getCalls());
        assertEquals(11, usage.getInputTokens());
        assertEquals(0, usage.getOutputTokens());
        assertEquals(17, usage.getTotalTokens());
        assertEquals(0, usage.getCacheTokens());
        assertEquals(0.0d, usage.getInputCost());
        assertEquals("model-a", usage.getModelName());
        assertEquals(List.of(Map.of("stage", "compact")), usage.getDetails());

        Map<String, Object> payload = MAPPER.readValue(MAPPER.writeValueAsBytes(usage), Map.class);
        assertEquals(11, payload.get("input_tokens"));
        assertEquals(17, payload.get("total_tokens"));
        assertEquals("model-a", payload.get("model_name"));
        assertFalse(payload.containsKey("inputTokens"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stateSerializationKeepsPythonFieldNames() throws Exception {
        ContextCompressionState state = new ContextCompressionState(
                "op-2",
                ContextCompressionStatus.COMPLETED,
                ContextCompressionPhase.ACTIVE_COMPRESS,
                new ContextCompressionMetric("2026-06-12T10:00:00", 4, 128, 64)
        );
        state.setAfter(new ContextCompressionMetric("2026-06-12T10:00:01", 2, 32, 16));
        state.setSaved(new ContextCompressionSaved(2, 96, 75.0d));
        state.setCompressionUsage(new ContextCompressionUsage(
                1, 20, 8, 28, 2, 0.1d, 0.2d, 0.3d, "model-b", List.of()));
        state.setDurationMs(125);
        state.setContextMax(200);
        state.setCompactSummary("kept latest turn");

        Map<String, Object> payload = MAPPER.readValue(MAPPER.writeValueAsBytes(state), Map.class);

        assertEquals("op-2", payload.get("operation_id"));
        assertEquals("completed", payload.get("status"));
        assertEquals("active_compress", payload.get("phase"));
        assertEquals(125, payload.get("duration_ms"));
        assertEquals(200, payload.get("context_max"));
        assertEquals("kept latest turn", payload.get("compact_summary"));
        assertTrue(((Map<String, Object>) payload.get("compression_usage")).containsKey("input_tokens"));
        assertFalse(payload.containsKey("operationId"));
    }
}
