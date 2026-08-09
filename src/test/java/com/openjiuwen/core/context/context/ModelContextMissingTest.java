/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Supplemental unit tests for model-context behavior.
 *
 * <p>Mirrors Python's {@code TestModelContext} in
 * {@code tests/unit_tests/core/context_engine/test_context_model.py}.</p>
 */
class ModelContextMissingTest {
    private static final String CONTEXT_ID = "test_context";
    private static final String SESSION_ID = "default_session_id";

    @Test
    void testModelContextAddOneMessages() {
        SessionModelContext context = createContext();

        List<BaseMessage> added = await(context.addMessages(user("test")));

        assertRolesAndContents(added, List.of(user("test")));
        assertRolesAndContents(context.getMessages(), List.of(user("test")));
        assertRolesAndContents(context.getMessages(null, true), List.of(user("test")));
        assertThat(context.length()).isEqualTo(1);
        context.popMessages(context.length(), true);
    }

    @Test
    void testModelContextAddInvalidMessages() {
        SessionModelContext context = createContext();

        assertThatThrownBy(() -> context.addMessages((List<BaseMessage>) null))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_MESSAGE_INVALID.getCode());
        assertThatThrownBy(() -> context.addMessages(invalidMessages()))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_MESSAGE_INVALID.getCode());
    }

    @Test
    void testModelContextAddBatchMessages() {
        List<BaseMessage> messages = users("test", 100);
        SessionModelContext context = createContext();

        List<BaseMessage> added = await(context.addMessages(messages));

        assertThat(added).containsExactlyElementsOf(messages);
        assertThat(context.getMessages()).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(null, false)).containsExactlyElementsOf(messages);
        assertThat(context.length()).isEqualTo(messages.size());
        context.popMessages(context.length(), true);
    }

    @Test
    void testModelContextAddOneMessagesWithHistory() {
        List<BaseMessage> history = users("history", 100);
        SessionModelContext context = createContext(history);

        List<BaseMessage> added = await(context.addMessages(user("test")));

        assertRolesAndContents(added, List.of(user("test")));
        assertRolesAndContents(context.getMessages(), concat(history, List.of(user("test"))));
        assertRolesAndContents(context.getMessages(null, false), List.of(user("test")));
        assertThat(context.length()).isEqualTo(history.size() + 1);
        context.popMessages(context.length(), true);
    }

    @Test
    void testModelContextAddBatchMessagesWithHistory() {
        List<BaseMessage> history = users("history", 100);
        List<BaseMessage> messages = users("test", 100);
        SessionModelContext context = createContext(history);

        List<BaseMessage> added = await(context.addMessages(messages));

        assertThat(added).containsExactlyElementsOf(messages);
        assertThat(context.getMessages()).containsExactlyElementsOf(concat(history, messages));
        assertThat(context.getMessages(null, false)).containsExactlyElementsOf(messages);
        assertThat(context.length()).isEqualTo(history.size() + messages.size());
        context.popMessages(context.length(), true);
    }

    @Test
    void testModelContextGetEmptyMessages() {
        SessionModelContext context = createContext();

        assertThat(context.length()).isZero();
        assertThat(context.getMessages(null, true)).isEmpty();
        assertThat(context.getMessages(null, false)).isEmpty();
        assertThat(context.getMessages(0, true)).isEmpty();
        assertThat(context.getMessages(10, true)).isEmpty();
    }

    @Test
    void testModelContextGetMessagesWithInvalidSize() {
        SessionModelContext context = createContext();

        assertThatThrownBy(() -> context.getMessages(-1, true))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_EXECUTION_ERROR.getCode());
    }

    @Test
    void testModelContextGetEmptyMessagesWithHistory() {
        List<BaseMessage> history = users("history", 100);
        SessionModelContext context = createContext(history);

        assertThat(context.length()).isEqualTo(100);
        assertThat(context.getMessages(null, true)).containsExactlyElementsOf(history);
        assertThat(context.getMessages(null, false)).isEmpty();
        assertThat(context.getMessages(0, true)).isEmpty();
        assertThat(context.getMessages(10, true)).containsExactlyElementsOf(history.subList(90, 100));
        assertThat(context.getMessages(100, true)).containsExactlyElementsOf(history);
        assertThat(context.getMessages(101, true)).containsExactlyElementsOf(history);
        assertThat(context.getMessages(0, false)).isEmpty();
        assertThat(context.getMessages(10, false)).isEmpty();
    }

    @Test
    void testModelContextGetMessages() {
        List<BaseMessage> messages = users("test", 100);
        SessionModelContext context = createContext();
        await(context.addMessages(messages));

        assertThat(context.length()).isEqualTo(100);
        assertThat(context.getMessages(null, true)).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(null, false)).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(0, true)).isEmpty();
        assertThat(context.getMessages(10, true)).containsExactlyElementsOf(messages.subList(90, 100));
        assertThat(context.getMessages(100, true)).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(101, true)).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(0, false)).isEmpty();
        assertThat(context.getMessages(10, false)).containsExactlyElementsOf(messages.subList(90, 100));
        assertThat(context.getMessages(100, false)).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(101, false)).containsExactlyElementsOf(messages);
    }

    @Test
    void testModelContextGetMessagesWithHistory() {
        List<BaseMessage> history = users("history", 100);
        List<BaseMessage> messages = users("test", 100);
        SessionModelContext context = createContext(history);
        await(context.addMessages(messages));

        assertThat(context.length()).isEqualTo(200);
        assertThat(context.getMessages(null, true)).containsExactlyElementsOf(concat(history, messages));
        assertThat(context.getMessages(null, false)).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(0, true)).isEmpty();
        assertThat(context.getMessages(10, true)).containsExactlyElementsOf(messages.subList(90, 100));
        assertThat(context.getMessages(100, true)).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(101, true)).containsExactlyElementsOf(concat(history.subList(99, 100), messages));
        assertThat(context.getMessages(150, true)).containsExactlyElementsOf(concat(history.subList(50, 100), messages));
        assertThat(context.getMessages(200, true)).containsExactlyElementsOf(concat(history, messages));
        assertThat(context.getMessages(201, true)).containsExactlyElementsOf(concat(history, messages));
        assertThat(context.getMessages(0, false)).isEmpty();
        assertThat(context.getMessages(10, false)).containsExactlyElementsOf(messages.subList(90, 100));
        assertThat(context.getMessages(100, false)).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(101, false)).containsExactlyElementsOf(messages);
        assertThat(context.getMessages(200, false)).containsExactlyElementsOf(messages);
    }

    @Test
    void testModelContextPopEmptyMessages() {
        SessionModelContext context = createContext();

        assertThat(context.popMessages(context.length(), true)).isEmpty();
        assertThat(context.length()).isZero();
        assertThat(context.popMessages(context.length(), false)).isEmpty();
        assertThat(context.length()).isZero();
        assertThat(context.popMessages(100, true)).isEmpty();
    }

    @Test
    void testModelContextPopEmptyMessagesWithHistory() {
        List<BaseMessage> history = users("history", 100);
        SessionModelContext context = createContext(history);
        assertThat(context.popMessages(context.length(), true)).containsExactlyElementsOf(history);
        assertThat(context.length()).isZero();

        history = users("history", 100);
        context = createContext(history);
        assertThat(context.popMessages(context.length(), false)).isEmpty();
        assertThat(context.length()).isEqualTo(100);

        history = users("history", 100);
        context = createContext(history);
        assertThat(context.popMessages(0, true)).isEmpty();
        assertThat(context.length()).isEqualTo(100);
        for (int index = 1; index <= 100; index++) {
            assertThat(context.popMessages(1, true)).containsExactly(history.get(100 - index));
            assertThat(context.length()).isEqualTo(100 - index);
        }
        assertThat(context.popMessages(1, true)).isEmpty();

        history = users("history", 100);
        context = createContext(history);
        assertThat(context.popMessages(10, true)).containsExactlyElementsOf(history.subList(90, 100));
        assertThat(context.length()).isEqualTo(90);
        assertThat(context.popMessages(100, true)).containsExactlyElementsOf(history.subList(0, 90));
        assertThat(context.length()).isZero();
    }

    @Test
    void testModelContextPopMessages() {
        List<BaseMessage> messages = users("test", 100);
        SessionModelContext context = createContext();
        await(context.addMessages(messages));
        assertThat(context.popMessages(context.length(), true)).containsExactlyElementsOf(messages);
        assertThat(context.length()).isZero();

        messages = users("test", 100);
        context = createContext();
        await(context.addMessages(messages));
        assertThat(context.popMessages(context.length(), false)).containsExactlyElementsOf(messages);
        assertThat(context.length()).isZero();

        messages = users("test", 100);
        context = createContext();
        await(context.addMessages(messages));
        assertThat(context.popMessages(0, true)).isEmpty();
        for (int index = 1; index <= 100; index++) {
            assertThat(context.popMessages(1, true)).containsExactly(messages.get(100 - index));
            assertThat(context.length()).isEqualTo(100 - index);
        }
        assertThat(context.popMessages(1, true)).isEmpty();

        messages = users("test", 100);
        context = createContext();
        await(context.addMessages(messages));
        assertThat(context.popMessages(10, true)).containsExactlyElementsOf(messages.subList(90, 100));
        assertThat(context.length()).isEqualTo(90);
        assertThat(context.popMessages(100, true)).containsExactlyElementsOf(messages.subList(0, 90));
        assertThat(context.length()).isZero();
    }

    @Test
    void testModelContextPopMessagesWithInvalidSize() {
        SessionModelContext context = createContext();

        assertThatThrownBy(() -> context.popMessages(-1, true))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_EXECUTION_ERROR.getCode());
    }

    @Test
    void testModelContextPopMessagesWithHistory() {
        List<BaseMessage> history = users("history", 100);
        List<BaseMessage> messages = users("test", 100);
        SessionModelContext context = createContext(history);
        await(context.addMessages(messages));
        assertThat(context.popMessages(context.length(), true)).containsExactlyElementsOf(concat(history, messages));
        assertThat(context.length()).isZero();

        history = users("history", 100);
        messages = users("test", 100);
        context = createContext(history);
        await(context.addMessages(messages));
        assertThat(context.popMessages(context.length(), false)).containsExactlyElementsOf(messages);
        assertThat(context.length()).isEqualTo(100);

        history = users("history", 100);
        messages = users("test", 100);
        context = createContext(history);
        await(context.addMessages(messages));
        assertThat(context.popMessages(0, true)).isEmpty();
        for (int index = 1; index <= 100; index++) {
            assertThat(context.popMessages(1, true)).containsExactly(messages.get(100 - index));
            assertThat(context.length()).isEqualTo(200 - index);
        }
        for (int index = 1; index <= 100; index++) {
            assertThat(context.popMessages(1, true)).containsExactly(history.get(100 - index));
            assertThat(context.length()).isEqualTo(100 - index);
        }
        assertThat(context.popMessages(1, true)).isEmpty();

        history = users("history", 100);
        messages = users("test", 100);
        context = createContext(history);
        await(context.addMessages(messages));
        assertThat(context.popMessages(10, true)).containsExactlyElementsOf(messages.subList(90, 100));
        assertThat(context.length()).isEqualTo(190);
        assertThat(context.popMessages(100, true)).containsExactlyElementsOf(concat(history.subList(90, 100),
                messages.subList(0, 90)));
        assertThat(context.length()).isEqualTo(90);
        assertThat(context.popMessages(10, true)).containsExactlyElementsOf(history.subList(80, 90));
        assertThat(context.length()).isEqualTo(80);
        assertThat(context.popMessages(100, true)).containsExactlyElementsOf(history.subList(0, 80));
        assertThat(context.length()).isZero();
    }

    @Test
    void testModelContextSetMessages() {
        List<BaseMessage> messages = users("test", 100);
        SessionModelContext context = createContext();
        context.setMessages(messages, true);
        assertThat(context.length()).isEqualTo(100);

        List<BaseMessage> history = users("history", 100);
        messages = users("test", 100);
        context = createContext(history);
        context.setMessages(messages, true);
        assertThat(context.length()).isEqualTo(100);
        assertThat(context.getMessages()).containsExactlyElementsOf(messages);

        history = users("history", 100);
        messages = users("test", 100);
        context = createContext(history);
        context.setMessages(messages, false);
        assertThat(context.length()).isEqualTo(200);
        assertThat(context.getMessages()).containsExactlyElementsOf(concat(history, messages));

        history = users("history", 100);
        messages = users("test", 100);
        context = createContext(history);
        context.popMessages(50, true);
        context.setMessages(messages, false);
        assertThat(context.length()).isEqualTo(150);
        assertThat(context.getMessages()).containsExactlyElementsOf(concat(history.subList(0, 50), messages));
    }

    @Test
    void testModelContextSetInvalidMessages() {
        SessionModelContext context = createContext();

        assertThatThrownBy(() -> context.setMessages(null, true))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_MESSAGE_INVALID.getCode());
        assertThatThrownBy(() -> context.setMessages(invalidMessages(), true))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_MESSAGE_INVALID.getCode());
    }

    @Test
    void testModelContextSetEmptyContextWindow() {
        ContextWindow window = window(createContext());

        assertThat(window.getContextMessages()).isEmpty();
        assertThat(window.getSystemMessages()).isEmpty();
        assertThat(window.getTools()).isEmpty();
    }

    @Test
    void testModelContextGetContextWindowWithInvalidSize() {
        SessionModelContext context = createContext();

        assertThatThrownBy(() -> window(context, List.of(), List.of(), -1, null))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_EXECUTION_ERROR.getCode());
    }

    @Test
    void testModelContextSetContextWindowWithSystemMessages() {
        List<BaseMessage> systemMessages = List.of(system("system message"));
        ContextWindow window = window(createContext(), systemMessages, List.of(), null, null);

        assertThat(window.getContextMessages()).isEmpty();
        assertThat(window.getSystemMessages()).containsExactlyElementsOf(systemMessages);
        assertThat(window.getTools()).isEmpty();
    }

    @Test
    void testModelContextSetContextWindowWithContextMessages() {
        List<BaseMessage> messages = users("test", 100);
        List<BaseMessage> systemMessages = List.of(system("system message"));
        SessionModelContext context = createContext(List.of(), 10, null, null, false, false,
                List.of(), defaultTokenCounter(), null);
        await(context.addMessages(messages));

        ContextWindow window = window(context, systemMessages, List.of(), null, null);

        assertThat(window.getContextMessages()).containsExactlyElementsOf(messages.subList(91, 100));
        assertThat(window.getSystemMessages()).containsExactlyElementsOf(systemMessages);
        assertThat(window.getTools()).isEmpty();
    }

    @Test
    void testModelContextSetContextWindowWithLimitedSize() {
        List<BaseMessage> messages = users("test", 100);
        List<BaseMessage> systemMessages = List.of(system("system message"));
        SessionModelContext context = createContext(List.of(), 1, null, null, false, false,
                List.of(), defaultTokenCounter(), null);
        await(context.addMessages(messages));

        ContextWindow window = window(context, systemMessages, List.of(), null, null);

        assertThat(window.getContextMessages()).isEmpty();
        assertThat(window.getSystemMessages()).containsExactlyElementsOf(systemMessages);
        assertThat(window.getTools()).isEmpty();

        messages = users("test", 100);
        systemMessages = List.of(system("system message-1"), system("system message-2"));
        context = createContext(List.of(), 1, null, null, false, false, List.of(), defaultTokenCounter(), null);
        await(context.addMessages(messages));
        window = window(context, systemMessages, List.of(), null, null);

        assertThat(window.getContextMessages()).isEmpty();
        assertThat(window.getSystemMessages()).containsExactlyElementsOf(systemMessages.subList(1, 2));
        assertThat(window.getTools()).isEmpty();
    }

    @Test
    void testModelContextStatistic() {
        List<BaseMessage> messages = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            messages.add(generateMessage(index));
        }
        SessionModelContext context = createContext();
        await(context.addMessages(messages));

        assertMessageStats(context.statistic(), 100, 25, 25, 25, 25);
        assertMessageStats(window(context).getStatistic(), 100, 25, 25, 25, 25);
    }

    @Test
    void testStatisticTotalDialogues() {
        SessionModelContext context = createContext();
        assertThat(context.statistic().getTotalDialogues()).isZero();

        context = createContext();
        await(context.addMessages(List.of(user("u1"), user("u2"))));
        assertThat(context.statistic().getTotalDialogues()).isEqualTo(1);

        context = createContext();
        await(context.addMessages(List.of(user("u1"), assistant("a1"))));
        assertThat(context.statistic().getTotalDialogues()).isEqualTo(1);

        context = createContext();
        await(context.addMessages(List.of(user("u1"), assistantToolCall("tc-1"), tool("result", "tc-1"),
                assistant("a-final"))));
        assertThat(context.statistic().getTotalDialogues()).isEqualTo(1);

        context = createContext();
        await(context.addMessages(List.of(user("u1"), assistant("a1"), user("u2"), assistant("a2"))));
        assertThat(context.statistic().getTotalDialogues()).isEqualTo(2);

        context = createContext();
        await(context.addMessages(List.of(system("sys"), user("u1"), assistant("a1"))));
        assertThat(context.statistic().getTotalDialogues()).isEqualTo(1);

        context = createContext();
        await(context.addMessages(List.of(user("u1"), assistant("a1"), user("u2"), assistant("a2"),
                user("u3"), assistant("a3"))));
        assertThat(context.statistic().getTotalDialogues()).isEqualTo(3);

        context = createContext();
        await(context.addMessages(List.of(user("u1"), assistant("a1"))));
        assertThat(window(context).getStatistic().getTotalDialogues()).isEqualTo(1);
    }

    @Test
    void testModelContextWindowValidation() {
        List<BaseMessage> messages = concat(List.of(tool("tool-0", "tc-0"), tool("tool-1", "tc-1"),
                tool("tool-2", "tc-2")), users("human", 10));
        SessionModelContext context = createContext(List.of(), 20, null, null, false, false,
                List.of(), defaultTokenCounter(), null);
        await(context.addMessages(messages));

        List<BaseMessage> systemMessages = List.of(system("sys"));
        ContextWindow window = window(context, systemMessages, List.of(), null, null);

        assertThat(window.getSystemMessages()).containsExactlyElementsOf(systemMessages);
        assertThat(window.getContextMessages()).noneMatch(ToolMessage.class::isInstance);
    }

    @Test
    void testModelContextWindowWithDialogueRound() {
        SessionModelContext context = createContext(List.of(), 100, 1, null, false, false,
                List.of(), defaultTokenCounter(), null);
        List<List<BaseMessage>> dialogues = completeDialogues();
        List<BaseMessage> messages = flatten(dialogues);

        await(context.addMessages(messages));
        assertThat(window(context).getContextMessages()).containsExactlyElementsOf(dialogues.get(2));

        await(context.addMessages(messages));
        assertThat(window(context, List.of(), List.of(), null, 1).getContextMessages())
                .containsExactlyElementsOf(dialogues.get(2));
        await(context.clearMessages(true));

        await(context.addMessages(messages));
        assertThat(window(context, List.of(), List.of(), null, 2).getContextMessages())
                .containsExactlyElementsOf(concat(dialogues.get(1), dialogues.get(2)));
        await(context.clearMessages(true));

        await(context.addMessages(messages));
        assertThat(window(context, List.of(), List.of(), null, 3).getContextMessages())
                .containsExactlyElementsOf(messages);
        await(context.clearMessages(true));

        await(context.addMessages(messages));
        assertThat(window(context, List.of(), List.of(), null, 4).getContextMessages())
                .containsExactlyElementsOf(messages);
        await(context.clearMessages(true));
    }

    @Test
    void testMaxContextMessageNumTriggersResize() {
        SessionModelContext context = createContext(List.of(), 100, null, 50, false, false,
                List.of(), defaultTokenCounter(), null);

        await(context.addMessages(users("m", 150)));

        assertThat(context.length()).isLessThanOrEqualTo(50);
    }

    @Test
    void testMaxContextMessageNumNoneNoLimit() {
        SessionModelContext context = createContext(List.of(), 100, null, null, false, false,
                List.of(), defaultTokenCounter(), null);

        await(context.addMessages(users("m", 200)));

        assertThat(context.length()).isEqualTo(200);
    }

    @Test
    void testEnableReloadAddsReloaderPrompt() {
        ContextWindow window = window(createContext(List.of(), 100, null, null, true, false,
                List.of(), defaultTokenCounter(), null));

        assertThat(window.getSystemMessages()).hasSize(1);
        assertThat(window.getSystemMessages().get(0).getContentAsString())
                .contains("reload_original_context_messages");
    }

    @Test
    void testEnableReloadFalseNoReloaderPrompt() {
        ContextWindow window = window(createContext(List.of(), 100, null, null, false, false,
                List.of(), defaultTokenCounter(), null));

        assertThat(window.getSystemMessages()).isEmpty();
    }

    @Test
    void testEnableReloadWithCustomSystemMessages() {
        List<BaseMessage> systemMessages = List.of(system("custom sys"));
        ContextWindow window = window(createContext(List.of(), 100, null, null, true, false,
                List.of(), defaultTokenCounter(), null), systemMessages, List.of(), null, null);

        assertThat(window.getSystemMessages()).hasSize(2);
        assertThat(window.getSystemMessages().get(0).getContentAsString()).isEqualTo("custom sys");
        assertThat(window.getSystemMessages().get(1).getContentAsString())
                .contains("reload_original_context_messages");
    }

    @Test
    void testEnableKvCacheReleaseCreatesManager() {
        SessionModelContext context = createContext(List.of(), 100, null, null, false, true,
                List.of(), defaultTokenCounter(), new RecordingKvCacheManager());

        assertThat(readField(context, "kvCacheManager")).isNotNull();
    }

    @Test
    void testEnableKvCacheReleaseCallsReleaseOnGetWindow() {
        RecordingKvCacheManager manager = new RecordingKvCacheManager();
        SessionModelContext context = createContext(List.of(), 100, null, null, false, true,
                List.of(), defaultTokenCounter(), manager);

        window(context);
        window(context);

        assertThat(manager.callCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testTokenCounterReturnsTokens() {
        SessionModelContext context = createContext();
        await(context.addMessages(List.of(user("hi"))));

        ContextStats stat = context.statistic();

        assertThat(stat.getTotalMessages()).isEqualTo(1);
        assertThat(stat.getTotalTokens()).isEqualTo(16);
        assertThat(stat.getUserMessageTokens()).isEqualTo(16);
    }

    @Test
    void testTokenCounterMockReturnsTokens() {
        SessionModelContext context = createContext(List.of(), 100, null, null, false, false,
                List.of(), new FixedTokenCounter(10, 5), null);
        await(context.addMessages(List.of(user("hi"))));

        ContextStats stat = context.statistic();
        ContextWindow window = window(context, List.of(), List.of(ToolInfo.builder().name("t1").description("d1")
                .build()), null, null);

        assertThat(stat.getUserMessageTokens()).isEqualTo(10);
        assertThat(window.getStatistic().getToolTokens()).isEqualTo(5);
    }

    @Test
    void testProcessorsEmptyAddMessagesSucceeds() {
        SessionModelContext context = createContext(List.of(), 100, null, null, false, false,
                List.of(), defaultTokenCounter(), null);

        await(context.addMessages(List.of(user("hi"))));

        assertRolesAndContents(context.getMessages(), List.of(user("hi")));
    }

    @Test
    void testProcessorExceptionDoesNotBlockAddMessages() {
        SessionModelContext context = createContext(List.of(), 10, null, null, false, false,
                List.of(new FailingProcessor()), defaultTokenCounter(), null);

        await(context.addMessages(List.of(user("msg"))));

        assertThat(context.length()).isEqualTo(1);
        assertThat(context.getMessages().get(0).getContentAsString()).isEqualTo("msg");
    }

    @Test
    void testReloaderToolOffloadThenReloadReturnsContent() {
        SessionModelContext context = createContext();
        context.offloadMessages("handle-1", List.of(user("secret"), assistant("reply")));

        SessionModelContext.ReloaderTool tool = (SessionModelContext.ReloaderTool) context.reloaderTool();
        String result = tool.reloadOriginalContextMessages("handle-1", "in_memory");

        assertThat(result).contains("handle-1");
        assertThat(result).containsAnyOf("secret", "reply");
    }

    @Test
    void testReloaderToolNonexistentReturnsFailureMessage() {
        SessionModelContext context = createContext();
        SessionModelContext.ReloaderTool tool = (SessionModelContext.ReloaderTool) context.reloaderTool();

        String result = tool.reloadOriginalContextMessages("nonexistent", "in_memory");

        assertThat(result).contains("Failed to reload");
        assertThat(result).contains("nonexistent");
    }

    @Test
    void testReloaderToolCardIdContainsSessionAndContext() {
        SessionModelContext context = createContext();
        SessionModelContext.ReloaderTool tool = (SessionModelContext.ReloaderTool) context.reloaderTool();

        assertThat(tool.name()).isEqualTo("reload_original_context_messages");
        assertThat(tool.toolInfo().getDescription()).contains("offloaded");
    }

    @Test
    void testOffloadMessagesSaveStateIncludesOffload() {
        SessionModelContext context = createContext();
        context.offloadMessages("h1", List.of(user("x")));

        Map<String, Object> state = context.saveState();

        assertThat(state).containsKey("offload_messages");
        assertThat(state.get("offload_messages").toString()).contains("h1");
    }

    @Test
    void testSaveStateStructure() {
        SessionModelContext context = createContext();
        await(context.addMessages(List.of(user("a"))));

        Map<String, Object> state = context.saveState();

        assertThat(state).containsKeys("messages", "offload_messages");
        assertThat((List<?>) state.get("messages")).hasSize(1);
    }

    @Test
    void testLoadStateRestoresMessages() {
        SessionModelContext context = createContext();
        List<BaseMessage> messages = List.of(user("loaded"), assistant("resp"));

        context.loadState(Map.of(context.contextId(), Map.of("messages", messages, "offload_messages", Map.of())));

        assertThat(context.getMessages()).containsExactlyElementsOf(messages);
    }

    @Test
    void testLoadStateRestoresOffloadMessages() {
        SessionModelContext context = createContext();
        context.loadState(Map.of(context.contextId(), Map.of("messages", List.of(), "offload_messages",
                Map.of("h1", List.of(user("offloaded"))))));
        SessionModelContext.ReloaderTool tool = (SessionModelContext.ReloaderTool) context.reloaderTool();

        String result = tool.reloadOriginalContextMessages("h1", "in_memory");

        assertThat(result).contains("offloaded");
    }

    @Test
    void testLoadStateEmptyStateClearsBuffer() {
        SessionModelContext context = createContext();
        await(context.addMessages(List.of(user("x"))));

        context.loadState(Map.of());

        assertThat(context.length()).isZero();
    }

    @Test
    void testLoadStateWrongContextIdClearsBuffer() {
        SessionModelContext context = createContext();
        await(context.addMessages(List.of(user("x"))));

        context.loadState(Map.of("other_context", Map.of("messages", List.of(user("other")),
                "offload_messages", Map.of())));

        assertThat(context.length()).isZero();
    }

    @Test
    void testGetContextWindowInvalidDialogueRound() {
        SessionModelContext context = createContext();

        assertThatThrownBy(() -> window(context, List.of(), List.of(), null, 0))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_EXECUTION_ERROR.getCode());
    }

    @Test
    void testGetContextWindowWithTools() {
        SessionModelContext context = createContext();
        List<ToolInfo> tools = List.of(ToolInfo.builder().name("my_tool").description("test").build());

        ContextWindow window = window(context, List.of(), tools, null, null);

        assertThat(window.getTools()).containsExactlyElementsOf(tools);
        assertThat(window.getStatistic().getTools()).isEqualTo(1);
    }

    @Test
    void testDialogueRoundOverridesWindowSizeWhenBothPassed() {
        SessionModelContext context = createContext(List.of(), 100, 1, null, false, false,
                List.of(), defaultTokenCounter(), null);
        List<List<BaseMessage>> dialogues = List.of(List.of(user("u1"), assistant("a1")),
                List.of(user("u2"), assistant("a2")));
        List<BaseMessage> messages = flatten(dialogues);
        await(context.addMessages(messages));

        ContextWindow window = window(context, List.of(), List.of(), 100, 1);

        assertThat(window.getContextMessages()).containsExactlyElementsOf(dialogues.get(1));
    }

    @Test
    void testSessionIdAndContextId() {
        SessionModelContext context = createContext();

        assertThat(context.sessionId()).isEqualTo(SESSION_ID);
        assertThat(context.contextId()).isEqualTo(CONTEXT_ID);
    }

    @Test
    void testClearMessagesWithHistoryTrueClearsAll() {
        SessionModelContext context = createContext(List.of(user("h1")));
        await(context.addMessages(List.of(user("n1"))));

        await(context.clearMessages(true));

        assertThat(context.length()).isZero();
    }

    @Test
    void testClearMessagesWithHistoryFalseKeepsHistory() {
        SessionModelContext context = createContext(List.of(user("h1")));
        await(context.addMessages(List.of(user("n1"))));

        await(context.clearMessages(false));

        assertThat(context.length()).isEqualTo(1);
        assertThat(context.getMessages().get(0).getContentAsString()).isEqualTo("h1");
    }

    @Test
    void testModelContextWindowWithIncompleteDialogueRound() {
        SessionModelContext context = createContext(List.of(), 100, 1, null, false, false,
                List.of(), defaultTokenCounter(), null);
        List<List<BaseMessage>> dialogues = incompleteDialogues();
        List<BaseMessage> messages = flatten(dialogues);

        await(context.addMessages(messages));
        assertThat(window(context).getContextMessages()).containsExactlyElementsOf(dialogues.get(2));

        await(context.addMessages(messages));
        assertThat(window(context, List.of(), List.of(), null, 1).getContextMessages())
                .containsExactlyElementsOf(dialogues.get(2));
        await(context.clearMessages(true));

        await(context.addMessages(messages));
        assertThat(window(context, List.of(), List.of(), null, 2).getContextMessages())
                .containsExactlyElementsOf(concat(dialogues.get(0).subList(6, 7), concat(dialogues.get(1),
                        dialogues.get(2))));
        await(context.clearMessages(true));

        await(context.addMessages(messages));
        assertThat(window(context, List.of(), List.of(), null, 3).getContextMessages())
                .containsExactlyElementsOf(messages);
        await(context.clearMessages(true));

        await(context.addMessages(messages));
        assertThat(window(context, List.of(), List.of(), null, 4).getContextMessages())
                .containsExactlyElementsOf(messages);
        await(context.clearMessages(true));
    }

    private static SessionModelContext createContext() {
        return createContext(List.of());
    }

    private static SessionModelContext createContext(List<BaseMessage> history) {
        return createContext(history, 100, null, null, false, false, List.of(), defaultTokenCounter(), null);
    }

    private static SessionModelContext createContext(List<BaseMessage> history, Integer windowLimit,
                                                     Integer dialogueRound, Integer maxContextMessageNum,
                                                     boolean enableReload, boolean enableKvCacheRelease,
                                                     List<SessionModelContext.ContextProcessorPort> processors,
                                                     SessionModelContext.TokenCounterPort tokenCounter,
                                                     SessionModelContext.KvCacheManagerPort kvCacheManager) {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setDefaultWindowMessageNum(windowLimit);
        config.setDefaultWindowRoundNum(dialogueRound);
        config.setMaxContextMessageNum(maxContextMessageNum);
        config.setEnableReload(enableReload);
        config.setEnableKvCacheRelease(enableKvCacheRelease);
        return new SessionModelContext(CONTEXT_ID, SESSION_ID, config, history, processors, tokenCounter,
                null, null, null, kvCacheManager, null);
    }

    private static SessionModelContext.TokenCounterPort defaultTokenCounter() {
        return messages -> messages == null ? 0 : messages.size() * 16;
    }

    private static ContextWindow window(SessionModelContext context) {
        return window(context, List.of(), List.of(), null, null);
    }

    private static ContextWindow window(SessionModelContext context, List<BaseMessage> systemMessages,
                                        List<ToolInfo> tools, Integer windowSize, Integer dialogueRound) {
        return await(context.getContextWindow(systemMessages, tools, windowSize, dialogueRound, Map.of()));
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static UserMessage user(String content) {
        return new UserMessage(content);
    }

    private static AssistantMessage assistant(String content) {
        return new AssistantMessage(content);
    }

    private static SystemMessage system(String content) {
        return new SystemMessage(content);
    }

    private static ToolMessage tool(String content, String toolCallId) {
        return new ToolMessage(content, toolCallId);
    }

    private static AssistantMessage assistantToolCall(String id) {
        return AssistantMessage.builder()
                .role("assistant")
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .name("test-tool")
                        .type("function")
                        .arguments("")
                        .build()))
                .build();
    }

    private static List<BaseMessage> users(String prefix, int count) {
        List<BaseMessage> messages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            messages.add(user(prefix + "-" + index));
        }
        return messages;
    }

    private static BaseMessage generateMessage(int index) {
        int remainder = index % 4;
        if (remainder == 0) {
            return user("use message-" + index);
        }
        if (remainder == 1) {
            return system("system message-" + index);
        }
        if (remainder == 2) {
            return assistant("ai message-" + index);
        }
        return tool("tool message-" + index, "");
    }

    private static List<List<BaseMessage>> completeDialogues() {
        return List.of(
                List.of(user("user-1"), assistantToolCall("tc-1", "tc-2", "tc-3"), tool("tool-1", "tc-1"),
                        tool("tool-2", "tc-2"), tool("tool-3", "tc-3"), assistant("assistant-2")),
                List.of(user("user-2"), assistantToolCall("tc-1", "tc-2"), tool("tool-4", "tc-1"),
                        tool("tool-5", "tc-2"), assistant("assistant-4")),
                List.of(user("user-3"), assistantToolCall("tc-1"), tool("tool-6", "tc-1"),
                        assistant("assistant-5"))
        );
    }

    private static List<List<BaseMessage>> incompleteDialogues() {
        return List.of(
                List.of(user("user-1"), assistantToolCall("tc-1", "tc-2", "tc-3"), tool("tool-1", "tc-1"),
                        tool("tool-2", "tc-2"), tool("tool-3", "tc-3"), assistant("assistant-2"),
                        user("user-1-1")),
                List.of(user("user-2"), assistantToolCall("tc-1", "tc-2"), tool("tool-4", "tc-1"),
                        tool("tool-5", "tc-2"), assistant("assistant-4")),
                List.of(user("user-3"))
        );
    }

    private static AssistantMessage assistantToolCall(String... ids) {
        List<ToolCall> calls = new ArrayList<>();
        for (String id : ids) {
            calls.add(ToolCall.builder().id(id).name("test-tool").type("function").arguments("").build());
        }
        return AssistantMessage.builder().role("assistant").content("").toolCalls(calls).build();
    }

    private static List<BaseMessage> flatten(List<List<BaseMessage>> groups) {
        List<BaseMessage> messages = new ArrayList<>();
        for (List<BaseMessage> group : groups) {
            messages.addAll(group);
        }
        return messages;
    }

    private static List<BaseMessage> concat(List<BaseMessage> first, List<BaseMessage> second) {
        List<BaseMessage> messages = new ArrayList<>(first);
        messages.addAll(second);
        return messages;
    }

    @SafeVarargs
    private static List<BaseMessage> concat(List<BaseMessage> first, List<BaseMessage>... rest) {
        List<BaseMessage> messages = new ArrayList<>(first);
        for (List<BaseMessage> item : rest) {
            messages.addAll(item);
        }
        return messages;
    }

    private static void assertRolesAndContents(List<BaseMessage> actual, List<BaseMessage> expected) {
        assertThat(actual).extracting(BaseMessage::getRole)
                .containsExactlyElementsOf(expected.stream().map(BaseMessage::getRole).toList());
        assertThat(actual).extracting(BaseMessage::getContentAsString)
                .containsExactlyElementsOf(expected.stream().map(BaseMessage::getContentAsString).toList());
    }

    private static void assertMessageStats(ContextStats stat, int total, int system, int assistant, int tool, int user) {
        assertThat(stat.getTotalMessages()).isEqualTo(total);
        assertThat(stat.getSystemMessages()).isEqualTo(system);
        assertThat(stat.getAssistantMessages()).isEqualTo(assistant);
        assertThat(stat.getToolMessages()).isEqualTo(tool);
        assertThat(stat.getUserMessages()).isEqualTo(user);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<BaseMessage> invalidMessages() {
        List raw = List.of(user("test"), Map.of("role", "user", "content", "test"));
        return raw;
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FixedTokenCounter implements SessionModelContext.ToolTokenCounterPort {
        private final int messageTokens;
        private final int toolTokens;

        private FixedTokenCounter(int messageTokens, int toolTokens) {
            this.messageTokens = messageTokens;
            this.toolTokens = toolTokens;
        }

        @Override
        public int countTokens(List<BaseMessage> messages) {
            return messageTokens;
        }

        @Override
        public int countTools(List<ToolInfo> tools) {
            return toolTokens;
        }
    }

    private static final class FailingProcessor implements SessionModelContext.ContextProcessorPort {
        @Override
        public String processorType() {
            return "MockProcessor";
        }

        @Override
        public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messages,
                                                           Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                                List<BaseMessage> messages,
                                                                                boolean force,
                                                                                Map<String, Object> kwargs) {
            throw new IllegalStateException("processor failed");
        }
    }

    private static final class RecordingKvCacheManager implements SessionModelContext.KvCacheManagerPort {
        private int callCount;

        @Override
        public void release(ContextWindow contextWindow, Object model) {
            callCount++;
        }
    }
}
