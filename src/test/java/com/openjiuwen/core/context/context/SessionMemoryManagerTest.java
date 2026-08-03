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
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SessionMemoryManager}.
 *
 * <p>Note: {@code Session} is {@code final} with no-op {@code getState}/{@code updateState},
 * so tests that depend on session state persistence verify the logic indirectly.
 * Static utility methods ({@code groupCompletedApiRounds}, {@code findMessageIndexByContextMessageId},
 * {@code truncateContextWindowToCompletedApiRound}) are fully testable without session state.</p>
 */
class SessionMemoryManagerTest {

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
    @DisplayName("countToolCalls counts tool calls in assistant messages")
    void testCountToolCalls() {
        List<BaseMessage> messages = List.of(
                new UserMessage("q1"),
                AssistantMessage.builder().content("").toolCalls(List.of(
                        ToolCall.builder().id("tc-1").name("grep").arguments("{}").build(),
                        ToolCall.builder().id("tc-2").name("find").arguments("{}").build()
                )).build(),
                ToolMessage.builder().content("r1").toolCallId("tc-1").name("grep").build(),
                new UserMessage("q2"),
                new AssistantMessage("a2")
        );

        assertEquals(2, SessionMemoryManager.countToolCalls(messages));
    }

    @Test
    @DisplayName("buildSessionMemoryRuntime constructs correct runtime map")
    void testBuildSessionMemoryRuntime() {
        Map<String, Object> runtime = SessionMemoryManager.buildSessionMemoryRuntime(
                "/tmp/memory.md", "/tmp/pending.md", true, 100, 2, 5, "msg-1", true);

        assertEquals("/tmp/memory.md", runtime.get("memory_path"));
        assertEquals("/tmp/pending.md", runtime.get("pending_memory_path"));
        assertEquals(true, runtime.get("initialized"));
        assertEquals(true, runtime.get("is_extracting"));
        assertEquals(100, runtime.get("tokens_at_last_update"));
        assertEquals(2, runtime.get("tool_calls_at_last_update"));
        assertEquals(5, runtime.get("last_summarized_message_count"));
        assertEquals("msg-1", runtime.get("notes_upto_message_id"));
    }

    @Test
    @DisplayName("groupCompletedApiRounds with single assistant without tool calls")
    void testGroupCompletedApiRoundsNoToolCalls() {
        List<BaseMessage> messages = List.of(
                new UserMessage("q1"),
                new AssistantMessage("a1")
        );

        List<int[]> rounds = SessionMemoryManager.groupCompletedApiRounds(messages);
        assertEquals(1, rounds.size());
        assertArrayEquals(new int[]{0, 2}, rounds.get(0));
    }

    @Test
    @DisplayName("groupCompletedApiRounds with empty messages returns empty list")
    void testGroupCompletedApiRoundsEmpty() {
        List<int[]> rounds = SessionMemoryManager.groupCompletedApiRounds(List.of());
        assertTrue(rounds.isEmpty());
    }

    @Test
    @DisplayName("findLastCompletedApiRoundEnd returns 0 for no completed rounds")
    void testFindLastCompletedApiRoundEndEmpty() {
        assertEquals(0, SessionMemoryManager.findLastCompletedApiRoundEnd(List.of()));
    }

    private static void setMetadata(BaseMessage message, String messageId) throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ContextUtils.CONTEXT_MESSAGE_ID_KEY, messageId);
        var setter = message.getClass().getMethod("setMetadata", Map.class);
        setter.invoke(message, metadata);
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
}
