/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SessionMemoryManager}.
 */
class SessionMemoryManagerTest {

    @Test
    @DisplayName("session memory runtime round-trips through session state")
    void testSessionMemoryRuntimeRoundTrip() {
        RecordingSession session = new RecordingSession();

        SessionMemoryManager.updateSessionMemoryRuntime(session, Map.of(
                "memory_path", "/tmp/session.md",
                "initialized", true,
                "tokens_at_last_update", 100
        ));

        Map<String, Object> runtime = SessionMemoryManager.getSessionMemoryRuntime(session);
        assertEquals("/tmp/session.md", runtime.get("memory_path"));
        assertEquals(true, runtime.get("initialized"));
        assertEquals(100, runtime.get("tokens_at_last_update"));
    }

    @Test
    @DisplayName("invalidateSessionMemoryAnchor resets anchor fields")
    void testInvalidateSessionMemoryAnchor() {
        RecordingSession session = new RecordingSession();
        SessionMemoryManager.updateSessionMemoryRuntime(session, Map.of(
                "tokens_at_last_update", 100,
                "last_summarized_message_count", 4,
                "notes_upto_message_id", "msg-1"
        ));

        SessionMemoryManager.invalidateSessionMemoryAnchor(session);
        Map<String, Object> runtime = SessionMemoryManager.getSessionMemoryRuntime(session);

        assertEquals(0, runtime.get("tokens_at_last_update"));
        assertEquals(0, runtime.get("last_summarized_message_count"));
        assertTrue(runtime.containsKey("notes_upto_message_id"));
        assertNull(runtime.get("notes_upto_message_id"));
    }

    @Test
    @DisplayName("findMessageIndexByContextMessageId finds metadata id")
    void testFindMessageIndexByContextMessageId() throws Exception {
        BaseMessage first = new UserMessage("one");
        BaseMessage second = new UserMessage("two");
        setMetadata(first, "msg-1");
        setMetadata(second, "msg-2");

        assertEquals(1, SessionMemoryManager.findMessageIndexByContextMessageId(List.of(first, second), "msg-2"));
        assertEquals(-1, SessionMemoryManager.findMessageIndexByContextMessageId(List.of(first, second), "missing"));
    }

    @Test
    @DisplayName("groupCompletedApiRounds groups user tool assistant spans")
    void testGroupCompletedApiRounds() {
        List<BaseMessage> messages = List.of(
                new UserMessage("q1"),
                AssistantMessage.builder().content("").toolCalls(List.of(
                        ToolCall.builder().id("tc-1").name("grep").arguments("{}").build()
                )).build(),
                ToolMessage.builder().content("r1").toolCallId("tc-1").name("grep").build(),
                new UserMessage("q2"),
                new AssistantMessage("a2")
        );

        List<int[]> rounds = SessionMemoryManager.groupCompletedApiRounds(messages);

        assertEquals(2, rounds.size());
        assertArrayEquals(new int[]{0, 3}, rounds.get(0));
        assertArrayEquals(new int[]{3, 5}, rounds.get(1));
        assertEquals(5, SessionMemoryManager.findLastCompletedApiRoundEnd(messages));
    }

    @Test
    @DisplayName("truncateContextWindowToCompletedApiRound drops incomplete tail")
    void testTruncateContextWindowToCompletedApiRound() {
        ContextWindow window = ContextWindow.builder()
                .systemMessages(List.of())
                .contextMessages(List.of(
                        new UserMessage("q1"),
                        new AssistantMessage("a1"),
                        new UserMessage("q2"),
                        AssistantMessage.builder().content("").toolCalls(List.of(
                                ToolCall.builder().id("tc-1").name("grep").arguments("{}").build()
                        )).build()
                ))
                .tools(List.of())
                .build();

        ContextWindow truncated = SessionMemoryManager.truncateContextWindowToCompletedApiRound(window);

        assertEquals(2, truncated.getContextMessages().size());
        assertEquals("a1", truncated.getContextMessages().get(1).getContentAsString());
    }

    @Test
    @DisplayName("shouldUpdate follows init, delta token, tool-call and shrink reset thresholds")
    void testShouldUpdateThresholdsAndBaselineReset() {
        RecordingSession session = new RecordingSession();
        TestContext context = new TestContext(120);
        SessionMemoryManager manager = new SessionMemoryManager(SessionMemoryConfig.builder()
                .triggerTokens(100)
                .triggerAddTokens(50)
                .toolMin(2)
                .build());
        ContextWindow initialWindow = ContextWindow.builder()
                .systemMessages(List.of())
                .contextMessages(List.of(new UserMessage("q"), new AssistantMessage("a")))
                .tools(List.of())
                .build();

        assertTrue(manager.shouldUpdate(session, context, initialWindow));
        assertEquals(true, SessionMemoryManager.getSessionMemoryRuntime(session).get("initialized"));

        SessionMemoryManager.updateSessionMemoryRuntime(session, Map.of(
                "tokens_at_last_update", 100,
                "tool_calls_at_last_update", 1
        ));
        context.tokenCount = 130;
        assertFalse(manager.shouldUpdate(session, context, toolWindow(3)));

        context.tokenCount = 170;
        assertTrue(manager.shouldUpdate(session, context, toolWindow(3)));

        SessionMemoryManager.updateSessionMemoryRuntime(session, Map.of(
                "tokens_at_last_update", 500,
                "tool_calls_at_last_update", 5
        ));
        context.tokenCount = 120;
        assertTrue(manager.shouldUpdate(session, context, toolWindow(3)));
        Map<String, Object> runtime = SessionMemoryManager.getSessionMemoryRuntime(session);
        assertEquals(0, runtime.get("tokens_at_last_update"));
        assertEquals(0, runtime.get("tool_calls_at_last_update"));
    }

    @Test
    @DisplayName("maybeScheduleUpdate records paths and extraction flag when thresholds pass")
    void testMaybeScheduleUpdateRecordsRuntime() {
        RecordingSession session = new RecordingSession();
        TestContext context = new TestContext(120);
        context.setMessages(List.of(new UserMessage("q"), new AssistantMessage("a")));
        SessionMemoryManager manager = new SessionMemoryManager(SessionMemoryConfig.builder()
                .triggerTokens(100)
                .triggerAddTokens(50)
                .toolMin(1)
                .build());

        assertTrue(manager.maybeScheduleUpdate(session, context, new WorkspaceLike("/tmp/workspace")));
        Map<String, Object> runtime = SessionMemoryManager.getSessionMemoryRuntime(session);
        assertEquals(true, runtime.get("is_extracting"));
        assertTrue(String.valueOf(runtime.get("memory_path")).endsWith(
                "context/session-1_context/session_memory/session_context.md"));
        assertTrue(String.valueOf(runtime.get("pending_memory_path")).endsWith("session_context.pending.md"));
    }

    private static void setMetadata(BaseMessage message, String messageId) throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ContextUtils.CONTEXT_MESSAGE_ID_KEY, messageId);
        var setter = message.getClass().getMethod("setMetadata", Map.class);
        setter.invoke(message, metadata);
    }

    private static class RecordingSession implements Session {
        private final Map<String, Object> state = new HashMap<>();

        @Override
        public String getSessionId() {
            return "session-1";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }

    private static ContextWindow toolWindow(int toolCalls) {
        List<ToolCall> calls = new ArrayList<>();
        for (int index = 0; index < toolCalls; index++) {
            calls.add(ToolCall.builder().id("tc-" + index).name("tool").arguments("{}").build());
        }
        return ContextWindow.builder()
                .systemMessages(List.of())
                .contextMessages(List.of(new UserMessage("q"), AssistantMessage.builder().content("").toolCalls(calls).build()))
                .tools(List.of())
                .build();
    }

    private static final class WorkspaceLike {
        private final String rootPath;

        private WorkspaceLike(String rootPath) {
            this.rootPath = rootPath;
        }

        public String getRootPath() {
            return rootPath;
        }
    }

    private static final class TestContext extends ModelContext {
        private int tokenCount;
        private List<BaseMessage> messages = new ArrayList<>();

        private TestContext(int tokenCount) {
            this.tokenCount = tokenCount;
        }

        @Override
        public int size() {
            return messages.size();
        }

        @Override
        public List<BaseMessage> addMessages(List<BaseMessage> messages) {
            this.messages.addAll(messages);
            return messages;
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            List<BaseMessage> popped = new ArrayList<>(messages.subList(Math.max(0, messages.size() - size), messages.size()));
            messages = new ArrayList<>(messages.subList(0, Math.max(0, messages.size() - size)));
            return popped;
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            if (size == null || size >= messages.size()) {
                return new ArrayList<>(messages);
            }
            return new ArrayList<>(messages.subList(messages.size() - size, messages.size()));
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            this.messages = new ArrayList<>(messages);
        }

        @Override
        public void clearMessages(boolean withHistory) {
            messages.clear();
        }

        @Override
        public ContextStats statistic() {
            ContextStats stats = new ContextStats();
            stats.setTotalMessages(messages.size());
            return stats;
        }

        @Override
        public String sessionId() {
            return "session-1";
        }

        @Override
        public String contextId() {
            return "ctx";
        }

        @Override
        public ContextWindow getContextWindow(
                List<BaseMessage> systemMessages,
                List<ToolInfo> tools,
                Integer windowSize,
                Integer dialogueRound,
                Map<String, Object> kwargs) {
            return ContextWindow.builder()
                    .systemMessages(systemMessages == null ? List.of() : systemMessages)
                    .contextMessages(new ArrayList<>(messages))
                    .tools(tools == null ? List.of() : tools)
                    .build();
        }

        @Override
        public TokenCounter tokenCounter() {
            return new TokenCounter() {
                @Override
                public int count(String text, String model) {
                    return tokenCount;
                }

                @Override
                public int countMessages(List<BaseMessage> messages, String model) {
                    return tokenCount;
                }

                @Override
                public int countTools(List<ToolInfo> tools, String model) {
                    return 0;
                }
            };
        }

        @Override
        public Tool reloaderTool() {
            return null;
        }
    }
}
