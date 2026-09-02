/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link PersistenceCheckpointer}.
 */
class PersistenceCheckpointerTest {
    @Test
    @DisplayName("corrupt serialized agent state is ignored during recovery")
    void corruptSerializedAgentStateDoesNotAbortRecovery() {
        String sessionId = "corrupt-session";
        InMemoryKVStore kvStore = new InMemoryKVStore();
        kvStore.set(Checkpointer.resolveNsKey(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, sessionId,
                "agent_state_blobs_dump_type"), "java");
        kvStore.set(Checkpointer.resolveNsKey(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, sessionId,
                "agent_state_blobs"), new byte[]{0, 1, 2});
        PersistenceCheckpointer checkpointer = new PersistenceCheckpointer(kvStore);
        AgentSession session = new AgentSession(sessionId, new Config(), checkpointer);

        assertDoesNotThrow(() -> checkpointer.preAgentExecute(session, null));
    }

    @Test
    @Tag("integration")
    @DisplayName("SQLite restores agent state after reopening the checkpointer")
    void sqliteRestoresAgentStateAcrossCheckpointerInstances(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("checkpointer.db");
        CheckpointerConfig config = new CheckpointerConfig("persistence",
                Map.of("db_type", "sqlite", "db_path", databasePath.toString()));

        try (Checkpointer writer = CheckpointerFactory.create(config)) {
            assertInstanceOf(PersistenceCheckpointer.class, writer);
            AgentSession saved = new AgentSession("sqlite-session", new Config(), writer);
            writer.preAgentExecute(saved, null);
            saved.state().update(Map.of("local", "saved"));
            saved.state().updateGlobal(Map.of("shared", "value"));
            writer.interruptAgentExecute(saved);
        }

        try (Checkpointer reader = CheckpointerFactory.create(config)) {
            AgentSession restored = new AgentSession("sqlite-session", new Config(), reader);
            reader.preAgentExecute(restored, null);

            assertEquals("saved", restored.state().get("local"));
            assertEquals("value", restored.state().getGlobal("shared"));
        }
    }

    @Test
    @DisplayName("workflow recovery commits pending updates restored from persistence")
    void workflowRecoveryCommitsRestoredPendingUpdates() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        PersistenceCheckpointer.PersistenceWorkflowStorage storage =
                new PersistenceCheckpointer.PersistenceWorkflowStorage(kvStore);
        WorkflowSession saved =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        NodeSession savedNode = new NodeSession(saved, "ask_user");
        savedNode.state().update(Map.of("checkpoint", "saved"));
        storage.save(saved);

        WorkflowSession restored =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        InteractiveInput inputs = new InteractiveInput();
        inputs.update("ask_user", "answer");
        storage.recover(restored, inputs);

        NodeSession restoredNode = new NodeSession(restored, "ask_user");
        assertEquals("saved", restoredNode.state().get("checkpoint"));
        assertEquals(List.of("answer"), restoredNode.state().get(Constant.INTERACTIVE_INPUT));
    }
}
