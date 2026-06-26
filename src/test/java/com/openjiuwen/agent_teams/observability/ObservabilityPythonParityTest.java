/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.openjiuwen.agent_teams.schema.BroadcastEvent;
import com.openjiuwen.agent_teams.schema.MemberSpawnedEvent;
import com.openjiuwen.agent_teams.schema.MemberStatusChangedEvent;
import com.openjiuwen.agent_teams.schema.MessageEvent;
import com.openjiuwen.agent_teams.schema.TaskCompletedEvent;
import com.openjiuwen.agent_teams.schema.TaskCreatedEvent;
import com.openjiuwen.agent_teams.schema.TeamCleanedEvent;
import com.openjiuwen.agent_teams.schema.TeamCreatedEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.callback.LLMCallEvents;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.TaskIterationInputs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Missing-test parity coverage for the agent-team observability subsystem.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/observability/test_observability.py}.</p>
 */
class ObservabilityPythonParityTest {

    @AfterEach
    void tearDown() {
        ObservabilitySetup.shutdownObservability();
        Runner.getCallbackFramework().unregisterNamespace(ObservabilitySetup.NAMESPACE);
        SpanContext.resetAll();
    }

    @Test
    void streamingLlmCallRecordsTtftAndReasoning() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelCallbackHandler handler = new OtelCallbackHandler(new ObservabilityConfig(), tracer);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", "You are a friendly helper."),
                Map.of("role", "user", "content", "Compute 6 * 7.")
        );

        handler.onLlmStreamInput(Map.of(
                "messages", messages,
                "temperature", 0.5d,
                "top_p", 0.9d,
                "max_tokens", 512,
                "model", "fake-llm-1"
        ));
        handler.onLlmStreamOutput(Map.of("result", new FakeChunk("4")));
        handler.onLlmStreamOutput(Map.of("result", new FakeChunk("2")));
        handler.onLlmInvokeOutput(Map.of("result", new FakeAssistantMessage(
                "42",
                "Six times seven equals forty-two.",
                "stop"
        )));

        TelemetrySpan llmSpan = findSpan(tracer, "llm.call");
        assertEquals("openjiuwen", llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_SYSTEM));
        assertEquals("fake-llm-1", llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_REQUEST_MODEL));
        assertEquals(0.5d, llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_REQUEST_TEMPERATURE));
        assertEquals(12L, llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_USAGE_PROMPT_TOKENS));
        assertEquals(7L, llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_USAGE_COMPLETION_TOKENS));
        assertEquals(19L, llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_USAGE_TOTAL_TOKENS));
        assertTrue(((Number) llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_RESPONSE_TTFT_MS))
                .doubleValue() >= 0.0d);
        assertTrue(String.valueOf(llmSpan.getAttributes().get("gen_ai.prompt.1.content"))
                .contains("Compute 6 * 7"));
        assertEquals("42", llmSpan.getAttributes().get("gen_ai.completion.0.content"));
        assertEquals("stop", llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_RESPONSE_FINISH_REASON));

        TelemetrySpan reasoningSpan = findSpan(tracer, "llm.reasoning");
        assertEquals(Boolean.TRUE, reasoningSpan.getAttributes().get("gen_ai.completion.0.is_reasoning"));
        assertTrue(String.valueOf(reasoningSpan.getAttributes().get("gen_ai.completion.0.content"))
                .contains("forty-two"));
        assertSame(llmSpan, reasoningSpan.getParent());
    }

    @Test
    void toolCallNestsUnderLlmSpan() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelCallbackHandler handler = new OtelCallbackHandler(new ObservabilityConfig(), tracer);
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", "Use the calc tool."));

        handler.onLlmInvokeInput(Map.of("messages", messages, "model", "fake-llm-1"));
        handler.onToolCallStarted(Map.of(
                "tool_name", "calc",
                "tool_id", "calc-1",
                "inputs", Map.of("expr", "6*7")
        ));
        handler.onToolCallFinished(Map.of("tool_name", "calc", "tool_id", "calc-1", "result", 42));
        handler.onLlmInvokeOutput(Map.of("result", new FakeAssistantMessage("42")));

        TelemetrySpan llmSpan = findSpan(tracer, "llm.call");
        TelemetrySpan toolSpan = findSpan(tracer, "tool.calc");
        assertEquals("calc", toolSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_TOOL_NAME));
        assertTrue(String.valueOf(toolSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_TOOL_INPUT))
                .contains("6*7"));
        assertEquals("42", toolSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_TOOL_OUTPUT));
        assertSame(llmSpan, toolSpan.getParent());
    }

    @Test
    void llmCallErrorMarksSpanError() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelCallbackHandler handler = new OtelCallbackHandler(new ObservabilityConfig(), tracer);
        RuntimeException error = new RuntimeException("provider down");

        handler.onLlmInvokeInput(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "fail please")),
                "model", "fake-llm-1"
        ));
        handler.onLlmCallError(Map.of("error", error));

        TelemetrySpan span = findSpan(tracer, "llm.call");
        assertEquals(TelemetrySpan.StatusCode.ERROR, span.getStatusCode());
        assertTrue(span.getStatusDescription().contains("provider down"));
        assertTrue(span.getExceptions().contains(error));
    }

    @Test
    void teamMonitorHandlerEmitsTeamAndTaskSpans() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(new ObservabilityConfig(), tracer);

        handler.onEvent(EventMessage.fromEvent(teamCreated())).toCompletableFuture().join();
        handler.onEvent(EventMessage.fromEvent(memberSpawned())).toCompletableFuture().join();
        handler.onEvent(EventMessage.fromEvent(memberStatusChanged())).toCompletableFuture().join();
        handler.onEvent(EventMessage.fromEvent(messageEvent())).toCompletableFuture().join();
        handler.onEvent(EventMessage.fromEvent(broadcastEvent())).toCompletableFuture().join();
        handler.onEvent(EventMessage.fromEvent(taskCreated())).toCompletableFuture().join();
        handler.onEvent(EventMessage.fromEvent(taskCompleted())).toCompletableFuture().join();
        handler.onEvent(EventMessage.fromEvent(teamCleaned())).toCompletableFuture().join();

        TelemetrySpan teamSpan = findSpan(tracer, "team.alpha");
        assertEquals("alpha", teamSpan.getAttributes().get(ObservabilitySemconv.AT_TEAM_NAME));
        assertEquals("Alpha Team", teamSpan.getAttributes().get(ObservabilitySemconv.AT_TEAM_DISPLAY_NAME));
        List<String> eventNames = teamSpan.getEvents().stream().map(TelemetrySpan.Event::name).toList();
        assertTrue(eventNames.contains("member_spawned"));
        assertTrue(eventNames.contains("member_status_changed"));
        assertTrue(eventNames.contains("message"));
        assertTrue(eventNames.contains("broadcast"));

        TelemetrySpan taskSpan = findSpan(tracer, "task.t1");
        assertEquals("completed", taskSpan.getAttributes().get(ObservabilitySemconv.AT_TASK_STATUS));
        assertTrue(taskSpan.isEnded());
    }

    @Test
    void observabilityRailOpensAndClosesIterationSpan() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        ObservabilityRail rail = new ObservabilityRail(tracer);
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setIteration(3);
        inputs.setFollowUp(true);
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);

        rail.beforeTaskIteration(context).toCompletableFuture().join();
        rail.afterTaskIteration(context).toCompletableFuture().join();

        TelemetrySpan span = findSpan(tracer, "deepagent.task_iteration.3");
        assertEquals(3, span.getAttributes().get(ObservabilitySemconv.DA_TASK_ITERATION));
        assertEquals(Boolean.TRUE, span.getAttributes().get(ObservabilitySemconv.DA_TASK_IS_FOLLOW_UP));
        assertTrue(span.isEnded());
    }

    @Test
    void observabilityRailMarksErrorOnException() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        ObservabilityRail rail = new ObservabilityRail(tracer);
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setIteration(1);
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);
        context.setException(new ValueError("kaboom"));

        rail.beforeTaskIteration(context).toCompletableFuture().join();
        rail.afterTaskIteration(context).toCompletableFuture().join();

        TelemetrySpan span = findSpan(tracer, "deepagent.task_iteration.1");
        assertEquals(TelemetrySpan.StatusCode.ERROR, span.getStatusCode());
    }

    @Test
    void disabledConfigRegistersNoCallbacksAndExportsNothing() {
        ObservabilityConfig config = new ObservabilityConfig();
        config.setEnabled(false);
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();

        ObservabilitySetup.initObservability(config, tracer);
        Runner.getCallbackFramework().triggerResults(
                LLMCallEvents.LLM_INVOKE_INPUT,
                Map.of("messages", List.of(Map.of("role", "user", "content", "hi")), "model", "fake-llm-1")
        );
        Runner.getCallbackFramework().triggerResults(
                LLMCallEvents.LLM_INVOKE_OUTPUT,
                Map.of("result", new FakeAssistantMessage("hello"))
        );

        assertTrue(ObservabilitySetup.registeredEvents().isEmpty());
        assertTrue(tracer.getSpans().isEmpty());
    }

    @Test
    void redactionReplacesPromptAndCompletionText() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        ObservabilityConfig config = new ObservabilityConfig();
        config.setRedactPrompts(true);
        config.setRedactCompletions(true);
        OtelCallbackHandler handler = new OtelCallbackHandler(config, tracer);

        handler.onLlmInvokeInput(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "secret prompt")),
                "model", "fake-llm-1"
        ));
        handler.onLlmInvokeOutput(Map.of("result", new FakeAssistantMessage("secret answer")));

        TelemetrySpan span = findSpan(tracer, "llm.call");
        String prompt = String.valueOf(span.getAttributes().get("gen_ai.prompt.0.content"));
        String completion = String.valueOf(span.getAttributes().get("gen_ai.completion.0.content"));
        assertTrue(prompt.startsWith("sha256:"));
        assertFalse(prompt.contains("secret"));
        assertTrue(completion.startsWith("sha256:"));
        assertFalse(completion.contains("secret"));
    }

    @Test
    void agentInvokeEmitsNamedSpan() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelCallbackHandler handler = new OtelCallbackHandler(new ObservabilityConfig(), tracer);

        handler.onAgentInvokeInput(Map.of("inputs", Map.of("agent_id", "leader", "user_input", "hello")));
        handler.onAgentInvokeOutput(Map.of(
                "inputs", Map.of("agent_id", "leader", "user_input", "hello"),
                "result", "acknowledged"
        ));

        TelemetrySpan span = findSpan(tracer, "agent.leader");
        assertEquals("leader", span.getAttributes().get(ObservabilitySemconv.AT_AGENT_ID));
        assertEquals("hello", span.getAttributes().get(ObservabilitySemconv.AT_AGENT_INPUT));
        assertEquals("acknowledged", span.getAttributes().get(ObservabilitySemconv.AT_AGENT_OUTPUT));
        assertTrue(span.isEnded());
    }

    private static TelemetrySpan findSpan(TelemetryTracer.InMemory tracer, String name) {
        return tracer.getSpans().stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static TeamCreatedEvent teamCreated() {
        TeamCreatedEvent event = new TeamCreatedEvent();
        event.setTeamName("alpha");
        event.setDisplayName("Alpha Team");
        event.setLeaderMemberName("leader");
        event.setCreated(1700000000);
        return event;
    }

    private static MemberSpawnedEvent memberSpawned() {
        MemberSpawnedEvent event = new MemberSpawnedEvent();
        event.setTeamName("alpha");
        event.setMemberName("alice");
        return event;
    }

    private static MemberStatusChangedEvent memberStatusChanged() {
        MemberStatusChangedEvent event = new MemberStatusChangedEvent();
        event.setTeamName("alpha");
        event.setMemberName("alice");
        event.setOldStatus("UNSTARTED");
        event.setNewStatus("READY");
        return event;
    }

    private static MessageEvent messageEvent() {
        MessageEvent event = new MessageEvent();
        event.setTeamName("alpha");
        event.setMessageId("m1");
        event.setFromMemberName("leader");
        event.setToMemberName("alice");
        return event;
    }

    private static BroadcastEvent broadcastEvent() {
        BroadcastEvent event = new BroadcastEvent();
        event.setTeamName("alpha");
        event.setMessageId("m2");
        event.setFromMemberName("leader");
        return event;
    }

    private static TaskCreatedEvent taskCreated() {
        TaskCreatedEvent event = new TaskCreatedEvent();
        event.setTeamName("alpha");
        event.setTaskId("t1");
        event.setStatus("open");
        return event;
    }

    private static TaskCompletedEvent taskCompleted() {
        TaskCompletedEvent event = new TaskCompletedEvent();
        event.setTeamName("alpha");
        event.setTaskId("t1");
        return event;
    }

    private static TeamCleanedEvent teamCleaned() {
        TeamCleanedEvent event = new TeamCleanedEvent();
        event.setTeamName("alpha");
        return event;
    }

    private record FakeUsage(long inputTokens, long outputTokens, long totalTokens, String modelName) {
        private FakeUsage() {
            this(12L, 7L, 19L, "fake-llm-1");
        }
    }

    private record FakeAssistantMessage(
            String content,
            String reasoningContent,
            String finishReason,
            FakeUsage usageMetadata
    ) {
        private FakeAssistantMessage(String content) {
            this(content, "", "stop", new FakeUsage());
        }

        private FakeAssistantMessage(String content, String reasoningContent, String finishReason) {
            this(content, reasoningContent, finishReason, new FakeUsage());
        }
    }

    private record FakeChunk(String content) {
    }

    private static final class ValueError extends RuntimeException {
        private ValueError(String message) {
            super(message);
        }
    }
}
