/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InmemoryStorage.
 * Mirrors Python's tests/unit_tests/core/session/checkpointer/test_inmemory_storage.py
 */
class TestInmemoryStorage {

    @Nested
    @DisplayName("InmemoryStorage tests")
    class StorageTests {

        @Test
        @DisplayName("test_inmemory_agent_storage_save_recover_exists_and_clear")
        void testInmemoryAgentStorageSaveRecoverExistsAndClear() {
            InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
            AgentSession session = new AgentSession("session-agent", new Config());
            
            session.state().update(Map.of("name", "alice"));
            session.state().updateGlobal(Map.of("shared", "value"));

            checkpointer.preAgentExecute(session, null);
            checkpointer.postAgentExecute(session);

            assertTrue(checkpointer.sessionExists("session-agent"));

            AgentSession recovered = new AgentSession("session-agent", new Config());
            checkpointer.preAgentExecute(recovered, null);

            assertEquals("alice", recovered.state().get("name"));
            assertEquals("value", recovered.state().getGlobal("shared"));

            checkpointer.release("session-agent");
            assertFalse(checkpointer.sessionExists("session-agent"));
        }

        @Test
        @DisplayName("test_inmemory_agent_group_storage_save_recover_exists_and_clear")
        void testInmemoryAgentGroupStorageSaveRecoverExistsAndClear() {
            InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
            AgentTeamSession session = new AgentTeamSession("session-team", "team-1", null, null);
            
            session.state().updateGlobal(Map.of("team", "alpha"));

            checkpointer.preAgentExecute(session, null);
            checkpointer.postAgentExecute(session);

            assertTrue(checkpointer.sessionExists("session-team"));

            AgentTeamSession recovered = new AgentTeamSession("session-team", "team-1", null, null);
            checkpointer.preAgentExecute(recovered, null);

            assertEquals("alpha", recovered.state().getGlobal("team"));

            checkpointer.release("session-team");
            assertFalse(checkpointer.sessionExists("session-team"));
        }
    }
}