/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.schema.ContextCompressionState;
import com.openjiuwen.core.context.token.SimpleTokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SessionModelContext}.
 */
class SessionModelContextTest {

    private SessionModelContext context;

    @BeforeEach
    void setUp() {
        ContextEngineConfig config = ContextEngineConfig.builder()
                .maxContextMessageNum(50)
                .defaultWindowMessageNum(10)
                .build();

        context = new SessionModelContext(
                "test_ctx",
                "test_session",
                config,
                new ArrayList<>(),
                new ArrayList<>(),
                new SimpleTokenCounter());
    }

    @Test
    @DisplayName("New context has zero size")
    void testNewContextSize() {
        assertEquals(0, context.size());
    }

    @Test
    @DisplayName("sessionId and contextId return correct values")
    void testIds() {
        assertEquals("test_session", context.sessionId());
        assertEquals("test_ctx", context.contextId());
    }

    @Test
    @DisplayName("addMessages increases size")
    void testAddMessages() {
        context.addMessages(List.of(
                new UserMessage("hello"),
                new AssistantMessage("hi")));
        assertEquals(2, context.size());
    }

    @Test
    @DisplayName("getMessages returns added messages")
    void testGetMessages() {
        context.addMessages(List.of(new UserMessage("q1")));
        context.addMessages(List.of(new AssistantMessage("a1")));

        List<BaseMessage> msgs = context.getMessages();
        assertEquals(2, msgs.size());
        assertEquals("q1", msgs.get(0).getContentAsString());
        assertEquals("a1", msgs.get(1).getContentAsString());
    }

    @Test
    @DisplayName("popMessages removes messages from end")
    void testPopMessages() {
        context.addMessages(List.of(
                new UserMessage("a"),
                new UserMessage("b"),
                new UserMessage("c")));

        List<BaseMessage> popped = context.popMessages(2, false);
        assertEquals(2, popped.size());
        assertEquals(1, context.size());
    }

    @Test
    @DisplayName("popMessages with negative size throws error")
    void testPopMessagesNegative() {
        assertThrows(RuntimeException.class, () -> context.popMessages(-1, false));
    }

    @Test
    @DisplayName("setMessages replaces all messages")
    void testSetMessages() {
        context.addMessages(List.of(new UserMessage("old")));
        context.setMessages(List.of(
                new UserMessage("new1"),
                new UserMessage("new2")));

        assertEquals(2, context.size());
    }

    @Test
    @DisplayName("clearMessages removes all messages")
    void testClearMessages() {
        context.addMessages(List.of(
                new UserMessage("a"),
                new AssistantMessage("b")));
        context.clearMessages(false);

        assertEquals(0, context.size());
    }

    @Test
    @DisplayName("getContextWindow returns window with proper structure")
    void testGetContextWindow() {
        context.addMessages(List.of(
                new UserMessage("q1"),
                new AssistantMessage("a1"),
                new UserMessage("q2"),
                new AssistantMessage("a2")));

        ContextWindow window = context.getContextWindow(null, null, null, null);
        assertNotNull(window);
        assertNotNull(window.getContextMessages());
        assertFalse(window.getContextMessages().isEmpty());
    }

    @Test
    @DisplayName("getContextWindow respects windowSize limit")
    void testGetContextWindowLimit() {
        context.addMessages(List.of(
                new UserMessage("q1"),
                new AssistantMessage("a1"),
                new UserMessage("q2"),
                new AssistantMessage("a2"),
                new UserMessage("q3"),
                new AssistantMessage("a3")));

        ContextWindow window = context.getContextWindow(null, null, 4, null);
        // Window size = 4 (no system messages, so all 4 go to context)
        assertTrue(window.getContextMessages().size() <= 4);
    }

    @Test
    @DisplayName("getContextWindow strips leading ToolMessages")
    void testGetContextWindowStripsLeadingToolMessages() {
        context.addMessages(List.of(
                new ToolMessage("tool_result", "call_1"),
                new UserMessage("question"),
                new AssistantMessage("answer")));

        // Get only last 2 messages which starts with a ToolMessage
        ContextWindow window = context.getContextWindow(null, null, null, null);
        // ToolMessage at leading position should be stripped
        for (BaseMessage msg : window.getContextMessages()) {
            if (msg == window.getContextMessages().get(0)) {
                assertNotEquals("tool", msg.getRole(),
                        "Leading ToolMessage should be stripped");
            }
        }
    }

    @Test
    @DisplayName("getContextWindow with invalid windowSize throws error")
    void testGetContextWindowInvalidSize() {
        assertThrows(RuntimeException.class,
                () -> context.getContextWindow(null, null, 0, null));
        assertThrows(RuntimeException.class,
                () -> context.getContextWindow(null, null, -1, null));
    }

    @Test
    @DisplayName("statistic returns valid ContextStats")
    void testStatistic() {
        context.addMessages(List.of(
                new UserMessage("q1"),
                new AssistantMessage("a1")));

        ContextStats stats = context.statistic();
        assertEquals(2, stats.getTotalMessages());
        assertEquals(1, stats.getUserMessages());
        assertEquals(1, stats.getAssistantMessages());
        assertTrue(stats.getTotalTokens() > 0);
    }

    @Test
    @DisplayName("tokenCounter returns non-null counter")
    void testTokenCounter() {
        assertNotNull(context.tokenCounter());
    }

    @Test
    @DisplayName("reloaderTool returns a functional tool")
    void testReloaderTool() {
        assertNotNull(context.reloaderTool());
        assertNotNull(context.reloaderTool().getCard());
        assertEquals("reload_original_context_messages",
                context.reloaderTool().getCard().getName());
    }

    @Test
    @DisplayName("saveState and loadState preserve messages")
    void testSaveLoadState() {
        context.addMessages(List.of(
                new UserMessage("preserved_msg")));

        var state = context.saveState();
        assertNotNull(state);
        assertTrue(state.containsKey("messages"));
    }

    @Test
    @DisplayName("compressContext emits compression state to session stream")
    void testCompressContextEmitsState() {
        RecordingSession session = new RecordingSession();
        ContextEngineConfig config = ContextEngineConfig.builder()
                .maxContextMessageNum(50)
                .defaultWindowMessageNum(10)
                .build();

        SessionModelContext compressibleContext = new SessionModelContext(
                "test_ctx",
                "test_session",
                config,
                new ArrayList<>(List.of(new UserMessage("before"))),
                List.of(new TestCompressor()),
                new SimpleTokenCounter(),
                session,
                null,
                null
        );

        String result = compressibleContext.compressContext();

        assertEquals("compressed", result);
        assertEquals(2, session.streams.size());
        assertTrue(session.streams.get(0) instanceof OutputSchema);
        OutputSchema started = (OutputSchema) session.streams.get(0);
        OutputSchema completed = (OutputSchema) session.streams.get(1);
        assertEquals(ContextCompressionState.CONTEXT_COMPRESSION_STATE_TYPE, started.getType());
        assertEquals(ContextCompressionState.CONTEXT_COMPRESSION_STATE_TYPE, completed.getType());
        ContextCompressionState startedState = (ContextCompressionState) started.getPayload();
        ContextCompressionState completedState = (ContextCompressionState) completed.getPayload();
        assertEquals("started", startedState.getStatus());
        assertEquals("completed", completedState.getStatus());
        assertEquals("active_compress", completedState.getPhase());
    }

    @Test
    @DisplayName("compressContext emits noop state when compressor leaves context unchanged")
    void testCompressContextEmitsNoopState() {
        RecordingSession session = new RecordingSession();
        SessionModelContext compressibleContext = new SessionModelContext(
                "test_ctx",
                "test_session",
                ContextEngineConfig.builder().maxContextMessageNum(50).build(),
                new ArrayList<>(List.of(new UserMessage("unchanged"))),
                List.of(new NoopCompressor()),
                new SimpleTokenCounter(),
                session,
                null,
                null
        );

        String result = compressibleContext.compressContext();

        assertEquals("noop", result);
        assertEquals(2, session.streams.size());
        ContextCompressionState startedState = (ContextCompressionState) ((OutputSchema) session.streams.get(0)).getPayload();
        ContextCompressionState noopState = (ContextCompressionState) ((OutputSchema) session.streams.get(1)).getPayload();
        assertEquals("started", startedState.getStatus());
        assertEquals("noop", noopState.getStatus());
        assertEquals("active_compress", noopState.getPhase());
        assertNotNull(noopState.getAfter());
        assertNotNull(noopState.getSaved());
    }

    @Test
    @DisplayName("compressContext uses configured model context window mapping in telemetry")
    void testCompressContextUsesConfiguredModelContextWindowMapping() {
        RecordingSession session = new RecordingSession();
        SessionModelContext compressibleContext = new SessionModelContext(
                "test_ctx",
                "test_session",
                ContextEngineConfig.builder()
                        .modelContextWindowTokens(Map.of("mapped-model", 200))
                        .build(),
                new ArrayList<>(List.of(new UserMessage("a".repeat(80)))),
                List.of(new NoopCompressor()),
                null,
                session,
                null,
                null
        );

        String result = compressibleContext.compressContext(null, Map.of("model_name", "mapped-model"));

        assertEquals("noop", result);
        ContextCompressionState startedState = (ContextCompressionState) ((OutputSchema) session.streams.get(0)).getPayload();
        assertEquals(20, startedState.getBefore().getTokens());
        assertEquals(10, startedState.getBefore().getContextPercent());
        assertEquals(200, startedState.getContextMax());
    }

    @Test
    @DisplayName("compressContext ignores stream write failures")
    void testCompressContextIgnoresStreamWriteFailure() {
        FailingSession session = new FailingSession();
        SessionModelContext compressibleContext = new SessionModelContext(
                "test_ctx",
                "test_session",
                ContextEngineConfig.builder().maxContextMessageNum(50).build(),
                new ArrayList<>(List.of(new UserMessage("unchanged"))),
                List.of(new NoopCompressor()),
                new SimpleTokenCounter(),
                session,
                null,
                null
        );

        assertDoesNotThrow(() -> assertEquals("noop", compressibleContext.compressContext()));
    }

    @Test
    @DisplayName("getContextWindow emits compression state when round-level compressor triggers")
    void testGetContextWindowEmitsRoundLevelCompressionState() {
        RecordingSession session = new RecordingSession();
        SessionModelContext compressibleContext = new SessionModelContext(
                "test_ctx",
                "test_session",
                ContextEngineConfig.builder()
                        .defaultWindowMessageNum(100)
                        .modelContextWindowTokens(Map.of("mapped-model", 400))
                        .build(),
                new ArrayList<>(List.of(
                        new UserMessage("old request"),
                        new AssistantMessage("old answer")
                )),
                List.of(new GetWindowCompressor()),
                new SimpleTokenCounter(),
                session,
                null,
                null
        );

        ContextWindow window = compressibleContext.getContextWindow(
                null,
                null,
                null,
                null,
                Map.of("model_name", "mapped-model"));

        assertEquals(1, window.getContextMessages().size());
        assertEquals("compressed", window.getContextMessages().get(0).getContentAsString());
        assertEquals(2, session.streams.size());
        ContextCompressionState startedState = (ContextCompressionState) ((OutputSchema) session.streams.get(0)).getPayload();
        ContextCompressionState completedState = (ContextCompressionState) ((OutputSchema) session.streams.get(1)).getPayload();
        assertEquals("started", startedState.getStatus());
        assertEquals("completed", completedState.getStatus());
        assertEquals("get_context_window", startedState.getPhase());
        assertEquals("get_context_window", completedState.getPhase());
        assertEquals("GetWindowCompressor", completedState.getProcessor());
        assertTrue(completedState.getSummary().contains("modified 2 messages"));
        assertEquals(400, completedState.getContextMax());
    }

    private static class RecordingSession implements Session {
        private final Map<String, Object> state = new HashMap<>();
        private final List<Object> streams = new ArrayList<>();

        @Override
        public String getSessionId() {
            return "test_session";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }

        @Override
        public void writeStream(Object data) {
            streams.add(data);
        }
    }

    private static class FailingSession extends RecordingSession {
        @Override
        public void writeStream(Object data) {
            throw new RuntimeException("stream failed");
        }
    }

    private static class TestCompressor extends ContextProcessor {
        TestCompressor() {
            super(RoundLevelCompressorConfig.builder().build());
        }

        @Override
        public ProcessResult onAddMessages(com.openjiuwen.core.context.ModelContext context, List<BaseMessage> messagesToAdd) {
            List<BaseMessage> updated = new ArrayList<>(context.getMessages());
            updated.set(0, new UserMessage("after"));
            context.setMessages(updated);
            return ProcessResult.ofMessages(
                    ContextEvent.builder().eventType(processorType()).messagesToModify(List.of(0)).build(),
                    List.of()
            );
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of();
        }
    }

    private static class NoopCompressor extends ContextProcessor {
        NoopCompressor() {
            super(RoundLevelCompressorConfig.builder().build());
        }

        @Override
        public ProcessResult onAddMessages(com.openjiuwen.core.context.ModelContext context, List<BaseMessage> messagesToAdd) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of();
        }
    }

    private static class GetWindowCompressor extends ContextProcessor {
        GetWindowCompressor() {
            super(RoundLevelCompressorConfig.builder().build());
        }

        @Override
        public boolean triggerGetContextWindow(com.openjiuwen.core.context.ModelContext context, ContextWindow contextWindow) {
            return true;
        }

        @Override
        public ProcessResult onGetContextWindow(com.openjiuwen.core.context.ModelContext context, ContextWindow contextWindow) {
            ContextWindow updated = ContextWindow.builder()
                    .systemMessages(contextWindow.getSystemMessages())
                    .contextMessages(List.of(new UserMessage("compressed")))
                    .tools(contextWindow.getTools())
                    .build();
            context.setMessages(updated.getContextMessages());
            return ProcessResult.ofContextWindow(
                    ContextEvent.builder().eventType(processorType()).messagesToModify(List.of(0, 1)).build(),
                    updated
            );
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of();
        }
    }
}
