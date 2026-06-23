/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.BaseAgent;
import com.openjiuwen.core.single_agent.schema.AgentCard;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Supplemental parity tests for the handoff container wrapper.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.multi_agent.builtin_teams.handoff.test_container_agent} in
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_container_agent.py}.</p>
 */
class ContainerAgentPythonParityTest {

    private static final String SOURCE =
            "tests/unit_tests/multi_agent/builtin_teams/handoff/test_container_agent.py";

    @TestFactory
    Collection<DynamicTest> pythonContainerAgentCases() {
        return pythonNodeIds()
                .map(nodeId -> dynamicTest(nodeId, () -> runPythonCase(nodeId)))
                .toList();
    }

    private static Stream<String> pythonNodeIds() {
        return Stream.of(
                SOURCE + "::TestBuildAgentInput::test_no_history_returns_raw_message",
                SOURCE + "::TestBuildAgentInput::test_no_history_dict_returned_as_is",
                SOURCE + "::TestBuildAgentInput::test_dict_message_with_history_merged",
                SOURCE + "::TestBuildAgentInput::test_string_message_with_history_wrapped",
                SOURCE + "::TestBuildAgentInput::test_history_list_passed_through",
                SOURCE + "::TestStripHandoffMessages::test_tool_messages_removed",
                SOURCE + "::TestStripHandoffMessages::test_assistant_with_tool_calls_removed",
                SOURCE + "::TestStripHandoffMessages::test_assistant_without_tool_calls_kept",
                SOURCE + "::TestStripHandoffMessages::test_user_messages_kept",
                SOURCE + "::TestStripHandoffMessages::test_empty_list_returns_empty",
                SOURCE + "::TestStripHandoffMessages::test_is_static_method",
                SOURCE + "::TestGetTargetAgent::test_provider_called_on_first_access",
                SOURCE + "::TestGetTargetAgent::test_provider_called_only_once",
                SOURCE + "::TestGetTargetAgent::test_same_instance_returned",
                SOURCE + "::TestContainerAgentInvoke::test_returns_empty_for_non_handoff_request",
                SOURCE + "::TestContainerAgentInvoke::test_raises_when_no_coordinator_empty_session",
                SOURCE + "::TestContainerAgentInvoke::test_completes_with_agent_result",
                SOURCE + "::TestContainerAgentInvoke::test_invoke_returns_empty_dict",
                SOURCE + "::TestContainerAgentInvoke::test_error_called_on_agent_exception",
                SOURCE + "::TestContainerAgentStream::test_stream_yields_one_chunk",
                SOURCE + "::TestContainerAgentStream::test_stream_non_request_yields_empty"
        );
    }

    private static void runPythonCase(String nodeId) throws Exception {
        switch (nodeId) {
            case SOURCE + "::TestBuildAgentInput::test_no_history_returns_raw_message" -> noHistoryReturnsRawMessage();
            case SOURCE + "::TestBuildAgentInput::test_no_history_dict_returned_as_is" -> noHistoryDictReturnedAsIs();
            case SOURCE + "::TestBuildAgentInput::test_dict_message_with_history_merged" ->
                    dictMessageWithHistoryMerged();
            case SOURCE + "::TestBuildAgentInput::test_string_message_with_history_wrapped" ->
                    stringMessageWithHistoryWrapped();
            case SOURCE + "::TestBuildAgentInput::test_history_list_passed_through" -> historyListPassedThrough();
            case SOURCE + "::TestStripHandoffMessages::test_tool_messages_removed" -> toolMessagesRemoved();
            case SOURCE + "::TestStripHandoffMessages::test_assistant_with_tool_calls_removed" ->
                    assistantWithToolCallsRemoved();
            case SOURCE + "::TestStripHandoffMessages::test_assistant_without_tool_calls_kept" ->
                    assistantWithoutToolCallsKept();
            case SOURCE + "::TestStripHandoffMessages::test_user_messages_kept" -> userMessagesKept();
            case SOURCE + "::TestStripHandoffMessages::test_empty_list_returns_empty" -> emptyListReturnsEmpty();
            case SOURCE + "::TestStripHandoffMessages::test_is_static_method" -> isStaticMethod();
            case SOURCE + "::TestGetTargetAgent::test_provider_called_on_first_access" -> providerCalledOnFirstAccess();
            case SOURCE + "::TestGetTargetAgent::test_provider_called_only_once" -> providerCalledOnlyOnce();
            case SOURCE + "::TestGetTargetAgent::test_same_instance_returned" -> sameInstanceReturned();
            case SOURCE + "::TestContainerAgentInvoke::test_returns_empty_for_non_handoff_request" ->
                    returnsEmptyForNonHandoffRequest();
            case SOURCE + "::TestContainerAgentInvoke::test_raises_when_no_coordinator_empty_session" ->
                    raisesWhenNoCoordinatorEmptySession();
            case SOURCE + "::TestContainerAgentInvoke::test_completes_with_agent_result" ->
                    completesWithAgentResult();
            case SOURCE + "::TestContainerAgentInvoke::test_invoke_returns_empty_dict" -> invokeReturnsEmptyDict();
            case SOURCE + "::TestContainerAgentInvoke::test_error_called_on_agent_exception" ->
                    errorCalledOnAgentException();
            case SOURCE + "::TestContainerAgentStream::test_stream_yields_one_chunk" -> streamYieldsOneChunk();
            case SOURCE + "::TestContainerAgentStream::test_stream_non_request_yields_empty" ->
                    streamNonRequestYieldsEmpty();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void noHistoryReturnsRawMessage() {
        TestableContainerAgent agent = agentWithResult(Map.of());

        assertThat(agent.build(new HandoffRequest("hello", List.of(), null))).isEqualTo("hello");
    }

    private static void noHistoryDictReturnedAsIs() {
        TestableContainerAgent agent = agentWithResult(Map.of());
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("query", "q");

        assertThat(agent.build(new HandoffRequest(message, List.of(), null))).isSameAs(message);
    }

    private static void dictMessageWithHistoryMerged() {
        TestableContainerAgent agent = agentWithResult(Map.of());
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("query", "q");
        List<Map<String, Object>> history = List.of(Map.of("agent", "a", "output", Map.of()));

        Map<String, Object> result = asMap(agent.build(new HandoffRequest(message, history, null)));

        assertThat(result).containsEntry("query", "q").containsKey("handoff_history");
    }

    private static void stringMessageWithHistoryWrapped() {
        TestableContainerAgent agent = agentWithResult(Map.of());
        List<Map<String, Object>> history = List.of(Map.of("agent", "a", "output", Map.of()));

        Map<String, Object> result = asMap(agent.build(new HandoffRequest("hello", history, null)));

        assertThat(result).containsEntry("query", "hello").containsKey("handoff_history");
    }

    private static void historyListPassedThrough() {
        TestableContainerAgent agent = agentWithResult(Map.of());
        List<Map<String, Object>> history = List.of(
                Map.of("agent", "a", "output", Map.of()),
                Map.of("agent", "b", "output", Map.of())
        );

        Map<String, Object> result = asMap(agent.build(new HandoffRequest("x", history, null)));

        assertThat(result.get("handoff_history")).isEqualTo(history);
    }

    private static void toolMessagesRemoved() {
        List<Object> cleaned = ContainerAgent.stripHandoffMessages(List.of(userMessage(), toolMessage()));

        assertThat(cleaned).allSatisfy(message -> assertThat(asMap(message).get("role")).isNotEqualTo("tool"));
    }

    private static void assistantWithToolCallsRemoved() {
        AssistantMessage assistant = new AssistantMessage("handoff");
        assistant.setToolCallsRaw(List.of(Map.of("id", "call-1", "function", Map.of("name", "handoff"))));

        assertThat(ContainerAgent.stripHandoffMessages(List.of(assistant))).isEmpty();
    }

    private static void assistantWithoutToolCallsKept() {
        AssistantMessage assistant = new AssistantMessage("keep");

        assertThat(ContainerAgent.stripHandoffMessages(List.of(assistant))).hasSize(1);
    }

    private static void userMessagesKept() {
        assertThat(ContainerAgent.stripHandoffMessages(List.of(userMessage()))).hasSize(1);
    }

    private static void emptyListReturnsEmpty() {
        assertThat(ContainerAgent.stripHandoffMessages(List.of())).isEmpty();
    }

    private static void isStaticMethod() throws NoSuchMethodException {
        assertThat(Modifier.isStatic(ContainerAgent.class.getMethod("stripHandoffMessages", List.class).getModifiers()))
                .isTrue();
    }

    private static void providerCalledOnFirstAccess() {
        AtomicInteger calls = new AtomicInteger();
        StubAgent target = new StubAgent(card("a"), Map.of());
        TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> {
            calls.incrementAndGet();
            return target;
        });

        assertThat(agent.target()).isSameAs(target);
        assertThat(calls).hasValue(1);
    }

    private static void providerCalledOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> {
            calls.incrementAndGet();
            return new StubAgent(card("a"), Map.of());
        });

        agent.target();
        agent.target();

        assertThat(calls).hasValue(1);
    }

    private static void sameInstanceReturned() {
        StubAgent target = new StubAgent(card("a"), Map.of());
        TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> target);

        assertThat(agent.target()).isSameAs(agent.target());
    }

    private static void returnsEmptyForNonHandoffRequest() {
        TestableContainerAgent agent = agentWithResult(Map.of());

        Object result = agent.invoke("not a request", null).toCompletableFuture().join();

        assertThat(result).isEqualTo(Map.of());
    }

    private static void raisesWhenNoCoordinatorEmptySession() {
        TestableContainerAgent agent = agentWithResult(Map.of());

        assertThatThrownBy(() -> agent.invoke(new HandoffRequest("hi"), null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("ContainerAgent invoked without a HandoffTeam session");
    }

    private static void completesWithAgentResult() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a"));
        AgentTeamSession session = new AgentTeamSession("sid", null, "team");
        TestableContainerAgent agent = new TestableContainerAgent(
                card("a"),
                () -> new StubAgent(card("a"), Map.of("answer", "done")),
                Set.of(),
                ignored -> coordinator
        );

        agent.invoke(new HandoffRequest("hi", List.of(), session), null).toCompletableFuture().join();

        assertThat(coordinator.doneFuture().join()).isEqualTo(Map.of("answer", "done"));
    }

    private static void invokeReturnsEmptyDict() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a"));
        AgentTeamSession session = new AgentTeamSession("sid", null, "team");
        TestableContainerAgent agent = new TestableContainerAgent(
                card("a"),
                () -> new StubAgent(card("a"), Map.of("ok", true)),
                Set.of(),
                ignored -> coordinator
        );

        Object result = agent.invoke(new HandoffRequest("hi", List.of(), session), null)
                .toCompletableFuture()
                .join();

        assertThat(result).isEqualTo(Map.of());
    }

    private static void errorCalledOnAgentException() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a"));
        AgentTeamSession session = new AgentTeamSession("sid", null, "team");
        TestableContainerAgent agent = new TestableContainerAgent(
                card("a"),
                () -> new StubAgent(card("a"), new IllegalStateException("crash")),
                Set.of(),
                ignored -> coordinator
        );

        Object result = agent.invoke(new HandoffRequest("hi", List.of(), session), null)
                .toCompletableFuture()
                .join();

        assertThat(result).isEqualTo(Map.of());
        assertThatThrownBy(() -> coordinator.doneFuture().join())
                .satisfies(error -> assertThat(unwrap(error)).isInstanceOf(BaseError.class));
    }

    private static void streamYieldsOneChunk() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a"));
        AgentTeamSession session = new AgentTeamSession("sid", null, "team");
        TestableContainerAgent agent = new TestableContainerAgent(
                card("a"),
                () -> new StubAgent(card("a"), Map.of("ok", true)),
                Set.of(),
                ignored -> coordinator
        );

        Iterator<Object> chunks = agent.stream(new HandoffRequest("hi", List.of(), session), null, List.of());

        assertThat(toList(chunks)).hasSize(1);
    }

    private static void streamNonRequestYieldsEmpty() {
        TestableContainerAgent agent = agentWithResult(Map.of());

        assertThat(toList(agent.stream("not a request", null, List.of()))).containsExactly(Map.of());
    }

    private static TestableContainerAgent agentWithResult(Object result) {
        return new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), result));
    }

    private static AgentCard card(String id) {
        return new AgentCard(id, id, "agent " + id);
    }

    private static Map<String, Object> userMessage() {
        return Map.of("role", "user", "content", "keep");
    }

    private static Map<String, Object> toolMessage() {
        return Map.of("role", "tool", "content", "drop");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class TestableContainerAgent extends ContainerAgent {

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
    }

    private static final class StubAgent extends BaseAgent {
        private final Object result;

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
            if (result instanceof RuntimeException runtimeException) {
                return CompletableFuture.failedFuture(runtimeException);
            }
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of(result).iterator();
        }
    }
}
