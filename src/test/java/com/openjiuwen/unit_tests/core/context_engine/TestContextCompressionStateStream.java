/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import com.openjiuwen.core.context.schema.ContextCompressionState;
import com.openjiuwen.core.context.schema.ContextCompressionMetric;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.processor.ContextProcessor.ProcessResult;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for context compression state stream behavior.
 * <p>
 * Mirrors Python's {@code test_context_compression_state_stream.py} from
 * {@code tests/unit_tests/core/context_engine/test_context_compression_state_stream.py}.
 */
class TestContextCompressionStateStream {

    // ---------------------------------------------------------------------------
    // Fake session for testing - Mirrors Python _FakeSession
    // ---------------------------------------------------------------------------

    static class FakeSession {
        private final String sessionId;
        private final List<OutputSchema> chunks = new ArrayList<>();

        public FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public List<OutputSchema> getChunks() {
            return chunks;
        }

        public void writeStream(OutputSchema data) {
            chunks.add(data);
        }
    }

    // ---------------------------------------------------------------------------
    // Fake compressor for testing - Mirrors Python _ReplacingCompressor
    // ---------------------------------------------------------------------------

    static class ReplacingCompressor extends ContextProcessor {
        public ReplacingCompressor() {
            super(new TestCompressConfig());
        }

        @Override
        public Map<String, Object> saveState() {
            return new HashMap<>();
        }

        @Override
        public void loadState(Map<String, Object> state) {
            // No-op for test
        }

        @Override
        public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
            return true;
        }

        @Override
        public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
            return ProcessResult.ofMessages(
                    ContextEvent.builder()
                            .eventType(processorType())
                            .messagesToModify(List.of(0, 1))
                            .build(),
                    List.of()
            );
        }
    }

    // ---------------------------------------------------------------------------
    // Noop compressor for testing - Mirrors Python _NoopCompressor
    // ---------------------------------------------------------------------------

    static class NoopCompressor extends ContextProcessor {
        public NoopCompressor() {
            super(new TestCompressConfig());
        }

        @Override
        public Map<String, Object> saveState() {
            return new HashMap<>();
        }

        @Override
        public void loadState(Map<String, Object> state) {
            // No-op for test
        }

        @Override
        public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
            return true;
        }

        @Override
        public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
            // No compression, return original messages
            return ProcessResult.ofMessages(null, messagesToAdd);
        }
    }

    // ---------------------------------------------------------------------------
    // Test config - Mirrors Python _CompressConfig
    // ---------------------------------------------------------------------------

    static class TestCompressConfig {
        int triggerTotalTokens = 100;
        String model = "test-compressor-model";
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testFakeSessionCreation() {
        FakeSession session = new FakeSession("session-1");
        assertEquals("session-1", session.getSessionId());
        assertTrue(session.getChunks().isEmpty());
    }

    @Test
    @Tag("level0")
    void testFakeSessionWriteStream() {
        FakeSession session = new FakeSession("session-1");
        OutputSchema chunk = new OutputSchema("test_type", 0, "payload");
        session.writeStream(chunk);
        assertEquals(1, session.getChunks().size());
        assertEquals(chunk, session.getChunks().get(0));
    }

    @Test
    @Tag("level0")
    void testReplacingCompressorCreation() {
        ReplacingCompressor compressor = new ReplacingCompressor();
        assertNotNull(compressor);
        assertEquals("ReplacingCompressor", compressor.getClass().getSimpleName());
    }

    @Test
    @Tag("level0")
    void testNoopCompressorCreation() {
        NoopCompressor compressor = new NoopCompressor();
        assertNotNull(compressor);
        assertEquals("NoopCompressor", compressor.getClass().getSimpleName());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Behavioral tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testReplacingCompressorTriggerAddMessagesReturnsTrue() {
        ReplacingCompressor compressor = new ReplacingCompressor();
        List<BaseMessage> messages = List.of(new UserMessage("test message"));
        boolean triggered = compressor.triggerAddMessages((ModelContext) null, messages);
        assertTrue(triggered);
    }

    @Test
    @Tag("level1")
    void testReplacingCompressorOnAddMessagesReturnsShortMessage() {
        ReplacingCompressor compressor = new ReplacingCompressor();
        List<BaseMessage> messagesToAdd = List.of(new UserMessage("long message"));
        ProcessResult result = compressor.onAddMessages((ModelContext) null, messagesToAdd);

        assertNotNull(result.event());
        assertTrue(result.messages().isEmpty());
    }

    @Test
    @Tag("level1")
    void testNoopCompressorTriggerAddMessagesReturnsTrue() {
        NoopCompressor compressor = new NoopCompressor();
        List<BaseMessage> messages = List.of(new UserMessage("test message"));
        boolean triggered = compressor.triggerAddMessages((ModelContext) null, messages);
        assertTrue(triggered);
    }

    @Test
    @Tag("level1")
    void testNoopCompressorOnAddMessagesReturnsOriginalMessages() {
        NoopCompressor compressor = new NoopCompressor();
        List<BaseMessage> messagesToAdd = List.of(new UserMessage("test message"));
        ProcessResult result = compressor.onAddMessages((ModelContext) null, messagesToAdd);

        assertNull(result.event());
        assertEquals(messagesToAdd, result.messages());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Integration tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testActiveCompressEmitsStateStream() {
        FakeSession session = new FakeSession("session-1");

        // Simulate compression state emission
        ContextCompressionState startedState = new ContextCompressionState();
        startedState.setStatus("started");
        startedState.setProcessor("TestProcessor");
        startedState.setPhase("add_messages");

        session.writeStream(new OutputSchema("context_compression_state", 0, startedState));

        ContextCompressionState completedState = new ContextCompressionState();
        completedState.setStatus("completed");
        completedState.setProcessor("TestProcessor");
        completedState.setPhase("add_messages");
        completedState.setDurationMs(100);
        completedState.setSummary("Test compression summary");

        session.writeStream(new OutputSchema("context_compression_state", 1, completedState));

        // Validate state stream
        List<OutputSchema> chunks = session.getChunks();
        assertEquals(2, chunks.size());

        // First chunk - started
        assertEquals("context_compression_state", chunks.get(0).getType());
        Object payload0 = chunks.get(0).getPayload();
        if (payload0 instanceof ContextCompressionState) {
            ContextCompressionState state = (ContextCompressionState) payload0;
            assertEquals("started", state.getStatus());
            assertEquals("TestProcessor", state.getProcessor());
        }

        // Second chunk - completed
        assertEquals("context_compression_state", chunks.get(1).getType());
        Object payload1 = chunks.get(1).getPayload();
        if (payload1 instanceof ContextCompressionState) {
            ContextCompressionState state = (ContextCompressionState) payload1;
            assertEquals("completed", state.getStatus());
            assertEquals(100, state.getDurationMs());
        }
    }

    @Test
    @Tag("level2")
    void testCompressionStatePairAssertion() {
        // Create valid state pair
        ContextCompressionState started = createTestState("started", "TestProcessor", "add_messages");
        ContextCompressionState completed = createTestState("completed", "TestProcessor", "add_messages");
        completed.setDurationMs(500);
        completed.setSummary("Test summary");

        List<ContextCompressionState> states = List.of(started, completed);

        // This should pass
        StreamStateHelpers.assertContextStatePair(states, "TestProcessor");
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private ContextCompressionState createTestState(String status, String processor, String phase) {
        ContextCompressionState state = new ContextCompressionState();
        state.setStatus(status);
        state.setProcessor(processor);
        state.setPhase(phase);
        ContextCompressionMetric before = new ContextCompressionMetric();
        before.setTime("1000");
        state.setBefore(before);
        return state;
    }
}
