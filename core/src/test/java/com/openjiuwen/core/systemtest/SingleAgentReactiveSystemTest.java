/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for reactive single-agent APIs backed by a real remote model.
 */
@Tag("system-test")
class SingleAgentReactiveSystemTest extends SystemTestSupport {

    @Test
    @DisplayName("ReActAgent.invoke invokes remote model")
    void testReActAgentInvokeAsyncWithRemoteModel() {
        assumeRemoteModelAvailable();

        String sessionId = trackSessionId("reactive-react-invoke-session");
        ReActAgent agent = newRemoteReActAgent(
                uniqueId("reactive-react-agent"),
                "Reply briefly in English. If the user asks for an exact token, return that token."
        );

        AgentSession session = AgentSession.createAgentSession(sessionId, null, agent.getCard());
        session.preRun(Map.of("query", "Reply with the exact token REACTIVE_INVOKE_OK.", "conversation_id", sessionId));

        Object result = agent.invoke(
                Map.of("query", "Reply with the exact token REACTIVE_INVOKE_OK.",
                        "conversation_id", sessionId),
                session);

        assertTrue(
                containsIgnoreCase(flattenText(result), "REACTIVE_INVOKE_OK"),
                () -> "Expected REACTIVE_INVOKE_OK in output but got: " + flattenText(result));

        session.postRun();
    }

    @Test
    @DisplayName("ReActAgent.stream streams remote model output")
    void testReActAgentStreamAsyncWithRemoteModel() {
        assumeRemoteModelAvailable();

        String sessionId = trackSessionId("reactive-react-stream-session");
        ReActAgent agent = newRemoteReActAgent(
                uniqueId("reactive-react-stream-agent"),
                "Reply briefly in English. If the user asks for an exact token, return that token."
        );

        AgentSession session = AgentSession.createAgentSession(sessionId, null, agent.getCard());
        Map<String, Object> inputs = Map.of(
                "query", "Reply with the exact token REACTIVE_STREAM_OK.",
                "conversation_id", sessionId);
        session.preRun(inputs);

        Iterator<Object> streamIterator = agent.stream(inputs, session, List.of(StreamMode.OUTPUT));
        List<Object> items = collect(streamIterator);
        assertTrue(
                containsIgnoreCase(flattenText(items), "REACTIVE_STREAM_OK"),
                () -> "Expected REACTIVE_STREAM_OK in stream but got: " + flattenText(items));

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
        Object invokeResult = Runner.runAgent(
                        agentId,
                        Map.of("query", "Reply with the exact token RUNNER_REACTIVE_INVOKE_OK.",
                                "conversation_id", invokeSessionId),
                        null,
                        null,
                        null)
                .toCompletableFuture()
                .join();
        assertTrue(
                containsIgnoreCase(flattenText(invokeResult), "RUNNER_REACTIVE_INVOKE_OK"),
                () -> "Expected RUNNER_REACTIVE_INVOKE_OK in output but got: " + flattenText(invokeResult));

        String streamSessionId = trackSessionId("runner-reactive-stream-session");
        Iterator<Object> streamResult = Runner.runAgentStreaming(
                                agentId,
                                Map.of("query", "Reply with the exact token RUNNER_REACTIVE_STREAM_OK.",
                                        "conversation_id", streamSessionId),
                                null,
                                null,
                                List.of(StreamMode.OUTPUT),
                                null)
                        .toCompletableFuture()
                        .join();
        List<Object> items = collect(streamResult);
        assertTrue(
                containsIgnoreCase(flattenText(items), "RUNNER_REACTIVE_STREAM_OK"),
                () -> "Expected RUNNER_REACTIVE_STREAM_OK in stream but got: " + flattenText(items));
    }
}
