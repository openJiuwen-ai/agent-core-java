/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamAgentState}.
 *
 * <p>Mirrors Python's {@code TeamAgentState} in
 * {@code openjiuwen/agent_teams/agent/state.py}.</p>
 */
class TeamAgentStateTest {

    @Test
    void defaultsMirrorDataclassDefaults() {
        TeamAgentState state = new TeamAgentState();

        assertThat(state.getTeamSession()).isNull();
        assertThat(state.getTeamMember()).isNull();
        assertThat(state.getPendingUserQuery()).isEmpty();
        assertThat(state.getEventListeners()).isEmpty();
        assertThat(state.isTeamCleaned()).isFalse();
    }

    @Test
    void eventListenerDefaultFactoryReturnsIndependentMutableLists() {
        List<Object> first = TeamAgentState.emptyListenerList();
        List<Object> second = TeamAgentState.emptyListenerList();

        first.add("listener");

        assertThat(first).containsExactly("listener");
        assertThat(second).isEmpty();
    }

    @Test
    void statePreservesSharedRuntimeValues() {
        TeamAgentState state = new TeamAgentState();
        RecordingSession session = new RecordingSession("session-1");
        TeamMember member = new TeamMember(
                "dev",
                "team",
                new AgentCard("card", "card", "desc"),
                null,
                null
        );

        state.setTeamSession(session);
        state.setTeamMember(member);
        state.setPendingUserQuery("hello");
        state.setEventListeners(List.of("listener"));
        state.setTeamCleaned(true);

        assertThat(state.getTeamSession()).isSameAs(session);
        assertThat(state.getTeamMember()).isSameAs(member);
        assertThat(state.getPendingUserQuery()).isEqualTo("hello");
        assertThat(state.getEventListeners()).containsExactly("listener");
        assertThat(state.isTeamCleaned()).isTrue();
    }

    @Test
    void implementsSessionManagerStateBoundary() {
        SessionManager.TeamAgentStateView state = new TeamAgentState();
        RecordingSession session = new RecordingSession("session-2");

        state.setTeamSession(session);

        assertThat(state.getTeamSession()).isSameAs(session);
    }

    private record RecordingSession(String sessionId) implements SessionManager.AgentTeamSessionView {
        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> state) {
        }
    }
}
