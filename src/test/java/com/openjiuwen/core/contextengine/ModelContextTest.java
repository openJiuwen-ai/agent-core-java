/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.contextengine;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.contextengine.schema.ContextEngineConfig;
import com.openjiuwen.core.contextengine.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ModelContext} implementations.
 * 
 * <p>Converted from Python: test_context_model.py</p>
 */
class ModelContextTest {

    /**
     * Fake token counter for testing.
     * Each message contributes exactly 10 tokens.
     * Each tool contributes exactly 100 tokens.
     */
    private static class FakeTokenCounter implements TokenCounter {
        @Override
        public int count(String text, String model) {
            return 0;
        }

        @Override
        public int countMessages(List<BaseMessage> messages, String model) {
            return 10 * messages.size();
        }

        @Override
        public int countTools(List<ToolInfo> tools, String model) {
            return 100 * tools.size();
        }
    }

    private ModelContext createContext(
            List<BaseMessage> history,
            int contextMessageLimit,
            TokenCounter tokenCounter) throws ExecutionException, InterruptedException {
        var contextEngine = new ContextEngine(
            ContextEngineConfig.builder()
                .defaultWindowMessageNum(contextMessageLimit)
                .build()
        );
        return contextEngine.createContext(
            "test_context",
            null,
            history,
            tokenCounter,
            null
        ).get();
    }

    private ModelContext createContext(List<BaseMessage> history, int contextMessageLimit) 
            throws ExecutionException, InterruptedException {
        return createContext(history, contextMessageLimit, null);
    }

    private ModelContext createContext() throws ExecutionException, InterruptedException {
        return createContext(null, 20, null);
    }

    /**
     * Test that add_messages validates message protocol.
     * 
     * <p>Python: test_add_messages_validates_message_protocol</p>
     * <p>Assertions: 2</p>
     */
    @Test
    void testAddMessagesValidatesMessageProtocol() throws ExecutionException, InterruptedException {
        var context = createContext();
        
        // Invalid message type should throw ExecutionException wrapping BaseError
        var ex1 = assertThrows(ExecutionException.class, () -> 
            context.addMessages((BaseMessage) null).get()
        );
        assertInstanceOf(BaseError.class, ex1.getCause());
        assertEquals(StatusCode.CONTEXT_MESSAGE_INVALID.getCode(), ((BaseError) ex1.getCause()).getCode());

        // List containing invalid element should throw BaseError
        var ex2 = assertThrows(ExecutionException.class, () -> {
            // Simulate adding a list with null element (not valid BaseMessage)
            context.addMessages(java.util.Arrays.asList(UserMessage.of("ok"), null)).get();
        });
        assertInstanceOf(BaseError.class, ex2.getCause());
        assertEquals(StatusCode.CONTEXT_MESSAGE_INVALID.getCode(), ((BaseError) ex2.getCause()).getCode());
    }

    /**
     * Test history boundary behavior with history flag.
     * 
     * <p>Python: test_history_boundary_with_history_flag</p>
     * <p>Assertions: 2</p>
     */
    @Test
    void testHistoryBoundaryWithHistoryFlag() throws ExecutionException, InterruptedException {
        var history = List.<BaseMessage>of(
            UserMessage.of("h0"),
            UserMessage.of("h1")
        );
        var context = createContext(history, 20);
        context.addMessages(List.of(
            UserMessage.of("n0"),
            UserMessage.of("n1")
        )).get();

        var expectedWithHistory = List.<BaseMessage>of(
            UserMessage.of("h0"),
            UserMessage.of("h1"),
            UserMessage.of("n0"),
            UserMessage.of("n1")
        );
        var expectedWithoutHistory = List.<BaseMessage>of(
            UserMessage.of("n0"),
            UserMessage.of("n1")
        );
        
        assertEquals(expectedWithHistory, context.getMessages(null, true));
        assertEquals(expectedWithoutHistory, context.getMessages(null, false));
    }

    /**
     * Test that set_messages preserves or replaces history.
     * 
     * <p>Python: test_set_messages_preserves_or_replaces_history</p>
     * <p>Assertions: 4</p>
     */
    @Test
    void testSetMessagesPreservesOrReplacesHistory() throws ExecutionException, InterruptedException {
        var history = List.<BaseMessage>of(
            UserMessage.of("h0"),
            UserMessage.of("h1")
        );
        var context = createContext(history, 20);
        context.addMessages(UserMessage.of("n0")).get();

        // Replace only the new segment; keep history intact.
        context.setMessages(List.of(UserMessage.of("n1")), false);
        
        var expectedWithHistory = List.<BaseMessage>of(
            UserMessage.of("h0"),
            UserMessage.of("h1"),
            UserMessage.of("n1")
        );
        assertEquals(expectedWithHistory, context.getMessages(null, true));
        assertEquals(List.<BaseMessage>of(UserMessage.of("n1")), context.getMessages(null, false));

        // Replace the whole sequence; history boundary resets.
        context.setMessages(List.of(UserMessage.of("all")), true);
        assertEquals(List.<BaseMessage>of(UserMessage.of("all")), context.getMessages(null, true));
        assertEquals(List.<BaseMessage>of(UserMessage.of("all")), context.getMessages(null, false));
    }

    /**
     * Test that pop_messages respects withHistory flag.
     * 
     * <p>Python: test_pop_messages_respects_with_history</p>
     * <p>Assertions: 4</p>
     */
    @Test
    void testPopMessagesRespectsWithHistory() throws ExecutionException, InterruptedException {
        var history = List.<BaseMessage>of(
            UserMessage.of("h0"),
            UserMessage.of("h1")
        );
        var context = createContext(history, 20);
        context.addMessages(List.of(
            UserMessage.of("n0"),
            UserMessage.of("n1")
        )).get();

        // Pop only from the new segment.
        var poppedNew = context.popMessages(10, false);
        assertEquals(
            List.<BaseMessage>of(UserMessage.of("n0"), UserMessage.of("n1")),
            poppedNew
        );
        assertEquals(history, context.getMessages(null, true));
        assertEquals(List.<BaseMessage>of(), context.getMessages(null, false));

        // Pop from history as well.
        var poppedAll = context.popMessages(10, true);
        assertEquals(history, poppedAll);
        assertEquals(List.<BaseMessage>of(), context.getMessages(null, true));
    }

    /**
     * Test window assembly with system messages first and message limit.
     * 
     * <p>Python: test_window_assembly_system_first_and_message_limit</p>
     * <p>Assertions: 2</p>
     */
    @Test
    void testWindowAssemblySystemFirstAndMessageLimit() throws ExecutionException, InterruptedException {
        // Window size = 4: system messages take priority (up to window_size),
        // remaining capacity is used for latest context messages.
        var context = createContext(null, 4, null);
        context.addMessages(List.of(
            UserMessage.of("u0"),
            UserMessage.of("u1"),
            UserMessage.of("u2")
        )).get();
        
        var system = List.<BaseMessage>of(
            SystemMessage.of("s0"),
            SystemMessage.of("s1"),
            SystemMessage.of("s2")
        );

        var window = context.getContextWindow(system, null, null).get();
        
        // System messages take 3 slots, leaving 1 for context
        assertEquals(system.subList(0, 3), window.getSystemMessages());
        assertEquals(List.<BaseMessage>of(UserMessage.of("u2")), window.getContextMessages());
    }

    /**
     * Test that window validation drops leading tool messages or empties the window.
     * 
     * <p>Python: test_window_validation_drops_leading_tool_messages_or_empties</p>
     * <p>Assertions: 3</p>
     */
    @Test
    void testWindowValidationDropsLeadingToolMessagesOrEmpties() throws ExecutionException, InterruptedException {
        var context = createContext(null, 10, null);

        var toolMsgs = List.<BaseMessage>of(
            ToolMessage.of("tool-0", "tc-0"),
            ToolMessage.of("tool-1", "tc-1")
        );
        
        var allMessages = new java.util.ArrayList<BaseMessage>();
        allMessages.addAll(toolMsgs);
        allMessages.add(UserMessage.of("u0"));
        allMessages.add(UserMessage.of("u1"));
        
        context.addMessages(allMessages).get();
        
        var window = context.getContextWindow(
            List.of(SystemMessage.of("sys")), 
            null, 
            null
        ).get();
        
        // Leading tool messages should be dropped
        assertFalse(window.getContextMessages().stream()
            .anyMatch(m -> m instanceof ToolMessage));
        assertEquals(
            List.<BaseMessage>of(UserMessage.of("u0"), UserMessage.of("u1")),
            window.getContextMessages()
        );

        // If window has only tool messages, it becomes empty.
        var onlyTools = createContext(null, 10, null);
        onlyTools.addMessages(toolMsgs).get();
        var window2 = onlyTools.getContextWindow(null, null, null).get();
        assertEquals(List.<BaseMessage>of(), window2.getContextMessages());
    }

    /**
     * Test statistics counts roles and token counter injection.
     * 
     * <p>Python: test_statistics_counts_roles_and_token_counter_injection</p>
     * <p>Assertions: 12</p>
     */
    @Test
    void testStatisticsCountsRolesAndTokenCounterInjection() throws ExecutionException, InterruptedException {
        var tokenCounter = new FakeTokenCounter();
        var context = createContext(null, 10, tokenCounter);

        var messages = List.<BaseMessage>of(
            SystemMessage.of("s"),
            UserMessage.of("u"),
            AssistantMessage.of("a"),
            ToolMessage.of("t", "tc")
        );
        context.addMessages(messages).get();

        var tools = List.of(
            ToolInfo.builder().name("t0").build(),
            ToolInfo.builder().name("t1").build()
        );
        var window = context.getContextWindow(null, tools, null).get();
        var stat = window.getStatistic();

        assertEquals(4, stat.getTotalMessages());
        assertEquals(1, stat.getSystemMessages());
        assertEquals(1, stat.getUserMessages());
        assertEquals(1, stat.getAssistantMessages());
        assertEquals(1, stat.getToolMessages());
        assertEquals(2, stat.getTools());

        // 4 messages * 10 + 2 tools * 100
        assertEquals(4 * 10 + 2 * 100, stat.getTotalTokens());
        assertEquals(2 * 100, stat.getToolTokens());
        assertEquals(10, stat.getSystemMessageTokens());
        assertEquals(10, stat.getUserMessageTokens());
        assertEquals(10, stat.getAssistantMessageTokens());
        assertEquals(10, stat.getToolMessageTokens());
    }
}

