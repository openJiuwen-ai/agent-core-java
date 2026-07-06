/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for reactive single-agent APIs backed by a real remote model.
 */
@Tag("system-test")
class SingleAgentReactiveSystemTest extends SystemTestSupport {

    @Test
    @DisplayName("ReActAgent.invokeAsync invokes remote model")
    void testReActAgentInvokeAsyncWithRemoteModel() {
        assumeRemoteModelAvailable();

        String sessionId = trackSessionId("reactive-react-invoke-session");
        ReActAgent agent = newRemoteReActAgent(
                uniqueId("reactive-react-agent"),
                "Reply briefly in English. If the user asks for an exact token, return that token."
        );

        AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard());
        session.preRun(Map.of("query", "Reply with the exact token REACTIVE_INVOKE_OK.", "conversation_id", sessionId));

        StepVerifier.create(agent.invokeAsync(
                        Map.of("query", "Reply with the exact token REACTIVE_INVOKE_OK.",
                                "conversation_id", sessionId),
                        session))
                .assertNext(result -> assertTrue(
                        containsIgnoreCase(flattenText(result), "REACTIVE_INVOKE_OK"),
                        () -> "Expected REACTIVE_INVOKE_OK in output but got: " + flattenText(result)))
                .expectComplete()
                .verify(Duration.ofSeconds(120));

        session.postRun();
    }

    @Test
    @DisplayName("ReActAgent.streamAsync streams remote model output")
    void testReActAgentStreamAsyncWithRemoteModel() {
        assumeRemoteModelAvailable();

        String sessionId = trackSessionId("reactive-react-stream-session");
        ReActAgent agent = newRemoteReActAgent(
                uniqueId("reactive-react-stream-agent"),
                "Reply briefly in English. If the user asks for an exact token, return that token."
        );

        AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard(), List.of(StreamMode.OUTPUT));
        Map<String, Object> inputs = Map.of(
                "query", "Reply with the exact token REACTIVE_STREAM_OK.",
                "conversation_id", sessionId);
        session.preRun(inputs);

        StepVerifier.create(agent.streamAsync(inputs, session, List.of(StreamMode.OUTPUT))
                        .collectList())
                .assertNext(items -> assertTrue(
                        containsIgnoreCase(flattenText(items), "REACTIVE_STREAM_OK"),
                        () -> "Expected REACTIVE_STREAM_OK in stream but got: " + flattenText(items)))
                .expectComplete()
                .verify(Duration.ofSeconds(120));

        session.postRun();
    }

    @Test
    @DisplayName("Runner reactive APIs execute registered ReActAgent")
    void testRunnerReactiveApisWithRegisteredReActAgent() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("runner-reactive-react-agent");
        ReActAgent agent = newRemoteReActAgent(
                agentId,
                "Reply briefly in English. If the user asks for an exact token, return that token."
        );
        registerAgent(agent);

        String invokeSessionId = trackSessionId("runner-reactive-invoke-session");
        StepVerifier.create(Runner.runAgentAsync(
                        agentId,
                        Map.of("query", "Reply with the exact token RUNNER_REACTIVE_INVOKE_OK.",
                                "conversation_id", invokeSessionId),
                        null,
                        null,
                        null))
                .assertNext(result -> assertTrue(
                        containsIgnoreCase(flattenText(result), "RUNNER_REACTIVE_INVOKE_OK"),
                        () -> "Expected RUNNER_REACTIVE_INVOKE_OK in output but got: " + flattenText(result)))
                .expectComplete()
                .verify(Duration.ofSeconds(120));

        String streamSessionId = trackSessionId("runner-reactive-stream-session");
        StepVerifier.create(Runner.runAgentStreamingAsync(
                                agentId,
                                Map.of("query", "Reply with the exact token RUNNER_REACTIVE_STREAM_OK.",
                                        "conversation_id", streamSessionId),
                                null,
                                null,
                                List.of(StreamMode.OUTPUT),
                                null)
                        .collectList())
                .assertNext(items -> assertTrue(
                        containsIgnoreCase(flattenText(items), "RUNNER_REACTIVE_STREAM_OK"),
                        () -> "Expected RUNNER_REACTIVE_STREAM_OK in stream but got: " + flattenText(items)))
                .expectComplete()
                .verify(Duration.ofSeconds(120));
    }
}
