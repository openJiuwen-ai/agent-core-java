/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's handoff-signal unit coverage in
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_signal.py}.
 */
class HandoffSignalTest {

    @Test
    void extractsDirectAndNestedPayloads() {
        Optional<HandoffSignal> direct = HandoffSignal.extractHandoffSignal(Map.of(
                HandoffSignal.HANDOFF_TARGET_KEY, "agent_b",
                HandoffSignal.HANDOFF_MESSAGE_KEY, "context",
                HandoffSignal.HANDOFF_REASON_KEY, "needs billing"
        ));
        Optional<HandoffSignal> nested = HandoffSignal.extractHandoffSignal(Map.of(
                "output",
                Map.of(HandoffSignal.HANDOFF_TARGET_KEY, "agent_c")
        ));

        assertThat(direct).isPresent();
        assertThat(direct.orElseThrow().getTarget()).isEqualTo("agent_b");
        assertThat(direct.orElseThrow().getMessage()).contains("context");
        assertThat(direct.orElseThrow().getReason()).contains("needs billing");
        assertThat(nested).isPresent();
        assertThat(nested.orElseThrow().getTarget()).isEqualTo("agent_c");
    }

    @Test
    void invalidTargetsReturnEmpty() {
        assertThat(HandoffSignal.extractHandoffSignal(Map.of())).isEmpty();
        assertThat(HandoffSignal.extractHandoffSignal(Map.of(HandoffSignal.HANDOFF_TARGET_KEY, ""))).isEmpty();
        assertThat(HandoffSignal.extractHandoffSignal(Map.of(HandoffSignal.HANDOFF_TARGET_KEY, 123))).isEmpty();
        assertThat(HandoffSignal.extractHandoffSignal("plain string")).isEmpty();
    }

    @Test
    void recoversLatestToolPayloadFromSessionJsonOrPythonDictText() {
        FakeSession jsonSession = new FakeSession(contextWithMessages(List.of(
                message("tool", "{\"__handoff_to__\":\"billing_agent\",\"__handoff_reason__\":\"billing question\"}")
        )));
        FakeSession pythonDictSession = new FakeSession(contextWithMessages(List.of(
                message("tool", "{'__handoff_to__': 'tech_agent', '__handoff_message__': 'escalate'}")
        )));

        assertThat(HandoffSignal.findHandoffFromSession(jsonSession))
                .contains(Map.of(
                        HandoffSignal.HANDOFF_TARGET_KEY, "billing_agent",
                        HandoffSignal.HANDOFF_REASON_KEY, "billing question"
                ));
        assertThat(HandoffSignal.findHandoffFromSession(pythonDictSession))
                .contains(Map.of(
                        HandoffSignal.HANDOFF_TARGET_KEY, "tech_agent",
                        HandoffSignal.HANDOFF_MESSAGE_KEY, "escalate"
                ));
    }

    @Test
    void directResultPayloadTakesPriorityOverRecoveredSessionPayload() {
        FakeSession session = new FakeSession(contextWithMessages(List.of(
                message("tool", "{\"__handoff_to__\":\"session_agent\"}")
        )));

        HandoffSignal signal = HandoffSignal.extractHandoffSignal(
                Map.of(HandoffSignal.HANDOFF_TARGET_KEY, "result_agent"),
                session
        ).orElseThrow();

        assertThat(signal.getTarget()).isEqualTo("result_agent");
    }

    @Test
    void sessionRecoverySkipsNonToolAndMalformedMessages() {
        FakeSession session = new FakeSession(contextWithMessages(List.of(
                message("assistant", "{\"__handoff_to__\":\"assistant_agent\"}"),
                message("tool", "not valid json or python"),
                message("tool", "{\"result_type\":\"answer\"}"),
                message("tool", "{\"__handoff_to__\":\"recovered_agent\"}")
        )));

        HandoffSignal signal = HandoffSignal.extractHandoffSignal(Map.of("output", "plain answer"), session)
                .orElseThrow();

        assertThat(signal.getTarget()).isEqualTo("recovered_agent");
    }

    private static Map<String, Object> contextWithMessages(List<?> messages) {
        return Map.of("default_context_id", Map.of("messages", messages));
    }

    private static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    /**
     * Mirrors Python's fake agent session used by handoff-signal tests in
     * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_signal.py}.
     */
    private static final class FakeSession {
        private final Object contextState;

        private FakeSession(Object contextState) {
            this.contextState = contextState;
        }

        public Object getState(String key) {
            return "context".equals(key) ? contextState : null;
        }
    }
}
