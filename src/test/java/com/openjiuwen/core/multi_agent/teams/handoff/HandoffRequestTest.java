/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

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
 */
class HandoffRequestTest {

    @Test
    void usesPythonDefaultsWhenOnlyInputMessageIsProvided() {
        HandoffRequest request = new HandoffRequest("hello");

        assertThat(request.getInputMessage()).isEqualTo("hello");
        assertThat(request.getHistory()).isEmpty();
        assertThat(request.getSession()).isNull();
        assertThat(request.getSessionId()).isEmpty();
    }

    @Test
    void sessionIdComesFromAttachedTeamSession() {
        AgentTeamSession session = new AgentTeamSession("session-1", Map.of("env", "test"), "team-1");
        HandoffRequest request = new HandoffRequest(Map.of("message", "next"), null, session);

        assertThat(request.getSessionId()).isEqualTo("session-1");
        assertThat(request.getSession()).isSameAs(session);
    }

    @Test
    void historyUsesIndependentMutableListPerRequest() {
        HandoffRequest first = new HandoffRequest("first");
        HandoffRequest second = new HandoffRequest("second");

        first.addHistory(Map.of("source", "agent_a", "target", "agent_b"));

        assertThat(first.getHistory()).containsExactly(Map.of("source", "agent_a", "target", "agent_b"));
        assertThat(second.getHistory()).isEmpty();
    }

    @Test
    void copiesProvidedHistoryMaps() {
        HandoffRequest request = new HandoffRequest();
        Map<String, Object> historyItem = new LinkedHashMap<>();
        historyItem.put("hop", 1);
        request.setHistory(List.of(historyItem));

        assertThat(request.getHistory()).containsExactly(Map.of("hop", 1));
    }
}
