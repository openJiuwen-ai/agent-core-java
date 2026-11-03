/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.session.AgentTeamSession;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the handoff request drive message.
 *
 * <p>Mirrors Python's {@code HandoffRequest} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_request.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.multi_agent.builtin_teams.handoff.test_handoff_request} in
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_request.py}.</p>
 */
class HandoffRequestTest {

    @Test
    void inputMessageStored() {
        HandoffRequest request = new HandoffRequest("hello");

        assertThat(request.getInputMessage()).isEqualTo("hello");
    }

    @Test
    void dictInputMessageStored() {
        Map<String, Object> message = Map.of("query", "what is 2+2");

        HandoffRequest request = new HandoffRequest(message);

        assertThat(request.getInputMessage()).isSameAs(message);
    }

    @Test
    void defaultHistoryIsEmptyList() {
        HandoffRequest request = new HandoffRequest("x");

        assertThat(request.getHistory()).isEmpty();
    }

    @Test
    void defaultSessionIsNone() {
        HandoffRequest request = new HandoffRequest("x");

        assertThat(request.getSession()).isNull();
    }

    @Test
    void customHistoryStored() {
        Map<String, Object> historyItem = new LinkedHashMap<>();
        historyItem.put("agent", "a");
        historyItem.put("output", Map.of("ok", true));
        List<Map<String, Object>> history = List.of(historyItem);

        HandoffRequest request = new HandoffRequest("x", history, null);

        assertThat(request.getHistory()).containsExactly(historyItem);
    }

    @Test
    void customSessionStored() {
        AgentTeamSession session = new AgentTeamSession("session-1", Map.of("env", "test"), "team-1");
        HandoffRequest request = new HandoffRequest("x", null, session);

        assertThat(request.getSession()).isSameAs(session);
    }

    @Test
    void sessionIdEmptyStringWhenNoSession() {
        HandoffRequest request = new HandoffRequest("hello");

        assertThat(request.getSessionId()).isEmpty();
    }

    @Test
    void sessionIdFromSession() {
        AgentTeamSession session = new AgentTeamSession("sid-123", Map.of(), "team-1");
        HandoffRequest request = new HandoffRequest("hi", null, session);

        assertThat(request.getSessionId()).isEqualTo("sid-123");
    }

    @Test
    void sessionIdCallsGetSessionIdOnce() {
        CountingSession session = new CountingSession("abc");
        HandoffRequest request = new HandoffRequest("hi", null, session);

        assertThat(request.getSessionId()).isEqualTo("abc");

        assertThat(session.getSessionIdCalls()).isEqualTo(1);
    }

    @Test
    void sessionIdIsStringType() {
        HandoffRequest request = new HandoffRequest("x");

        assertThat(request.getSessionId()).isInstanceOf(String.class);
    }

    @Test
    void sessionIdChangesWithSession() {
        AgentTeamSession session = new AgentTeamSession("new-id", Map.of(), "team-1");
        HandoffRequest request = new HandoffRequest("x", null, session);

        assertThat(request.getSessionId()).isEqualTo("new-id");
    }

    @Test
    void defaultHistoryNotSharedAcrossInstances() {
        HandoffRequest first = new HandoffRequest("first");
        HandoffRequest second = new HandoffRequest("second");

        first.getHistory().add(Map.of("agent", "x", "output", Map.of()));

        assertThat(second.getHistory()).isEmpty();
    }

    @Test
    void historyMutability() {
        HandoffRequest request = new HandoffRequest("x");

        request.getHistory().add(Map.of("agent", "a", "output", Map.of()));

        assertThat(request.getHistory()).hasSize(1);
    }

    @Test
    void historyLengthMatchesSupplied() {
        List<Map<String, Object>> history = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> Map.<String, Object>of("agent", "agent_" + index, "output", Map.of()))
                .toList();

        HandoffRequest request = new HandoffRequest("x", history, null);

        assertThat(request.getHistory()).hasSize(5);
    }

    private static final class CountingSession extends AgentTeamSession {
        private int sessionIdCalls;

        private CountingSession(String sessionId) {
            super(sessionId, Map.of(), "team-1");
        }

        @Override
        public String getSessionId() {
            sessionIdCalls++;
            return super.getSessionId();
        }

        private int getSessionIdCalls() {
            return sessionIdCalls;
        }
    }
}
