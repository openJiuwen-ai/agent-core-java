package com.openjiuwen.agent_teams;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTeamsContextTest {

    @Test
    void setAndResetRestoresPreviousSessionId() {
        AgentTeamsContext.SessionIdToken first = AgentTeamsContext.setSessionId("session-a");
        AgentTeamsContext.SessionIdToken second = AgentTeamsContext.setSessionId("session-b");

        assertThat(AgentTeamsContext.getSessionId()).isEqualTo("session-b");

        AgentTeamsContext.resetSessionId(second);
        assertThat(AgentTeamsContext.getSessionId()).isEqualTo("session-a");

        AgentTeamsContext.resetSessionId(first);
        assertThat(AgentTeamsContext.getSessionId()).isEmpty();
    }
}
