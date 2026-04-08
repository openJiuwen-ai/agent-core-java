/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.context;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link ModelContext} (via {@link SessionModelContext}).
 * <p>
 * Ported from Python's {@code test_context_model.py}.
 */
class ModelContextTest {

    // ===================== Helper Methods =====================

    private ModelContext createContext() {
        return createContext(null, 100, null, null, false, false, null);
    }

    private ModelContext createContext(List<BaseMessage> history) {
        return createContext(history, 100, null, null, false, false, null);
    }

    private ModelContext createContext(List<BaseMessage> history, int windowMessageLimit) {
        return createContext(history, windowMessageLimit, null, null, false, false, null);
    }

    private ModelContext createContext(
            List<BaseMessage> history,
            int windowMessageLimit,
            Integer dialogueRound,
            Integer maxContextMessageNum,
            boolean enableReload,
            boolean enableKvCacheRelease,
            TokenCounter tokenCounter) {

        ContextEngineConfig config = ContextEngineConfig.builder()
                .defaultWindowMessageNum(windowMessageLimit)
                .defaultWindowRoundNum(dialogueRound)
                .maxContextMessageNum(maxContextMessageNum)
                .enableReload(enableReload)
                .enableKvCacheRelease(enableKvCacheRelease)
                .build();
        ContextEngine engine = new ContextEngine(config);
        return engine.createContext("test_context", null,
                null, history, tokenCounter);
    }

    private List<BaseMessage> userMessages(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> (BaseMessage) new UserMessage("test-" + i))
                .toList();
    }

    private List<BaseMessage> historyMessages(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> (BaseMessage) new UserMessage("history-" + i))
                .toList();
    }

    private List<ToolCall> createToolCallList(List<String> ids) {
        return ids.stream()
                .map(id -> ToolCall.builder().id(id).name("test-tool").type("function").arguments("").build())
                .toList();
    }

    // ===================== Add Messages =====================

    @Nested
    @DisplayName("addMessages")
    class AddMessages {

        @Test
        @DisplayName("add one message")
        void testAddOneMessage() {
            ModelContext context = createContext();
            List<BaseMessage> result = context.addMessages(new UserMessage("test"));
            assertEquals(1, result.size());
            assertEquals(List.of(new UserMessage("test")), context.getMessages());
            assertEquals(1, context.size());
        }

        @Test
        @DisplayName("add batch messages")
        void testAddBatchMessages() {
            ModelContext context = createContext();
            List<BaseMessage> msgList = userMessages(100);
            List<BaseMessage> result = context.addMessages(msgList);
            assertEquals(msgList, result);
            assertEquals(msgList, context.getMessages());
            assertEquals(100, context.size());
        }

        @Test
        @DisplayName("add one message with history")
        void testAddOneMessageWithHistory() {
            List<BaseMessage> history = historyMessages(100);
            ModelContext context = createContext(history);
            List<BaseMessage> result = context.addMessages(new UserMessage("test"));
            assertEquals(1, result.size());

            List<BaseMessage> expected = new ArrayList<>(history);
            expected.add(new UserMessage("test"));
            assertEquals(expected, context.getMessages());
            assertEquals(List.of(new UserMessage("test")), context.getMessages(null, false));
            assertEquals(101, context.size());
        }

        @Test
        @DisplayName("add batch messages with history")
        void testAddBatchMessagesWithHistory() {
            List<BaseMessage> history = historyMessages(100);
            List<BaseMessage> msgList = userMessages(100);
            ModelContext context = createContext(history);
            List<BaseMessage> result = context.addMessages(msgList);
            assertEquals(msgList, result);

            List<BaseMessage> expected = new ArrayList<>(history);
            expected.addAll(msgList);
            assertEquals(expected, context.getMessages());
            assertEquals(msgList, context.getMessages(null, false));
            assertEquals(200, context.size());
        }
    }

    // ===================== Get Messages =====================

    @Nested
    @DisplayName("getMessages")
    class GetMessages {

        @Test
        @DisplayName("get empty messages")
        void testGetEmptyMessages() {
            ModelContext context = createContext();
            assertEquals(0, context.size());
            assertEquals(List.of(), context.getMessages(null, true));
            assertEquals(List.of(), context.getMessages(null, false));
            assertEquals(List.of(), context.getMessages(0, true));
            assertEquals(List.of(), context.getMessages(10, true));
        }

        @Test
        @DisplayName("get messages with invalid size throws")
        void testGetMessagesWithInvalidSize() {
            ModelContext context = createContext();
            assertThrows(BaseError.class, () -> context.getMessages(-1, true));
        }

        @Test
        @DisplayName("get empty messages with history")
        void testGetEmptyMessagesWithHistory() {
            List<BaseMessage> history = historyMessages(100);
            ModelContext context = createContext(history);
            assertEquals(100, context.size());
            assertEquals(history, context.getMessages(null, true));
            assertEquals(List.of(), context.getMessages(null, false));
            assertEquals(List.of(), context.getMessages(0, true));
            assertEquals(history.subList(90, 100), context.getMessages(10, true));
            assertEquals(history, context.getMessages(100, true));
            assertEquals(history, context.getMessages(101, true));
            assertEquals(List.of(), context.getMessages(0, false));
            assertEquals(List.of(), context.getMessages(10, false));
        }

        @Test
        @DisplayName("get messages without history")
        void testGetMessages() {
            ModelContext context = createContext();
            List<BaseMessage> msgList = userMessages(100);
            context.addMessages(msgList);
            assertEquals(100, context.size());
            assertEquals(msgList, context.getMessages(null, true));
            assertEquals(msgList, context.getMessages(null, false));
            assertEquals(List.of(), context.getMessages(0, true));
            assertEquals(msgList.subList(90, 100), context.getMessages(10, true));
            assertEquals(msgList, context.getMessages(100, true));
            assertEquals(msgList, context.getMessages(101, true));

            assertEquals(List.of(), context.getMessages(0, false));
            assertEquals(msgList.subList(90, 100), context.getMessages(10, false));
            assertEquals(msgList, context.getMessages(100, false));
            assertEquals(msgList, context.getMessages(101, false));
        }

        @Test
        @DisplayName("get messages with history")
        void testGetMessagesWithHistory() {
            List<BaseMessage> history = historyMessages(100);
            List<BaseMessage> msgList = userMessages(100);
            ModelContext context = createContext(history);
            context.addMessages(msgList);
            assertEquals(200, context.size());

            List<BaseMessage> all = new ArrayList<>(history);
            all.addAll(msgList);

            assertEquals(all, context.getMessages(null, true));
            assertEquals(msgList, context.getMessages(null, false));
            assertEquals(List.of(), context.getMessages(0, true));
            assertEquals(msgList.subList(90, 100), context.getMessages(10, true));
            assertEquals(msgList, context.getMessages(100, true));

            List<BaseMessage> expected101 = new ArrayList<>();
            expected101.add(history.get(99));
            expected101.addAll(msgList);
            assertEquals(expected101, context.getMessages(101, true));

            List<BaseMessage> expected150 = new ArrayList<>(history.subList(50, 100));
            expected150.addAll(msgList);
            assertEquals(expected150, context.getMessages(150, true));
            assertEquals(all, context.getMessages(200, true));
            assertEquals(all, context.getMessages(201, true));

            assertEquals(List.of(), context.getMessages(0, false));
            assertEquals(msgList.subList(90, 100), context.getMessages(10, false));
            assertEquals(msgList, context.getMessages(100, false));
            assertEquals(msgList, context.getMessages(101, false));
            assertEquals(msgList, context.getMessages(200, false));
        }
    }

    // ===================== Pop Messages =====================

    @Nested
    @DisplayName("popMessages")
    class PopMessages {

        @Test
        @DisplayName("pop empty messages")
        void testPopEmptyMessages() {
            ModelContext context = createContext();
            assertEquals(List.of(), context.popMessages(0, true));
            assertEquals(0, context.size());
            assertEquals(List.of(), context.popMessages(0, false));
            assertEquals(0, context.size());
            assertEquals(List.of(), context.popMessages(100, true));
            assertEquals(0, context.size());
        }

        @Test
        @DisplayName("pop empty messages with history")
        void testPopEmptyMessagesWithHistory() {
            List<BaseMessage> history = historyMessages(100);
            ModelContext context = createContext(history);
            assertEquals(history, context.popMessages(context.size(), true));
            assertEquals(0, context.size());

            // Reset: pop without history
            context = createContext(historyMessages(100));
            assertEquals(List.of(), context.popMessages(context.size(), false));
            assertEquals(100, context.size());

            // Pop one at a time from history
            context = createContext(historyMessages(100));
            assertEquals(List.of(), context.popMessages(0, true));
            assertEquals(100, context.size());
            for (int i = 1; i <= 100; i++) {
                List<BaseMessage> popped = context.popMessages(1, true);
                assertEquals(100 - i, context.size());
                assertEquals(List.of(historyMessages(100).get(100 - i)), popped);
            }
            assertEquals(List.of(), context.popMessages(1, true));
            assertEquals(0, context.size());

            // Pop 10 then rest
            List<BaseMessage> historyFull = historyMessages(100);
            context = createContext(new ArrayList<>(historyFull));
            List<BaseMessage> popped10 = context.popMessages(10, true);
            assertEquals(90, context.size());
            assertEquals(historyFull.subList(90, 100), popped10);
            List<BaseMessage> popped100 = context.popMessages(100, true);
            assertEquals(0, context.size());
            assertEquals(historyFull.subList(0, 90), popped100);
        }

        @Test
        @DisplayName("pop messages without history")
        void testPopMessages() {
            List<BaseMessage> msgList = userMessages(100);
            ModelContext context = createContext();
            context.addMessages(new ArrayList<>(msgList));
            assertEquals(msgList, context.popMessages(context.size(), true));
            assertEquals(0, context.size());

            // Reset: pop with withHistory=false gives same
            context = createContext();
            context.addMessages(new ArrayList<>(msgList));
            assertEquals(msgList, context.popMessages(context.size(), false));
            assertEquals(0, context.size());

            // Pop one at a time
            context = createContext();
            context.addMessages(new ArrayList<>(msgList));
            assertEquals(List.of(), context.popMessages(0, true));
            assertEquals(100, context.size());
            for (int i = 1; i <= 100; i++) {
                List<BaseMessage> popped = context.popMessages(1, true);
                assertEquals(100 - i, context.size());
                assertEquals(List.of(msgList.get(100 - i)), popped);
            }
            assertEquals(List.of(), context.popMessages(1, true));
            assertEquals(0, context.size());

            // Pop 10 then rest
            context = createContext();
            context.addMessages(new ArrayList<>(msgList));
            List<BaseMessage> popped10 = context.popMessages(10, true);
            assertEquals(90, context.size());
            assertEquals(msgList.subList(90, 100), popped10);
            List<BaseMessage> popped100 = context.popMessages(100, true);
            assertEquals(0, context.size());
            assertEquals(msgList.subList(0, 90), popped100);
        }

        @Test
        @DisplayName("pop messages with invalid size throws")
        void testPopMessagesWithInvalidSize() {
            ModelContext context = createContext();
            assertThrows(BaseError.class, () -> context.popMessages(-1, true));
        }

        @Test
        @DisplayName("pop messages with history - complex")
        void testPopMessagesWithHistory() {
            List<BaseMessage> history = historyMessages(100);
            List<BaseMessage> msgList = userMessages(100);
            List<BaseMessage> all = new ArrayList<>(history);
            all.addAll(msgList);

            // Pop all with history
            ModelContext context = createContext(new ArrayList<>(history));
            context.addMessages(new ArrayList<>(msgList));
            assertEquals(all, context.popMessages(context.size(), true));
            assertEquals(0, context.size());

            // Pop without history
            context = createContext(new ArrayList<>(history));
            context.addMessages(new ArrayList<>(msgList));
            assertEquals(msgList, context.popMessages(context.size(), false));
            assertEquals(100, context.size());

            // Pop one at a time
            context = createContext(new ArrayList<>(history));
            context.addMessages(new ArrayList<>(msgList));
            assertEquals(List.of(), context.popMessages(0, true));
            assertEquals(200, context.size());
            for (int i = 1; i <= 100; i++) {
                List<BaseMessage> popped = context.popMessages(1, true);
                assertEquals(200 - i, context.size());
                assertEquals(List.of(msgList.get(100 - i)), popped);
            }
            for (int i = 1; i <= 100; i++) {
                List<BaseMessage> popped = context.popMessages(1, true);
                assertEquals(100 - i, context.size());
                assertEquals(List.of(history.get(100 - i)), popped);
            }
            assertEquals(List.of(), context.popMessages(1, true));
            assertEquals(0, context.size());

            // Pop 10, 100, 10, rest
            context = createContext(new ArrayList<>(history));
            context.addMessages(new ArrayList<>(msgList));
            List<BaseMessage> p10 = context.popMessages(10, true);
            assertEquals(190, context.size());
            assertEquals(msgList.subList(90, 100), p10);
            List<BaseMessage> p100 = context.popMessages(100, true);
            assertEquals(90, context.size());
            List<BaseMessage> expected100 = new ArrayList<>(history.subList(90, 100));
            expected100.addAll(msgList.subList(0, 90));
            assertEquals(expected100, p100);
            List<BaseMessage> p10b = context.popMessages(10, true);
            assertEquals(80, context.size());
            assertEquals(history.subList(80, 90), p10b);
            List<BaseMessage> pRest = context.popMessages(100, true);
            assertEquals(0, context.size());
            assertEquals(history.subList(0, 80), pRest);
        }
    }

    // ===================== Set Messages =====================

    @Nested
    @DisplayName("setMessages")
    class SetMessages {

        @Test
        @DisplayName("set messages replaces all")
        void testSetMessages() {
            List<BaseMessage> msgList = userMessages(100);
            ModelContext context = createContext();
            context.setMessages(msgList);
            assertEquals(100, context.size());
        }

        @Test
        @DisplayName("set messages with history replaces all")
        void testSetMessagesReplacesHistory() {
            List<BaseMessage> history = historyMessages(100);
            List<BaseMessage> msgList = userMessages(100);
            ModelContext context = createContext(new ArrayList<>(history));
            context.setMessages(msgList);
            assertEquals(100, context.size());
            assertEquals(msgList, context.getMessages());
        }

        @Test
        @DisplayName("set messages without history keeps history")
        void testSetMessagesWithoutHistory() {
            List<BaseMessage> history = historyMessages(100);
            List<BaseMessage> msgList = userMessages(100);
            ModelContext context = createContext(new ArrayList<>(history));
            context.setMessages(msgList, false);
            assertEquals(200, context.size());
            List<BaseMessage> expected = new ArrayList<>(history);
            expected.addAll(msgList);
            assertEquals(expected, context.getMessages());
        }

        @Test
        @DisplayName("set messages after pop keeps remaining history")
        void testSetMessagesAfterPop() {
            List<BaseMessage> history = historyMessages(100);
            List<BaseMessage> msgList = userMessages(100);
            ModelContext context = createContext(new ArrayList<>(history));
            context.popMessages(50, true);
            context.setMessages(msgList, false);
            assertEquals(150, context.size());
            List<BaseMessage> expected = new ArrayList<>(history.subList(0, 50));
            expected.addAll(msgList);
            assertEquals(expected, context.getMessages());
        }
    }

    // ===================== Clear Messages =====================

    @Nested
    @DisplayName("clearMessages")
    class ClearMessages {

        @Test
        @DisplayName("clear with history=true clears all")
        void testClearWithHistoryTrue() {
            List<BaseMessage> history = List.of(new UserMessage("h1"));
            ModelContext context = createContext(new ArrayList<>(history));
            context.addMessages(new UserMessage("n1"));
            context.clearMessages(true);
            assertEquals(0, context.size());
        }

        @Test
        @DisplayName("clear with history=false keeps history")
        void testClearWithHistoryFalse() {
            List<BaseMessage> history = List.of(new UserMessage("h1"));
            ModelContext context = createContext(new ArrayList<>(history));
            context.addMessages(new UserMessage("n1"));
            context.clearMessages(false);
            assertEquals(1, context.size());
            assertEquals("h1", context.getMessages().get(0).getContent());
        }
    }

    // ===================== Context Window =====================

    @Nested
    @DisplayName("getContextWindow")
    class GetContextWindowTests {

        @Test
        @DisplayName("empty context window")
        void testEmptyContextWindow() {
            ModelContext context = createContext();
            ContextWindow window = context.getContextWindow();
            assertEquals(List.of(), window.getContextMessages());
            assertEquals(List.of(), window.getSystemMessages());
            assertEquals(List.of(), window.getTools());
        }

        @Test
        @DisplayName("invalid window size throws")
        void testInvalidWindowSize() {
            ModelContext context = createContext();
            assertThrows(BaseError.class, () ->
                    context.getContextWindow(null, null, -1, null));
        }

        @Test
        @DisplayName("with system messages")
        void testWithSystemMessages() {
            List<BaseMessage> sysMsgs = List.of(new SystemMessage("system message"));
            ModelContext context = createContext();
            ContextWindow window = context.getContextWindow(sysMsgs, null, null, null);
            assertEquals(List.of(), window.getContextMessages());
            assertEquals(sysMsgs, window.getSystemMessages());
            assertEquals(List.of(), window.getTools());
        }

        @Test
        @DisplayName("with context messages limited by window size")
        void testWithContextMessagesLimited() {
            List<BaseMessage> msgList = userMessages(100);
            List<BaseMessage> sysMsgs = List.of(new SystemMessage("system message"));
            ModelContext context = createContext(null, 10);
            context.addMessages(msgList);
            ContextWindow window = context.getContextWindow(sysMsgs, null, null, null);
            // window size = 10, system takes 1, so context gets 9
            assertEquals(msgList.subList(91, 100), window.getContextMessages());
            assertEquals(sysMsgs, window.getSystemMessages());
        }

        @Test
        @DisplayName("limited window size truncates system messages")
        void testLimitedWindowSizeTruncatesSysMessages() {
            List<BaseMessage> msgList = userMessages(100);
            List<BaseMessage> sysMsgs = List.of(
                    new SystemMessage("system message-1"),
                    new SystemMessage("system message-2"));
            ModelContext context = createContext(null, 1);
            context.addMessages(msgList);
            ContextWindow window = context.getContextWindow(sysMsgs, null, null, null);
            assertEquals(List.of(), window.getContextMessages());
            assertEquals(sysMsgs.subList(1, 2), window.getSystemMessages());
        }

        @Test
        @DisplayName("window validates leading ToolMessages stripped")
        void testWindowStripsLeadingToolMessages() {
            List<BaseMessage> toolMsgs = List.of(
                    new ToolMessage("tool-0", "tc-0"),
                    new ToolMessage("tool-1", "tc-1"),
                    new ToolMessage("tool-2", "tc-2"));
            List<BaseMessage> userMsgs = userMessages(10);
            List<BaseMessage> allMsgs = new ArrayList<>(toolMsgs);
            allMsgs.addAll(userMsgs);

            ModelContext context = createContext(null, 20);
            context.addMessages(allMsgs);
            List<BaseMessage> sysMsgs = List.of(new SystemMessage("sys"));
            ContextWindow window = context.getContextWindow(sysMsgs, null, null, null);

            assertEquals(sysMsgs, window.getSystemMessages());
            assertTrue(window.getContextMessages().stream()
                    .noneMatch(m -> m instanceof ToolMessage));
        }

        @Test
        @DisplayName("with tools")
        void testWithTools() {
            ModelContext context = createContext();
            List<ToolInfo> tools = List.of(ToolInfo.builder().name("my_tool").description("test").build());
            ContextWindow window = context.getContextWindow(null, tools, null, null);
            assertEquals(tools, window.getTools());
            assertEquals(1, window.getStatistic().getTools());
        }

        @Test
        @DisplayName("invalid dialogue round throws")
        void testInvalidDialogueRound() {
            ModelContext context = createContext();
            assertThrows(BaseError.class, () ->
                    context.getContextWindow(null, null, null, 0));
        }

        @Test
        @DisplayName("dialogue round limits returned messages")
        void testDialogueRoundLimit() {
            ModelContext context = createContext(null, 100, 1, null, false, false, null);
            List<BaseMessage> d1 = List.of(
                    new UserMessage("user-1"),
                    AssistantMessage.builder().content("a1")
                            .toolCalls(createToolCallList(List.of("tc-1", "tc-2", "tc-3"))).build(),
                    new ToolMessage("tool-1", "tc-1"),
                    new ToolMessage("tool-2", "tc-2"),
                    new ToolMessage("tool-3", "tc-3"),
                    new AssistantMessage("assistant-2"));
            List<BaseMessage> d2 = List.of(
                    new UserMessage("user-2"),
                    AssistantMessage.builder().content("a3")
                            .toolCalls(createToolCallList(List.of("tc-1", "tc-2"))).build(),
                    new ToolMessage("tool-4", "tc-1"),
                    new ToolMessage("tool-5", "tc-2"),
                    new AssistantMessage("assistant-4"));
            List<BaseMessage> d3 = List.of(
                    new UserMessage("user-3"),
                    AssistantMessage.builder().content("a3")
                            .toolCalls(createToolCallList(List.of("tc-1"))).build(),
                    new ToolMessage("tool-6", "tc-1"),
                    new AssistantMessage("assistant-5"));

            List<BaseMessage> messages = new ArrayList<>();
            messages.addAll(d1);
            messages.addAll(d2);
            messages.addAll(d3);

            context.addMessages(messages);
            ContextWindow window = context.getContextWindow();
            assertEquals(d3, window.getContextMessages());

            // dialogue_round=2
            context.clearMessages(true);
            context.addMessages(messages);
            window = context.getContextWindow(null, null, null, 2);
            List<BaseMessage> expected2 = new ArrayList<>(d2);
            expected2.addAll(d3);
            assertEquals(expected2, window.getContextMessages());

            // dialogue_round=3
            context.clearMessages(true);
            context.addMessages(messages);
            window = context.getContextWindow(null, null, null, 3);
            assertEquals(messages, window.getContextMessages());

            // dialogue_round=4 (more than available)
            context.clearMessages(true);
            context.addMessages(messages);
            window = context.getContextWindow(null, null, null, 4);
            assertEquals(messages, window.getContextMessages());
        }

        @Test
        @DisplayName("incomplete dialogue round")
        void testIncompleteDialogueRound() {
            ModelContext context = createContext(null, 100, 1, null, false, false, null);
            List<BaseMessage> d1 = List.of(
                    new UserMessage("user-1"),
                    AssistantMessage.builder().content("a1")
                            .toolCalls(createToolCallList(List.of("tc-1", "tc-2", "tc-3"))).build(),
                    new ToolMessage("tool-1", "tc-1"),
                    new ToolMessage("tool-2", "tc-2"),
                    new ToolMessage("tool-3", "tc-3"),
                    new AssistantMessage("assistant-2"),
                    new UserMessage("user-1-1"));
            List<BaseMessage> d2 = List.of(
                    new UserMessage("user-2"),
                    AssistantMessage.builder().content("a3")
                            .toolCalls(createToolCallList(List.of("tc-1", "tc-2"))).build(),
                    new ToolMessage("tool-4", "tc-1"),
                    new ToolMessage("tool-5", "tc-2"),
                    new AssistantMessage("assistant-4"));
            List<BaseMessage> d3 = List.of(
                    new UserMessage("user-3"));

            List<BaseMessage> messages = new ArrayList<>();
            messages.addAll(d1);
            messages.addAll(d2);
            messages.addAll(d3);

            context.addMessages(messages);
            ContextWindow window = context.getContextWindow();
            assertEquals(d3, window.getContextMessages());

            context.clearMessages(true);
            context.addMessages(messages);
            window = context.getContextWindow(null, null, null, 2);
            List<BaseMessage> expected2 = new ArrayList<>(d2);
            expected2.addAll(d3);
            assertEquals(expected2, window.getContextMessages());

            context.clearMessages(true);
            context.addMessages(messages);
            window = context.getContextWindow(null, null, null, 3);
            assertEquals(messages, window.getContextMessages());

            context.clearMessages(true);
            context.addMessages(messages);
            window = context.getContextWindow(null, null, null, 4);
            assertEquals(messages, window.getContextMessages());
        }

        @Test
        @DisplayName("dialogue round overrides window size when both passed")
        void testDialogueRoundOverridesWindowSize() {
            ModelContext context = createContext(null, 100, 1, null, false, false, null);
            List<BaseMessage> d1 = List.of(new UserMessage("u1"), new AssistantMessage("a1"));
            List<BaseMessage> d2 = List.of(new UserMessage("u2"), new AssistantMessage("a2"));
            List<BaseMessage> messages = new ArrayList<>(d1);
            messages.addAll(d2);
            context.addMessages(messages);

            ContextWindow window = context.getContextWindow(null, null, 100, 1);
            assertEquals(d2, window.getContextMessages());
        }
    }

    // ===================== Statistics =====================

    @Nested
    @DisplayName("statistic")
    class Statistics {

        @Test
        @DisplayName("statistic counts messages by role")
        void testStatisticCountsByRole() {
            List<BaseMessage> msgList = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                switch (i % 4) {
                    case 0 -> msgList.add(new UserMessage("user-" + i));
                    case 1 -> msgList.add(new SystemMessage("system-" + i));
                    case 2 -> msgList.add(new AssistantMessage("ai-" + i));
                    case 3 -> msgList.add(new ToolMessage("tool-" + i, ""));
                }
            }
            ModelContext context = createContext();
            context.addMessages(msgList);

            ContextStats stat = context.statistic();
            assertEquals(100, stat.getTotalMessages());
            assertEquals(25, stat.getSystemMessages());
            assertEquals(25, stat.getAssistantMessages());
            assertEquals(25, stat.getToolMessages());
            assertEquals(25, stat.getUserMessages());

            ContextWindow window = context.getContextWindow();
            ContextStats windowStat = window.getStatistic();
            assertEquals(100, windowStat.getTotalMessages());
            assertEquals(25, windowStat.getSystemMessages());
            assertEquals(25, windowStat.getAssistantMessages());
            assertEquals(25, windowStat.getToolMessages());
            assertEquals(25, windowStat.getUserMessages());
        }

        @Test
        @DisplayName("total_dialogues counts rounds correctly")
        void testTotalDialogues() {
            // Empty => 0
            ModelContext context = createContext();
            assertEquals(0, context.statistic().getTotalDialogues());

            // Only user messages => 0
            context = createContext();
            context.addMessages(List.of(new UserMessage("u1"), new UserMessage("u2")));
            assertEquals(0, context.statistic().getTotalDialogues());

            // One complete round
            context = createContext();
            context.addMessages(List.of(new UserMessage("u1"), new AssistantMessage("a1")));
            assertEquals(1, context.statistic().getTotalDialogues());

            // Round with tool calls
            context = createContext();
            context.addMessages(List.of(
                    new UserMessage("u1"),
                    AssistantMessage.builder().content("")
                            .toolCalls(createToolCallList(List.of("tc-1"))).build(),
                    new ToolMessage("result", "tc-1"),
                    new AssistantMessage("a-final")));
            assertEquals(1, context.statistic().getTotalDialogues());

            // Two rounds
            context = createContext();
            context.addMessages(List.of(
                    new UserMessage("u1"), new AssistantMessage("a1"),
                    new UserMessage("u2"), new AssistantMessage("a2")));
            assertEquals(2, context.statistic().getTotalDialogues());

            // System + user + assistant: system is not a round
            context = createContext();
            context.addMessages(List.of(
                    new SystemMessage("sys"),
                    new UserMessage("u1"), new AssistantMessage("a1")));
            assertEquals(1, context.statistic().getTotalDialogues());

            // Three rounds
            context = createContext();
            context.addMessages(List.of(
                    new UserMessage("u1"), new AssistantMessage("a1"),
                    new UserMessage("u2"), new AssistantMessage("a2"),
                    new UserMessage("u3"), new AssistantMessage("a3")));
            assertEquals(3, context.statistic().getTotalDialogues());

            // Context window stat also counts dialogues
            context = createContext();
            context.addMessages(List.of(
                    new UserMessage("u1"), new AssistantMessage("a1")));
            ContextWindow window = context.getContextWindow();
            assertEquals(1, window.getStatistic().getTotalDialogues());
        }

        @Test
        @DisplayName("token counter null returns zero tokens")
        void testTokenCounterNull() {
            ModelContext context = createContext();
            context.addMessages(new UserMessage("hi"));
            ContextStats stat = context.statistic();
            assertEquals(1, stat.getTotalMessages());
            assertEquals(0, stat.getTotalTokens());
            assertEquals(0, stat.getUserMessageTokens());
        }

        @Test
        @DisplayName("token counter mock returns tokens")
        void testTokenCounterMock() {
            TokenCounter mockCounter = new TokenCounter() {
                @Override
                public int count(String text, String model) {
                    return 10;
                }

                @Override
                public int countMessages(List<BaseMessage> messages, String model) {
                    return 10;
                }

                @Override
                public int countTools(List<ToolInfo> tools, String model) {
                    return 5;
                }
            };
            ModelContext context = createContext(null, 100, null, null, false, false, mockCounter);
            context.addMessages(new UserMessage("hi"));
            ContextStats stat = context.statistic();
            assertEquals(10, stat.getUserMessageTokens());

            List<ToolInfo> tools = List.of(ToolInfo.builder().name("t1").description("d1").build());
            ContextWindow window = context.getContextWindow(null, tools, null, null);
            assertEquals(5, window.getStatistic().getToolTokens());
        }
    }

    // ===================== Max Context Message Num =====================

    @Nested
    @DisplayName("maxContextMessageNum")
    class MaxContextMessageNum {

        @Test
        @DisplayName("triggers resize when exceeded")
        void testMaxContextMessageNumTriggersResize() {
            ModelContext context = createContext(null, 100, null, 50, false, false, null);
            List<BaseMessage> msgList = userMessages(150);
            context.addMessages(msgList);
            assertTrue(context.size() <= 50);
        }

        @Test
        @DisplayName("null means no limit")
        void testMaxContextMessageNumNull() {
            ModelContext context = createContext(null, 100, null, null, false, false, null);
            List<BaseMessage> msgList = userMessages(200);
            context.addMessages(msgList);
            assertEquals(200, context.size());
        }
    }

    // ===================== Enable Reload =====================

    @Nested
    @DisplayName("enableReload")
    class EnableReload {

        @Test
        @DisplayName("adds reloader system prompt")
        void testEnableReloadAddsPrompt() {
            ModelContext context = createContext(null, 100, null, null, true, false, null);
            ContextWindow window = context.getContextWindow();
            assertEquals(1, window.getSystemMessages().size());
            assertTrue(window.getSystemMessages().get(0).getContentAsString().contains("offloaded content markers"));
        }

        @Test
        @DisplayName("false adds no reloader prompt")
        void testDisableReloadNoPrompt() {
            ModelContext context = createContext(null, 100, null, null, false, false, null);
            ContextWindow window = context.getContextWindow();
            assertEquals(List.of(), window.getSystemMessages());
        }

        @Test
        @DisplayName("with custom system messages appends reloader")
        void testEnableReloadWithCustomSystemMessages() {
            ModelContext context = createContext(null, 100, null, null, true, false, null);
            List<BaseMessage> sysMsgs = List.of(new SystemMessage("custom sys"));
            ContextWindow window = context.getContextWindow(sysMsgs, null, null, null);
            assertEquals(2, window.getSystemMessages().size());
            assertEquals("custom sys", window.getSystemMessages().get(0).getContentAsString());
            assertTrue(window.getSystemMessages().get(1).getContentAsString().contains("offloaded content markers"));
        }
    }

    // ===================== KV Cache =====================

    @Nested
    @DisplayName("enableKvCacheRelease")
    class KvCacheRelease {

        @Test
        @DisplayName("creates KV cache manager when enabled")
        void testEnableKvCacheRelease() {
            // Just verify no exception and context works
            ModelContext context = createContext(null, 100, null, null, false, true, null);
            context.addMessages(new UserMessage("hi"));
            assertNotNull(context.getContextWindow());
        }

        @Test
        @DisplayName("successive getContextWindow calls do not throw")
        void testMultipleGetContextWindow() {
            ModelContext context = createContext(null, 100, null, null, false, true, null);
            context.addMessages(new UserMessage("hi"));
            context.getContextWindow();
            context.getContextWindow();
        }
    }

    // ===================== Reloader Tool =====================

    @Nested
    @DisplayName("reloaderTool")
    class ReloaderToolTests {

        @Test
        @DisplayName("offload then reload returns content")
        void testOffloadThenReload() throws Exception {
            ModelContext context = createContext();
            List<BaseMessage> msgs = List.of(new UserMessage("secret"), new AssistantMessage("reply"));
            ((SessionModelContext) context).offloadMessages("handle-1", msgs);
            var tool = context.reloaderTool();
            Object result = tool.invoke(
                    java.util.Map.of("offload_handle", "handle-1", "offload_type", "in_memory"), null);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("handle-1"));
        }

        @Test
        @DisplayName("nonexistent handle returns failure message")
        void testNonexistentHandle() throws Exception {
            ModelContext context = createContext();
            var tool = context.reloaderTool();
            Object result = tool.invoke(
                    java.util.Map.of("offload_handle", "nonexistent", "offload_type", "in_memory"), null);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("Failed to reload"));
            assertTrue(resultStr.contains("nonexistent"));
        }

        @Test
        @DisplayName("reloader tool card contains session and context")
        void testReloaderToolCardId() {
            ModelContext context = createContext();
            var tool = context.reloaderTool();
            String cardId = tool.getCard().getId();
            assertTrue(cardId.contains("default_session_id") || cardId.contains("test_context"));
        }
    }

    // ===================== Save/Load State =====================

    @Nested
    @DisplayName("saveState / loadState")
    class SaveLoadState {

        @Test
        @DisplayName("save state structure")
        void testSaveStateStructure() {
            ModelContext context = createContext();
            context.addMessages(new UserMessage("a"));
            var state = ((SessionModelContext) context).saveState();
            assertTrue(state.containsKey("messages"));
            assertTrue(state.containsKey("offload_messages"));
            @SuppressWarnings("unchecked")
            List<BaseMessage> msgs = (List<BaseMessage>) state.get("messages");
            assertEquals(1, msgs.size());
        }

        @Test
        @DisplayName("load state restores messages")
        void testLoadStateRestoresMessages() {
            ModelContext context = createContext();
            List<BaseMessage> msgs = List.of(new UserMessage("loaded"), new AssistantMessage("resp"));
            Map<String, Object> innerState = new java.util.HashMap<>();
            innerState.put("messages", msgs);
            innerState.put("offload_messages", new java.util.HashMap<>());
            Map<String, Object> state = new java.util.HashMap<>();
            state.put(context.contextId(), innerState);
            ((SessionModelContext) context).loadState(state);
            assertEquals(msgs, context.getMessages());
        }

        @Test
        @DisplayName("load empty state clears buffer")
        void testLoadEmptyStateClearsBuffer() {
            ModelContext context = createContext();
            context.addMessages(new UserMessage("x"));
            ((SessionModelContext) context).loadState(java.util.Map.of());
            assertEquals(0, context.size());
        }

        @Test
        @DisplayName("load state wrong context id clears buffer")
        void testLoadStateWrongContextId() {
            ModelContext context = createContext();
            context.addMessages(new UserMessage("x"));
            Map<String, Object> innerState = new java.util.HashMap<>();
            innerState.put("messages", List.of(new UserMessage("other")));
            innerState.put("offload_messages", new java.util.HashMap<>());
            Map<String, Object> state = new java.util.HashMap<>();
            state.put("other_context", innerState);
            ((SessionModelContext) context).loadState(state);
            assertEquals(0, context.size());
        }
    }

    // ===================== Session ID / Context ID =====================

    @Test
    @DisplayName("sessionId and contextId")
    void testSessionIdAndContextId() {
        ModelContext context = createContext();
        assertEquals("default_session_id", context.sessionId());
        assertEquals("test_context", context.contextId());
    }
}
