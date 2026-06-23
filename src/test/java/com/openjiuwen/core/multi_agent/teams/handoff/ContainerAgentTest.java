/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for the internal handoff container wrapper.
 *
 * <p>Mirrors Python's {@code ContainerAgent} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/container_agent.py}.</p>
 */
class ContainerAgentTest {

    @Test
    void targetProviderIsLazyAndCalledOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        StubAgent target = new StubAgent(card("a"), Map.of("ok", true));
        TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> {
            calls.incrementAndGet();
            return target;
        });

        assertThat(agent.target()).isSameAs(target);
        assertThat(agent.target()).isSameAs(target);
        assertThat(calls).hasValue(1);
    }

    @Test
    void buildAgentInputPreservesRawMessageUnlessHistoryExists() {
        TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                () -> new StubAgent(card("a"), Map.of()));
        assertThat(agent.build(new HandoffRequest("hello"))).isEqualTo("hello");

        List<Map<String, Object>> history = new ArrayList<>(List.of(Map.of("agent", "a")));
        Object wrapped = agent.build(new HandoffRequest("hello", history, null));

        assertThat(wrapped).isInstanceOf(Map.class);
        assertThat(asStringObjectMap(wrapped)).containsEntry("query", "hello")
                .containsEntry("handoff_history", history);
    }

    @Test
    void buildAgentInputMergesHistoryIntoDictMessage() {
        TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                () -> new StubAgent(card("a"), Map.of()));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", "q");
        List<Map<String, Object>> history = List.of(Map.of("agent", "a"));

        Object result = agent.build(new HandoffRequest(input, history, null));

        assertThat(asStringObjectMap(result)).containsEntry("query", "q")
                .containsEntry("handoff_history", history);
    }

    @Test
    void stripHandoffMessagesDropsToolMessagesAndAssistantToolCalls() {
        Map<String, Object> assistantToolCall = new LinkedHashMap<>();
        assistantToolCall.put("role", "assistant");
        assistantToolCall.put("content", "handoff");
        assistantToolCall.put("tool_calls", List.of(Map.of("id", "call-1")));

        List<Object> cleaned = ContainerAgent.stripHandoffMessages(List.of(
                Map.of("role", "user", "content", "keep"),
                Map.of("role", "tool", "content", "drop"),
                assistantToolCall,
                Map.of("role", "assistant", "content", "keep")
        ));

        List<Object> contents = cleaned.stream()
                .map(item -> ((Map<?, ?>) item).get("content"))
                .collect(Collectors.toList());
        assertThat(contents).containsExactly("keep", "keep");
    }

    @Test
    void contextHistoryIsSavedWithDedupAndInjectedIntoAgentSession() {
        TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                () -> new StubAgent(card("a"), Map.of()));
        AgentSession sourceSession = AgentSession.createAgentSession("sid", null, card("a"));
        AgentTeamSession teamSession = new AgentTeamSession("sid", null, "team");
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "hello");
        sourceSession.updateState(Map.of("context", Map.of(
                ContainerAgent.DEFAULT_CONTEXT_ID,
                Map.of("messages", List.of(
                        userMessage,
                        Map.of("role", "tool", "content", "drop")
                ))
        )));

        agent.save(sourceSession, teamSession);
        agent.save(sourceSession, teamSession);

        assertThat(asObjectList(teamSession.getState(ContainerAgent.CONTEXT_HISTORY_KEY)))
                .containsExactly(userMessage);

        AgentSession targetSession = AgentSession.createAgentSession("sid", null, card("a"));
        agent.inject(targetSession, teamSession);
        Map<?, ?> context = (Map<?, ?>) targetSession.getState("context");
        Map<?, ?> defaultContext = (Map<?, ?>) context.get(ContainerAgent.DEFAULT_CONTEXT_ID);
        assertThat(asObjectList(defaultContext.get("messages"))).containsExactly(userMessage);
        assertThat((Map<?, ?>) defaultContext.get("offload_messages")).isEmpty();
    }

    @Test
    void invokeRaisesStructuredErrorWhenCoordinatorIsMissing() {
        TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                () -> new StubAgent(card("a"), Map.of()),
                Set.of(),
                ignored -> null);

        assertThatThrownBy(() -> agent.invoke(new HandoffRequest("hi"), null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("ContainerAgent invoked without a HandoffTeam session");
    }

    @Test
    void invokeCompletesCoordinatorWhenNoHandoffSignalExists() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a", "b"));
        AgentTeamSession session = new AgentTeamSession("sid", null, "team");
        TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                () -> new StubAgent(card("a"), Map.of("answer", "done")),
                Set.of(),
                ignored -> coordinator);

        Object result = agent.invoke(new HandoffRequest("hi", List.of(), session), null)
                .toCompletableFuture()
                .join();

        assertThat(result).isEqualTo(Map.of());
        assertThat(coordinator.doneFuture().join()).isEqualTo(Map.of("answer", "done"));
    }

    @Test
    void invokePublishesNextRequestWhenHandoffIsApproved() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a", "b"));
        AgentTeamSession session = new AgentTeamSession("sid", null, "team");
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put(HandoffSignal.HANDOFF_TARGET_KEY, "b");
        signal.put(HandoffSignal.HANDOFF_MESSAGE_KEY, "next");
        signal.put(HandoffSignal.HANDOFF_REASON_KEY, "handoff");
        TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                () -> new StubAgent(card("a"), signal),
                Set.of("b"),
                ignored -> coordinator);

        Object result = agent.invoke(new HandoffRequest("original", List.of(), session), null)
                .toCompletableFuture()
                .join();

        assertThat(result).isEqualTo(Map.of());
        assertThat(agent.publishedTopic).isEqualTo("container_b");
        assertThat(agent.publishedSessionId).isEqualTo("sid");
        assertThat(agent.publishedMessage).isInstanceOf(HandoffRequest.class);
        HandoffRequest next = (HandoffRequest) agent.publishedMessage;
        assertThat(next.getInputMessage()).isEqualTo("next");
        assertThat(next.getHistory()).hasSize(1);
        assertThat(next.getHistory().getFirst()).containsEntry("agent", "a")
                .containsEntry("output", signal);
    }

    @Test
    void streamDelegatesInvokeAndYieldsSingletonResult() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a", "b"));
        AgentTeamSession session = new AgentTeamSession("sid", null, "team");
        TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                () -> new StubAgent(card("a"), Map.of("ok", true)),
                Set.of(),
                ignored -> coordinator);

        Iterator<Object> chunks = agent.stream(new HandoffRequest("hi", List.of(), session), null, List.of());

        assertThat(chunks.hasNext()).isTrue();
        assertThat(chunks.next()).isEqualTo(Map.of());
        assertThat(chunks.hasNext()).isFalse();
    }

    private static AgentCard card(String id) {
        return new AgentCard(id, id, "agent " + id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asObjectList(Object value) {
        return (List<Object>) value;
    }

    private static final class TestableContainerAgent extends ContainerAgent {
        private Object publishedMessage;
        private String publishedTopic;
        private String publishedSessionId;

        private TestableContainerAgent(AgentCard card, java.util.function.Supplier<? extends BaseAgent> provider) {
            this(card, provider, Set.of(), ignored -> null);
        }

        private TestableContainerAgent(AgentCard card,
                                       java.util.function.Supplier<? extends BaseAgent> provider,
                                       Set<String> allowedTargets,
                                       java.util.function.Function<String, HandoffOrchestrator> lookup) {
            super(card, provider, allowedTargets, lookup);
        }

        private Object build(HandoffRequest request) {
            return buildAgentInput(request);
        }

        private BaseAgent target() {
            return getTargetAgent();
        }

        private void save(AgentSessionApi agentSession, AgentTeamSession teamSession) {
            saveContextToTeamSession(agentSession, teamSession);
        }

        private void inject(AgentSessionApi agentSession, AgentTeamSession teamSession) {
            injectContextHistory(agentSession, teamSession);
        }

        @Override
        public CompletableFuture<Void> publish(Object message, String topicId, String sessionId) {
            this.publishedMessage = message;
            this.publishedTopic = topicId;
            this.publishedSessionId = sessionId;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class StubAgent extends BaseAgent {
        private final Object result;
        private Object lastInput;

        private StubAgent(AgentCard card, Object result) {
            super(card);
            this.result = result;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            this.lastInput = inputs;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of(result).iterator();
        }

        private Object getLastInput() {
            return lastInput;
        }
    }
}
