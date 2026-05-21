/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import com.openjiuwen.core.context.schema.ContextCompressionState;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility helpers for capturing and validating context compression states in tests.
 * <p>
 * Mirrors Python's {@code _stream_state_helpers.py} from
 * {@code tests/unit_tests/core/context_engine/_stream_state_helpers.py}.
 */
public class StreamStateHelpers {

    private static final String CONTEXT_COMPRESSION_STATE_TYPE = "context_compression_state";

    /**
     * Captures context compression states emitted during an action execution.
     * <p>
     * Collects all stream chunks from the session, filters for context compression
     * state type, and returns them as a list of ContextCompressionState objects.
     *
     * @param session the agent session to collect states from
     * @param action  the action to execute that may emit compression states
     * @return a tuple-like result containing the action result and list of states
     */
    public static CaptureResult captureContextCompressionStates(
            AgentSessionApi session,
            Supplier<CompletableFuture<Object>> action) {

        List<Object> chunks = new ArrayList<>();

        // Collect stream chunks in a background thread
        CompletableFuture<Void> collectTask = CompletableFuture.runAsync(() -> {
            session.getInner().streamWriterManager().streamOutput(-1, -1, false, chunks::add);
        });

        try {
            // Execute the action
            CompletableFuture<Object> actionFuture = action.get();
            Object result = actionFuture.join();

            // Wait for collection to complete
            collectTask.join();

            // Filter and parse compression states
            List<ContextCompressionState> states = new ArrayList<>();
            for (Object chunk : chunks) {
                if (!(chunk instanceof OutputSchema)) {
                    continue;
                }
                OutputSchema schema = (OutputSchema) chunk;
                if (!CONTEXT_COMPRESSION_STATE_TYPE.equals(schema.getType())) {
                    continue;
                }

                Object payload = schema.getPayload();
                if (payload instanceof ContextCompressionState) {
                    states.add((ContextCompressionState) payload);
                } else if (payload instanceof java.util.Map) {
                    // Parse from map if needed
                    ContextCompressionState state = parseStateFromMap((java.util.Map<String, Object>) payload);
                    states.add(state);
                }
            }

            return new CaptureResult(result, states);
        } catch (Exception e) {
            throw new RuntimeException("Failed to capture context compression states", e);
        }
    }

    /**
     * Asserts that the captured states represent a valid pair of start/completed events.
     * <p>
     * Validates that there are exactly two states with "started" and the final status,
     * both have the same processor type and phase, and the completed state has
     * duration and summary.
     *
     * @param states         the list of captured states
     * @param processorType  the expected processor type
     * @param phase          the expected phase (default: "add_messages")
     * @param finalStatus    the expected final status (default: "completed")
     */
    public static void assertContextStatePair(
            List<ContextCompressionState> states,
            String processorType,
            String phase,
            String finalStatus) {

        // Validate status sequence
        List<String> actualStatuses = states.stream()
                .map(ContextCompressionState::getStatus)
                .toList();
        List<String> expectedStatuses = List.of("started", finalStatus);
        assertEquals(expectedStatuses, actualStatuses,
                "Status sequence should be [started, " + finalStatus + "]");

        // Validate processor type
        for (ContextCompressionState state : states) {
            assertEquals(processorType, state.getProcessor(),
                    "All states should have processor type: " + processorType);
        }

        // Validate phase
        for (ContextCompressionState state : states) {
            assertEquals(phase, state.getPhase(),
                    "All states should have phase: " + phase);
        }

        // Validate timing info on first state
        assertNotNull(states.get(0).getBefore(),
                "First state should have 'before' timing info");
        assertNotNull(states.get(0).getBefore().getTime(),
                "First state 'before' should have time");

        // Validate completion info on second state
        assertNotNull(states.get(1).getDurationMs(),
                "Second state should have duration_ms");
        assertFalse(states.get(1).getSummary().isEmpty(),
                "Second state should have summary");
    }

    /**
     * Simplified assertion with default phase and final status.
     */
    public static void assertContextStatePair(
            List<ContextCompressionState> states,
            String processorType) {
        assertContextStatePair(states, processorType, "add_messages", "completed");
    }

    /**
     * Parse ContextCompressionState from a map representation.
     */
    private static ContextCompressionState parseStateFromMap(java.util.Map<String, Object> map) {
        ContextCompressionState state = new ContextCompressionState();
        state.setType((String) map.getOrDefault("type", CONTEXT_COMPRESSION_STATE_TYPE));
        state.setOperationId((String) map.get("operationId"));
        state.setStatus((String) map.get("status"));
        state.setPhase((String) map.get("phase"));
        state.setProcessor((String) map.getOrDefault("processor", ""));
        state.setModel((String) map.getOrDefault("model", ""));
        state.setDurationMs(map.get("durationMs") instanceof Number
                ? ((Number) map.get("durationMs")).intValue() : null);
        state.setContextMax(map.get("contextMax") instanceof Number
                ? ((Number) map.get("contextMax")).intValue() : null);
        state.setSummary((String) map.getOrDefault("summary", ""));
        state.setError((String) map.get("error"));
        return state;
    }

    /**
     * Result container for captureContextCompressionStates.
     * Holds the action result and the list of captured states.
     */
    public static class CaptureResult {
        private final Object result;
        private final List<ContextCompressionState> states;

        public CaptureResult(Object result, List<ContextCompressionState> states) {
            this.result = result;
            this.states = states;
        }

        public Object getResult() {
            return result;
        }

        public List<ContextCompressionState> getStates() {
            return states;
        }
    }
}