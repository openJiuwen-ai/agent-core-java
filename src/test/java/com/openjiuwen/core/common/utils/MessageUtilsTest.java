/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for {@link MessageUtils}.
 *
 * <p>Mirrors Python's {@code MessageUtils} in
 * {@code openjiuwen/core/common/utils/message_utils.py}.</p>
 */
class MessageUtilsTest {

    @Test
    void shouldAddUserMessageSkipsDuplicateUserContentOnly() {
        FakeContextEngine engine = new FakeContextEngine();
        FakeSession session = new FakeSession("session-a");

        assertThat(MessageUtils.shouldAddUserMessage("hello", engine, session)).isTrue();

        engine.defaultContext.messages.add(new UserMessage("hello"));
        assertThat(MessageUtils.shouldAddUserMessage("hello", engine, session)).isFalse();
        assertThat(MessageUtils.shouldAddUserMessage("new", engine, session)).isTrue();

        engine.defaultContext.messages.clear();
        engine.defaultContext.messages.add(new AssistantMessage("hello"));
        assertThat(MessageUtils.shouldAddUserMessage("hello", engine, session)).isTrue();
    }

    @Test
    void addUserMessageAddsWhenNotDuplicateAndPreservesDynamicContent() {
        FakeContextEngine engine = new FakeContextEngine();
        FakeSession session = new FakeSession("session-a");
        Map<String, Object> content = Map.of("text", "hello");

        MessageUtils.addUserMessage(content, engine, session).toCompletableFuture().join();

        assertThat(engine.defaultContext.messages).singleElement().satisfies(message -> {
            assertThat(message).isInstanceOf(UserMessage.class);
            assertThat(message.getRole()).isEqualTo("user");
            assertThat(message.getContent()).isEqualTo(content);
        });
    }

    @Test
    void addUserMessageDoesNotAddDuplicateStringContent() {
        FakeContextEngine engine = new FakeContextEngine();
        FakeSession session = new FakeSession("session-a");
        engine.defaultContext.messages.add(new UserMessage("hello"));

        MessageUtils.addUserMessage("hello", engine, session).toCompletableFuture().join();

        assertThat(engine.defaultContext.messages).hasSize(1);
    }

    @Test
    void addAiAndToolMessagesIgnoreNullAndAppendNonNull() {
        FakeContextEngine engine = new FakeContextEngine();
        FakeSession session = new FakeSession("session-a");

        MessageUtils.addAiMessage(null, engine, session).toCompletableFuture().join();
        MessageUtils.addToolMessage(null, engine, session).toCompletableFuture().join();
        MessageUtils.addAiMessage(new AssistantMessage("answer"), engine, session).toCompletableFuture().join();
        MessageUtils.addToolMessage(new ToolMessage("tool", "call-1"), engine, session).toCompletableFuture().join();

        assertThat(engine.defaultContext.messages).hasSize(2);
        assertThat(engine.defaultContext.messages.get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(engine.defaultContext.messages.get(1)).isInstanceOf(ToolMessage.class);
    }

    @Test
    void addWorkflowMessageUsesWorkflowContextAndSessionId() {
        FakeContextEngine engine = new FakeContextEngine();
        FakeSession session = new FakeSession("session-a");
        BaseMessage message = new BaseMessage("workflow", "payload");

        MessageUtils.addWorkflowMessage(message, "workflow-1", engine, session).toCompletableFuture().join();

        assertThat(engine.defaultContext.messages).isEmpty();
        assertThat(engine.contexts.get("workflow-1|session-a").messages).containsExactly(message);
    }

    @Test
    void getChatHistoryReturnsLastTwoMessagesPerReservedRound() {
        FakeContextEngine engine = new FakeContextEngine();
        FakeSession session = new FakeSession("session-a");
        for (int index = 0; index < 6; index++) {
            engine.defaultContext.messages.add(new BaseMessage("role", "m" + index));
        }

        List<BaseMessage> history = MessageUtils.getChatHistory(engine, session, new FakeAgentConfig(2));

        assertThat(history).extracting(BaseMessage::getContent).containsExactly("m2", "m3", "m4", "m5");
    }

    @Test
    void getChatHistoryWithZeroRoundsMatchesPythonSliceZeroAndReturnsAll() {
        FakeContextEngine engine = new FakeContextEngine();
        FakeSession session = new FakeSession("session-a");
        engine.defaultContext.messages.add(new BaseMessage("role", "m0"));
        engine.defaultContext.messages.add(new BaseMessage("role", "m1"));

        List<BaseMessage> history = MessageUtils.getChatHistory(engine, session, new FakeAgentConfig(0));

        assertThat(history).extracting(BaseMessage::getContent).containsExactly("m0", "m1");
    }

    private static final class FakeContextEngine implements MessageUtils.ContextEnginePort {
        private final FakeAgentContext defaultContext = new FakeAgentContext();
        private final Map<String, FakeAgentContext> contexts = new LinkedHashMap<>();

        @Override
        public MessageUtils.AgentContextPort getContext(String sessionId) {
            return defaultContext;
        }

        @Override
        public MessageUtils.AgentContextPort getContext(String contextId, String sessionId) {
            return contexts.computeIfAbsent(contextId + "|" + sessionId, ignored -> new FakeAgentContext());
        }
    }

    private static final class FakeAgentContext implements MessageUtils.AgentContextPort {
        private final List<BaseMessage> messages = new ArrayList<>();

        @Override
        public List<BaseMessage> getMessages() {
            return messages;
        }

        @Override
        public CompletionStage<Void> addMessages(BaseMessage message) {
            messages.add(message);
            return CompletableFuture.completedFuture(null);
        }
    }

    private record FakeSession(String id) implements MessageUtils.SessionPort {
        @Override
        public String getSessionId() {
            return id;
        }
    }

    private record FakeAgentConfig(int rounds) implements MessageUtils.AgentConfigView {
        @Override
        public MessageUtils.ConstrainView constrain() {
            return () -> rounds;
        }
    }
}
