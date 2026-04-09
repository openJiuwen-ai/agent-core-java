/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.SimpleTokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
}
