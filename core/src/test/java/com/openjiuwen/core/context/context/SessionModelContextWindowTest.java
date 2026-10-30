/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verifies that bounded context windows retain the history prefix until a limit requires trimming.
 *
 * @since 2026-10-30
 */
class SessionModelContextWindowTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10})
    void defaultWindowWithinRoundLimitRetainsHistoryPrefix(int rounds) {
        List<BaseMessage> history = historyWithRounds(rounds);
        SessionModelContext context = createContext(new ContextEngineConfig(), history);

        ContextWindow window = window(context, null);

        assertThat(window.getMessages()).containsExactlyElementsOf(history);
        assertThat(window.getStatistic().getTotalDialogues()).isEqualTo(rounds);
        assertThat(context.getMessages()).containsExactlyElementsOf(history);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10})
    void explicitRoundLimitRetainsPrefixWhenAllRoundsFit(int roundLimit) {
        List<BaseMessage> history = historyWithRounds(1);
        SessionModelContext context = createContext(new ContextEngineConfig(), history);

        ContextWindow window = window(context, roundLimit);

        assertThat(window.getMessages()).containsExactlyElementsOf(history);
    }

    @Test
    void explicitNullConfigurationRetainsHistoryPrefix() {
        ContextEngineConfig config = ContextEngineConfig.builder()
                .defaultWindowRoundNum(null).defaultWindowMessageNum(null).maxContextMessageNum(null).build();
        List<BaseMessage> history = historyWithRounds(1);
        SessionModelContext context = createContext(config, history);

        ContextWindow window = window(context, null);

        assertThat(window.getMessages()).containsExactlyElementsOf(history);
    }

    @Test
    void defaultRoundLimitStillTrimsExcessDialogue() {
        List<BaseMessage> history = historyWithRounds(11);
        SessionModelContext context = createContext(new ContextEngineConfig(), history);

        ContextWindow window = window(context, null);

        assertThat(window.getMessages()).containsExactlyElementsOf(history.subList(4, history.size()));
        assertThat(window.getStatistic().getTotalDialogues()).isEqualTo(10);
        assertThat(context.getMessages()).containsExactlyElementsOf(history);
    }

    @Test
    void explicitRoundLimitTrimsHistoryAndKeepsSeparateSystemPrompt() {
        List<BaseMessage> history = historyWithRounds(3);
        SessionModelContext context = createContext(new ContextEngineConfig(), history);
        List<BaseMessage> systemMessages = List.of(new SystemMessage("current instructions"));

        ContextWindow window = context.getContextWindow(systemMessages, null, null, 2, Map.of())
                .toCompletableFuture().join();

        assertThat(window.getSystemMessages()).containsExactlyElementsOf(systemMessages);
        assertThat(window.getContextMessages()).containsExactlyElementsOf(history.subList(4, history.size()));
        assertThat(window.getStatistic().getTotalDialogues()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void pendingUserRoundWithinLimitRetainsHistoryPrefix(int completedRounds) {
        List<BaseMessage> history = historyWithRounds(completedRounds);
        history.add(new UserMessage("pending question"));
        SessionModelContext context = createContext(new ContextEngineConfig(), history);

        ContextWindow window = window(context, null);

        assertThat(window.getMessages()).containsExactlyElementsOf(history);
        assertThat(window.getStatistic().getTotalDialogues()).isEqualTo(completedRounds + 1);
    }

    @Test
    void historyWithoutDialogueRemainsVisible() {
        List<BaseMessage> history = historyWithRounds(0);
        SessionModelContext context = createContext(new ContextEngineConfig(), history);

        ContextWindow window = window(context, null);

        assertThat(window.getMessages()).containsExactlyElementsOf(history);
        assertThat(window.getStatistic().getTotalDialogues()).isZero();
    }

    @Test
    void defaultMessageLimitStillBoundsASingleLargeRound() {
        ContextEngineConfig config = ContextEngineConfig.builder().maxContextMessageNum(300).build();
        List<BaseMessage> history = historyWithRounds(0);
        for (int index = 0; index < 201; index++) {
            history.add(new UserMessage("question part " + index));
        }
        SessionModelContext context = createContext(config, history);

        ContextWindow window = window(context, null);

        assertThat(window.getMessages()).hasSize(200).containsExactlyElementsOf(history.subList(3, history.size()));
        assertThat(context.getMessages()).containsExactlyElementsOf(history);
    }

    @Test
    void toolRoundRetainsPrefixAndCompleteCallResultPair() {
        AssistantMessage toolRequest = new AssistantMessage("");
        toolRequest.setToolCalls(List.of(ToolCall.builder().id("call-1").type("function")
                .name("lookup").arguments("{}").build()));
        List<BaseMessage> history = List.of(new SystemMessage("instructions"), new UserMessage("question"),
                toolRequest, new ToolMessage("result", "call-1"), new AssistantMessage("answer"));
        SessionModelContext context = createContext(new ContextEngineConfig(), history);

        ContextWindow window = window(context, null);

        assertThat(window.getMessages()).containsExactlyElementsOf(history);
        assertThat(window.getStatistic().getTotalDialogues()).isEqualTo(1);
    }

    @Test
    void restoredStateRetainsHistoryPrefixInDefaultWindow() {
        List<BaseMessage> history = historyWithRounds(2);
        SessionModelContext original = createContext(new ContextEngineConfig(), history);
        SessionModelContext restored = createContext(new ContextEngineConfig(), List.of());

        restored.loadState(Map.of("context", original.saveState()));
        ContextWindow window = window(restored, null);

        assertThat(window.getMessages()).containsExactlyElementsOf(history);
        assertThat(restored.getMessages()).containsExactlyElementsOf(history);
    }

    private static List<BaseMessage> historyWithRounds(int rounds) {
        List<BaseMessage> history = new ArrayList<>();
        history.add(new SystemMessage("instructions"));
        history.add(new BaseMessage("system", "runtime context"));
        for (int index = 0; index < rounds; index++) {
            history.add(new UserMessage("question " + index));
            history.add(new AssistantMessage("answer " + index));
        }
        return history;
    }

    private static SessionModelContext createContext(ContextEngineConfig config, List<BaseMessage> history) {
        return new SessionModelContext("context", "session", config, history, List.of(), null);
    }

    private static ContextWindow window(SessionModelContext context, Integer dialogueRound) {
        return context.getContextWindow(null, null, null, dialogueRound, Map.of()).toCompletableFuture().join();
    }
}
