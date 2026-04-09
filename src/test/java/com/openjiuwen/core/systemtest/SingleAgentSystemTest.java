/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for the singleagent module.
 */
@Tag("system-test")
class SingleAgentSystemTest extends SystemTestSupport {

    @Test
    @DisplayName("ReActAgent invokes remote model and fires callback events")
    void testReActAgentInvokeAndCallbacks() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("react-agent");
        String sessionId = trackSessionId("react-session");
        ReActAgent agent = newRemoteReActAgent(
                agentId,
                "Reply briefly in English. If the user asks for an exact token, return that token."
        );

        AtomicBoolean beforeInvokeTriggered = new AtomicBoolean(false);
        AtomicBoolean afterInvokeTriggered = new AtomicBoolean(false);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> beforeInvokeTriggered.set(true), 10);
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE, ctx -> afterInvokeTriggered.set(true), 10);

        InvocationCapture capture = invokeAgent(
                agent,
                Map.of(
                        "query", "Reply with the exact token ASTRA_ACK.",
                        "conversation_id", sessionId
                ),
                sessionId
        );

        assertTrue(beforeInvokeTriggered.get(), "BEFORE_INVOKE callback should fire");
        assertTrue(afterInvokeTriggered.get(), "AFTER_INVOKE callback should fire");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) capture.result();
        assertEquals("answer", result.get("result_type"));
        assertTrue(containsIgnoreCase(capture.flattenedText(), "ASTRA_ACK"),
                () -> "Expected ASTRA_ACK in output but got: " + capture.flattenedText());
    }

    @Test
    @DisplayName("ReActAgent preserves conversation across repeated sessions")
    void testReActAgentConversationPersistence() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("react-memory-agent");
        String sessionId = trackSessionId("react-memory-session");
        ReActAgent agent = newRemoteReActAgent(
                agentId,
                "When asked to remember a secret word, acknowledge with ACK_ONLY. "
                        + "When later asked what the secret word is, return only that word."
        );

        InvocationCapture firstTurn = invokeAgent(
                agent,
                Map.of(
                        "query", "Remember the secret word KITE_CODE.",
                        "conversation_id", sessionId
                ),
                sessionId
        );
        InvocationCapture secondTurn = invokeAgent(
                agent,
                Map.of(
                        "query", "What is the secret word? Reply with the exact word only.",
                        "conversation_id", sessionId
                ),
                sessionId
        );

        assertTrue(containsIgnoreCase(firstTurn.flattenedText(), "ACK_ONLY"),
                () -> "Expected ACK_ONLY in first turn but got: " + firstTurn.flattenedText());
        assertTrue(containsIgnoreCase(secondTurn.flattenedText(), "KITE_CODE"),
                () -> "Expected KITE_CODE in second turn but got: " + secondTurn.flattenedText());
    }
}
