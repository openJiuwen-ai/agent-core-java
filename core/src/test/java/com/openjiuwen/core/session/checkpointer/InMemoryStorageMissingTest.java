/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's in-memory storage tests in
 * {@code tests/unit_tests/core/session/checkpointer/test_inmemory_storage.py}.</p>
 */
class InMemoryStorageMissingTest {

    @Test
    void testInmemoryAgentStorageSaveRecoverExistsAndClear() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentSession session = agentSession(checkpointer);
        session.state().update(Map.of("name", "alice"));
        session.state().updateGlobal(Map.of("shared", "value"));

        checkpointer.preAgentExecute(session, null);
        checkpointer.postAgentExecute(session);

        assertThat(checkpointer.sessionExists("session-agent")).isTrue();

        AgentSession recovered = agentSession(checkpointer);
        checkpointer.preAgentExecute(recovered, null);

        assertThat(recovered.state().get("name")).isEqualTo("alice");
        assertThat(recovered.state().getGlobal("shared")).isEqualTo("value");

        checkpointer.release("session-agent", "agent-1");
        AgentSession afterClear = agentSession(checkpointer);
        checkpointer.preAgentExecute(afterClear, null);
        assertThat(afterClear.state().get("name")).isNull();
        assertThat(afterClear.state().getGlobal("shared")).isNull();
    }

    @Test
    void testInmemoryAgentGroupStorageSaveRecoverExistsAndClear() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        AgentTeamSession session = new AgentTeamSession("session-team", "team-1", new Config(), checkpointer);
        session.state().update(Map.of("agent_local", "should_not_be_restored"));
        session.state().updateGlobal(Map.of("team", "alpha"));

        checkpointer.preAgentTeamExecute(session, null);
        checkpointer.postAgentTeamExecute(session);

        assertThat(checkpointer.sessionExists("session-team")).isTrue();

        AgentTeamSession recovered = new AgentTeamSession("session-team", "team-1", new Config(), checkpointer);
        checkpointer.preAgentTeamExecute(recovered, null);

        assertThat(recovered.state().getGlobal("team")).isEqualTo("alpha");
        assertThat(recovered.state().get("agent_local")).isNull();

        checkpointer.release("session-team");
        assertThat(checkpointer.sessionExists("session-team")).isFalse();
    }

    private static AgentSession agentSession(InMemoryCheckpointer checkpointer) {
        Config config = new Config();
        config.setAgentConfig(Map.of("id", "agent-1"));
        return new AgentSession("session-agent", config, checkpointer, null, null);
    }
}
