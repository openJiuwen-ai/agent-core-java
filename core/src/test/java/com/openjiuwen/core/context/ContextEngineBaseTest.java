/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for context-engine base contracts.
 *
 * <p>Mirrors Python's {@code ModelContext}, {@code ContextStats}, and
 * {@code ContextWindow} in {@code openjiuwen/core/context_engine/base.py}.</p>
 */
class ContextEngineBaseTest {

    @Test
    void contextStatsDefaultsAllCountersToZero() {
        ContextStats stats = new ContextStats();

        assertThat(stats.getTotalMessages()).isZero();
        assertThat(stats.getTotalTokens()).isZero();
        assertThat(stats.getTotalDialogues()).isZero();
        assertThat(stats.getSystemMessages()).isZero();
        assertThat(stats.getUserMessages()).isZero();
        assertThat(stats.getAssistantMessages()).isZero();
        assertThat(stats.getToolMessages()).isZero();
        assertThat(stats.getTools()).isZero();
        assertThat(stats.getSystemMessageTokens()).isZero();
        assertThat(stats.getUserMessageTokens()).isZero();
        assertThat(stats.getAssistantMessageTokens()).isZero();
        assertThat(stats.getToolMessageTokens()).isZero();
        assertThat(stats.getToolTokens()).isZero();
    }

    @Test
    void contextStatsStoresAllPythonFields() {
        ContextStats stats = new ContextStats();
        stats.setTotalMessages(4);
        stats.setTotalTokens(40);
        stats.setTotalDialogues(2);
        stats.setSystemMessages(1);
        stats.setUserMessages(1);
        stats.setAssistantMessages(1);
        stats.setToolMessages(1);
        stats.setTools(3);
        stats.setSystemMessageTokens(5);
        stats.setUserMessageTokens(6);
        stats.setAssistantMessageTokens(7);
        stats.setToolMessageTokens(8);
        stats.setToolTokens(9);

        assertThat(stats.getTotalMessages()).isEqualTo(4);
        assertThat(stats.getTotalTokens()).isEqualTo(40);
        assertThat(stats.getTotalDialogues()).isEqualTo(2);
        assertThat(stats.getSystemMessages()).isEqualTo(1);
        assertThat(stats.getUserMessages()).isEqualTo(1);
        assertThat(stats.getAssistantMessages()).isEqualTo(1);
        assertThat(stats.getToolMessages()).isEqualTo(1);
        assertThat(stats.getTools()).isEqualTo(3);
        assertThat(stats.getSystemMessageTokens()).isEqualTo(5);
        assertThat(stats.getUserMessageTokens()).isEqualTo(6);
        assertThat(stats.getAssistantMessageTokens()).isEqualTo(7);
        assertThat(stats.getToolMessageTokens()).isEqualTo(8);
        assertThat(stats.getToolTokens()).isEqualTo(9);
    }

    @Test
    void contextWindowDefaultsListsAndStatistic() {
        ContextWindow window = new ContextWindow();

        assertThat(window.getSystemMessages()).isEmpty();
        assertThat(window.getContextMessages()).isEmpty();
        assertThat(window.getTools()).isEmpty();
        assertThat(window.getStatistic()).isEqualTo(new ContextStats());
        assertThat(window.getMessages()).isEmpty();
    }

    @Test
    void contextWindowCombinesSystemAndContextMessagesInOrder() {
        BaseMessage system = new BaseMessage("system", "rules");
        BaseMessage user = new BaseMessage("user", "question");
        ToolInfo tool = ToolInfo.builder().name("search").description("Search").build();
        ContextStats stats = new ContextStats();
        stats.setTotalMessages(2);

        ContextWindow window = new ContextWindow(List.of(system), List.of(user), List.of(tool), stats);

        assertThat(window.getMessages()).containsExactly(system, user);
        assertThat(window.getTools()).containsExactly(tool);
        assertThat(window.getStatistic()).isSameAs(stats);
    }

    @Test
    void contextWindowCopiesInputLists() {
        List<BaseMessage> contextMessages = new ArrayList<>();
        contextMessages.add(new BaseMessage("user", "first"));
        ContextWindow window = new ContextWindow(null, contextMessages, null, null);

        contextMessages.add(new BaseMessage("user", "second"));

        assertThat(window.getContextMessages()).extracting(BaseMessage::getContent).containsExactly("first");
    }

    @Test
    void modelContextContractCanBeImplementedWithPythonMethodSemantics() {
        RecordingContext context = new RecordingContext();
        BaseMessage first = new BaseMessage("user", "hello");

        context.addMessages(first).toCompletableFuture().join();

        assertThat(context.length()).isEqualTo(1);
        assertThat(context.getMessages(null, true)).containsExactly(first);
        assertThat(context.popMessages(1, true)).containsExactly(first);
        assertThat(context.length()).isZero();
        assertThat(context.sessionId()).isEqualTo("session-a");
        assertThat(context.contextId()).isEqualTo("context-a");
        assertThat(context.tokenCounter().countTokens(List.of(first))).isEqualTo(1);
        assertThat(context.reloaderTool().name()).isEqualTo("reload");
    }

    private static final class RecordingContext implements ModelContext {
        private final List<BaseMessage> messages = new ArrayList<>();
        private final ContextStats stats = new ContextStats();

        @Override
        public int length() {
            return messages.size();
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
            this.messages.clear();
            this.messages.addAll(messages);
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            int count = Math.min(size, messages.size());
            List<BaseMessage> popped = new ArrayList<>(messages.subList(0, count));
            messages.subList(0, count).clear();
            return popped;
        }

        @Override
        public CompletionStage<Void> clearMessages(boolean withHistory) {
            messages.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
            messages.add(message);
            return CompletableFuture.completedFuture(getMessages(null, true));
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
            this.messages.addAll(messages);
            return CompletableFuture.completedFuture(getMessages(null, true));
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages,
                                                               List<ToolInfo> tools,
                                                               Integer windowSize,
                                                               Integer dialogueRound,
                                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new ContextWindow(systemMessages, messages, tools, stats));
        }

        @Override
        public ContextStats statistic() {
            return stats;
        }

        @Override
        public String sessionId() {
            return "session-a";
        }

        @Override
        public String contextId() {
            return "context-a";
        }

        @Override
        public TokenCounterPort tokenCounter() {
            return List::size;
        }

        @Override
        public ToolPort reloaderTool() {
            return () -> "reload";
        }
    }
}
