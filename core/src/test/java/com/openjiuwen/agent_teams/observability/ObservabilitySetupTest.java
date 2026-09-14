/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.callback.AgentEvents;
import com.openjiuwen.core.runner.callback.LLMCallEvents;
import com.openjiuwen.core.runner.callback.ToolCallEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilitySetupTest {

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void initObservabilityRegistersCallbacksAndShutdownUnregistersThem() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();

        ObservabilitySetup.initObservability(new ObservabilityConfig(), tracer);

        assertThat(ObservabilitySetup.getCallbackHandler()).isNotNull();
        assertThat(ObservabilitySetup.getMonitorHandler()).isNotNull();
        assertThat(ObservabilitySetup.registeredEvents()).containsExactlyInAnyOrder(
                LLMCallEvents.LLM_INVOKE_INPUT,
                LLMCallEvents.LLM_STREAM_INPUT,
                LLMCallEvents.LLM_STREAM_OUTPUT,
                LLMCallEvents.LLM_INVOKE_OUTPUT,
                LLMCallEvents.LLM_CALL_ERROR,
                ToolCallEvents.TOOL_CALL_STARTED,
                ToolCallEvents.TOOL_CALL_FINISHED,
                ToolCallEvents.TOOL_CALL_ERROR,
                AgentEvents.AGENT_INVOKE_INPUT,
                AgentEvents.AGENT_INVOKE_OUTPUT
        );
        assertThat(Runner.getCallbackFramework().listEvents(ObservabilitySetup.NAMESPACE))
                .contains(LLMCallEvents.LLM_INVOKE_INPUT, LLMCallEvents.LLM_INVOKE_OUTPUT);

        Runner.getCallbackFramework().triggerResults(
                LLMCallEvents.LLM_INVOKE_INPUT,
                Map.of(
                        "messages", List.of(Map.of("role", "user", "content", "hello")),
                        "model", "fake-llm-1"
                )
        );
        Runner.getCallbackFramework().triggerResults(
                LLMCallEvents.LLM_INVOKE_OUTPUT,
                Map.of("result", new FakeAssistantMessage("hello back", "stop"))
        );

        TelemetrySpan llmSpan = findSpan(tracer, "llm.call");
        assertThat(llmSpan.getAttributes())
                .containsEntry(ObservabilitySemconv.GEN_AI_REQUEST_MODEL, "fake-llm-1")
                .containsEntry(ObservabilitySemconv.GEN_AI_COMPLETION + ".0.content", "hello back");
        assertThat(llmSpan.isEnded()).isTrue();

        ObservabilitySetup.shutdownObservability();

        assertThat(ObservabilitySetup.registeredEvents()).isEmpty();
        assertThat(Runner.getCallbackFramework().listEvents(ObservabilitySetup.NAMESPACE)).isEmpty();
    }

    @Test
    void disabledConfigRegistersNoCallbacksAndExportsNothing() {
        ObservabilityConfig config = new ObservabilityConfig();
        config.setEnabled(false);
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();

        ObservabilitySetup.initObservability(config, tracer);
        Runner.getCallbackFramework().triggerResults(
                LLMCallEvents.LLM_INVOKE_INPUT,
                Map.of(
                        "messages", List.of(Map.of("role", "user", "content", "hi")),
                        "model", "fake-llm-1"
                )
        );
        Runner.getCallbackFramework().triggerResults(
                LLMCallEvents.LLM_INVOKE_OUTPUT,
                Map.of("result", new FakeAssistantMessage("hello", "stop"))
        );

        assertThat(ObservabilitySetup.registeredEvents()).isEmpty();
        assertThat(ObservabilitySetup.getCallbackHandler()).isNull();
        assertThat(tracer.getSpans()).isEmpty();
    }

    @Test
    void attachAndDetachTeamAgentManageMonitorListenerIdempotently() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        ObservabilitySetup.initObservability(new ObservabilityConfig(), tracer);
        TeamAgent teamAgent = new TeamAgent(new AgentCard("leader", "Leader", "test leader"));

        ObservabilitySetup.attachToTeamAgent(teamAgent);
        ObservabilitySetup.attachToTeamAgent(teamAgent);

        assertThat(teamAgent.getEventListeners())
                .containsExactly(ObservabilitySetup.getMonitorHandler());

        ObservabilitySetup.detachFromTeamAgent(teamAgent);

        assertThat(teamAgent.getEventListeners()).isEmpty();
    }

    private static void cleanup() {
        ObservabilitySetup.shutdownObservability();
        Runner.getCallbackFramework().unregisterNamespace(ObservabilitySetup.NAMESPACE);
        SpanContext.resetAll();
    }

    private static TelemetrySpan findSpan(TelemetryTracer.InMemory tracer, String name) {
        return tracer.getSpans().stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow();
    }

    private record FakeUsage(long inputTokens, long outputTokens, long totalTokens, String modelName) {
        private FakeUsage() {
            this(1, 1, 2, "fake-llm-1");
        }
    }

    private record FakeAssistantMessage(
            String content,
            String finishReason,
            FakeUsage usageMetadata
    ) {
        private FakeAssistantMessage(String content, String finishReason) {
            this(content, finishReason, new FakeUsage());
        }
    }
}
