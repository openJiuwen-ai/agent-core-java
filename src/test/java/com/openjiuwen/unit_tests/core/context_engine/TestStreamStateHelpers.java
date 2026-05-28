/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import com.openjiuwen.core.context.schema.ContextCompressionMetric;
import com.openjiuwen.core.context.schema.ContextCompressionState;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for stream state helpers utility class.
 * <p>
 * Mirrors Python's stream state helpers tests from
 * {@code tests/unit_tests/core/context_engine/_stream_state_helpers.py}.
 */
class TestStreamStateHelpers {

    @Test
    @Tag("level0")
    void testStreamStateHelpersClassExists() {
        assertNotNull(StreamStateHelpers.class);
    }

    @Test
    @Tag("level0")
    void testCaptureResultClassExists() {
        assertNotNull(StreamStateHelpers.CaptureResult.class);
    }

    @Test
    @Tag("level0")
    void testCaptureResultFields() {
        List<ContextCompressionState> states = List.of(
                createTestState("started", "test_processor", "add_messages"),
                createTestState("completed", "test_processor", "add_messages")
        );
        StreamStateHelpers.CaptureResult result = new StreamStateHelpers.CaptureResult("testResult", states);

        assertEquals("testResult", result.getResult());
        assertEquals(2, result.getStates().size());
    }

    @Test
    @Tag("level1")
    void testAssertContextStatePairWithValidStates() {
        ContextCompressionState startedState = createTestState("started", "FullCompactProcessor", "add_messages");
        startedState.setBefore(createTestMetric(1000));

        ContextCompressionState completedState = createTestState("completed", "FullCompactProcessor", "add_messages");
        completedState.setDurationMs(500);
        completedState.setSummary("Test summary");

        List<ContextCompressionState> states = List.of(startedState, completedState);

        StreamStateHelpers.assertContextStatePair(states, "FullCompactProcessor");
    }

    @Test
    @Tag("level1")
    void testAssertContextStatePairWithCustomPhaseAndStatus() {
        ContextCompressionState startedState = createTestState("started", "MicroCompactProcessor", "active_compress");
        startedState.setBefore(createTestMetric(1000));

        ContextCompressionState completedState = createTestState("skipped", "MicroCompactProcessor", "active_compress");
        completedState.setDurationMs(100);
        completedState.setSummary("Skipped due to budget");

        List<ContextCompressionState> states = List.of(startedState, completedState);

        StreamStateHelpers.assertContextStatePair(states, "MicroCompactProcessor", "active_compress", "skipped");
    }

    @Test
    @Tag("level1")
    void testAssertContextStatePairFailsOnWrongStatusSequence() {
        ContextCompressionState state1 = createTestState("started", "TestProcessor", "add_messages");
        ContextCompressionState state2 = createTestState("failed", "TestProcessor", "add_messages");

        List<ContextCompressionState> states = List.of(state1, state2);

        assertThrows(AssertionError.class,
                () -> StreamStateHelpers.assertContextStatePair(states, "TestProcessor", "add_messages", "completed"));
    }

    @Test
    @Tag("level1")
    void testAssertContextStatePairFailsOnWrongProcessorType() {
        ContextCompressionState state1 = createTestState("started", "ProcessorA", "add_messages");
        ContextCompressionState state2 = createTestState("completed", "ProcessorB", "add_messages");

        List<ContextCompressionState> states = List.of(state1, state2);

        assertThrows(AssertionError.class,
                () -> StreamStateHelpers.assertContextStatePair(states, "ProcessorA"));
    }

    @Test
    @Tag("level1")
    void testAssertContextStatePairFailsOnWrongPhase() {
        ContextCompressionState state1 = createTestState("started", "TestProcessor", "phase_a");
        ContextCompressionState state2 = createTestState("completed", "TestProcessor", "phase_b");

        List<ContextCompressionState> states = List.of(state1, state2);

        assertThrows(AssertionError.class,
                () -> StreamStateHelpers.assertContextStatePair(states, "TestProcessor", "phase_a"));
    }

    @Test
    @Tag("level1")
    void testAssertContextStatePairFailsOnMissingBeforeTime() {
        ContextCompressionState state1 = createTestState("started", "TestProcessor", "add_messages");
        state1.setBefore(null);  // Missing before

        ContextCompressionState state2 = createTestState("completed", "TestProcessor", "add_messages");
        state2.setDurationMs(100);
        state2.setSummary("Summary");

        List<ContextCompressionState> states = List.of(state1, state2);

        assertThrows(AssertionError.class,
                () -> StreamStateHelpers.assertContextStatePair(states, "TestProcessor"));
    }

    @Test
    @Tag("level1")
    void testAssertContextStatePairFailsOnMissingDuration() {
        ContextCompressionState state1 = createTestState("started", "TestProcessor", "add_messages");
        state1.setBefore(createTestMetric(1000));

        ContextCompressionState state2 = createTestState("completed", "TestProcessor", "add_messages");
        state2.setDurationMs(null);  // Missing duration
        state2.setSummary("Summary");

        List<ContextCompressionState> states = List.of(state1, state2);

        assertThrows(AssertionError.class,
                () -> StreamStateHelpers.assertContextStatePair(states, "TestProcessor"));
    }

    @Test
    @Tag("level1")
    void testAssertContextStatePairFailsOnEmptySummary() {
        ContextCompressionState state1 = createTestState("started", "TestProcessor", "add_messages");
        state1.setBefore(createTestMetric(1000));

        ContextCompressionState state2 = createTestState("completed", "TestProcessor", "add_messages");
        state2.setDurationMs(100);
        state2.setSummary("");  // Empty summary

        List<ContextCompressionState> states = List.of(state1, state2);

        assertThrows(AssertionError.class,
                () -> StreamStateHelpers.assertContextStatePair(states, "TestProcessor"));
    }

    // Helper methods

    private ContextCompressionState createTestState(String status, String processor, String phase) {
        ContextCompressionState state = new ContextCompressionState();
        state.setStatus(status);
        state.setProcessor(processor);
        state.setPhase(phase);
        return state;
    }

    private ContextCompressionMetric createTestMetric(int time) {
        ContextCompressionMetric metric = new ContextCompressionMetric();
        metric.setTime(String.valueOf(time));
        return metric;
    }
}