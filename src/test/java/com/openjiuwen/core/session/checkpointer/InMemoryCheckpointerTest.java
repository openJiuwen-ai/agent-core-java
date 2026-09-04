/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.InMemoryStore;
import com.openjiuwen.core.graph.store.KeyLockedStore;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tests for {@link InMemoryCheckpointer}.
 * <p>
 * Ported from Python workflow/agent storage and integration checkpointer tests.
 */
class InMemoryCheckpointerTest {
    @Test
    @DisplayName("preAgentExecute restores saved state and queues interactive input")
    void testPreAgentExecuteRestoresStateAndQueuesInteractiveInput() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();

        AgentSession session = new AgentSession("session-1", new Config(), checkpointer);
        checkpointer.preAgentExecute(session, null);
        session.state().updateGlobal(Map.of("persisted", "value"));
        checkpointer.interruptAgentExecute(session);

        AgentSession restored = new AgentSession("session-1", new Config(), checkpointer);
        checkpointer.preAgentExecute(restored, "hello");

        assertEquals("value", restored.state().getGlobal("persisted"));
        assertEquals(List.of("hello"), restored.state().get(Constant.INTERACTIVE_INPUT));
    }

    @Test
    @DisplayName("preWorkflowExecute restores raw interactive input into workflow state")
    void testPreWorkflowExecuteRestoresRawInteractiveInput() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);

        checkpointer.preWorkflowExecute(session, new InteractiveInput("resume-value"));

        WorkflowStateCollection state = (WorkflowStateCollection) session.state();
        assertEquals("resume-value", state.getWorkflow(Constant.INTERACTIVE_INPUT));
    }

    @Test
    @DisplayName("preWorkflowExecute restores node interactive inputs to node state")
    void testPreWorkflowExecuteRestoresNodeInteractiveInput() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);

        InteractiveInput inputs = new InteractiveInput();
        inputs.update("ask_user", Map.of("answer", "ok"));

        checkpointer.preWorkflowExecute(session, inputs);

        NodeSession nodeSession = new NodeSession(session, "ask_user");
        assertEquals(List.of(Map.of("answer", "ok")), nodeSession.state().get(Constant.INTERACTIVE_INPUT));
    }

    @Test
    @DisplayName("workflow checkpoint is saved on exception and cleared on completion")
    void testWorkflowCheckpointSaveAndClear() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);

        checkpointer.preWorkflowExecute(session, null);
        session.state().updateGlobal(Map.of("persisted", "value"));
        ((WorkflowCommitState) session.state()).commit();
        checkpointer.graphStore().save("session-1", "workflow-1",
                GraphStoreState.create("workflow-1", 1, Map.of("k", "v"), List.of(), Map.of(), Map.of()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> checkpointer.postWorkflowExecute(session, null, new IllegalStateException("boom")));
        assertEquals("boom", error.getMessage());
        assertTrue(checkpointer.sessionExists("session-1"));
        assertTrue(checkpointer.graphStore().get("session-1", "workflow-1").isPresent());

        WorkflowSession restored = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        checkpointer.preWorkflowExecute(restored, new InteractiveInput("resume"));
        assertEquals("value", restored.state().getGlobal("persisted"));

        checkpointer.postWorkflowExecute(restored, Map.of("ok", true), null);
        assertFalse(checkpointer.graphStore().get("session-1", "workflow-1").isPresent());
        assertFalse(checkpointer.sessionExists("session-1"));
    }

    @Test
    @DisplayName("release clears graph store for interrupted workflows")
    void testReleaseClearsGraphStore() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);

        checkpointer.preWorkflowExecute(session, null);
        ((WorkflowCommitState) session.state()).commit();
        assertThrows(RuntimeException.class,
                () -> checkpointer.postWorkflowExecute(session, null, new IllegalStateException("boom")));

        checkpointer.graphStore().save("session-1", "workflow-1",
                GraphStoreState.create("workflow-1", 1, Map.of(), List.of(), Map.of(), Map.of()));

        checkpointer.release("session-1");

        assertFalse(checkpointer.graphStore().get("session-1", "workflow-1").isPresent());
    }

    @Test
    @DisplayName("graphStore exposes a key-locked in-memory store")
    void testGraphStoreIsInMemoryStore() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        // Concurrent write isolation wraps InMemoryStore with KeyLockedStore.
        assertInstanceOf(KeyLockedStore.class, checkpointer.graphStore());
        assertInstanceOf(InMemoryStore.class, ((KeyLockedStore) checkpointer.graphStore()).delegate());
    }

    @Test
    @DisplayName("sessions older than TTL are evicted on the next write")
    void testTtlEvictionRemovesExpiredSession() {
        AtomicLong now = new AtomicLong(1_000_000L);
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer(60_000L, 0, now::get);

        AgentSession first = new AgentSession("session-1", new Config(), checkpointer);
        checkpointer.preAgentExecute(first, null);
        assertTrue(checkpointer.sessionExists("session-1"));

        now.set(1_000_000L + 60_001L);
        AgentSession second = new AgentSession("session-2", new Config(), checkpointer);
        checkpointer.preAgentExecute(second, null);

        assertFalse(checkpointer.sessionExists("session-1"),
                "expired session should be evicted by the TTL policy");
        assertTrue(checkpointer.sessionExists("session-2"));
    }

    @Test
    @DisplayName("capacity limit evicts the least recently written session")
    void testCapacityEvictionRemovesOldestWrittenSession() {
        AtomicLong now = new AtomicLong(1_000_000L);
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer(0, 2, now::get);

        for (String sessionId : List.of("session-1", "session-2")) {
            AgentSession session = new AgentSession(sessionId, new Config(), checkpointer);
            checkpointer.preAgentExecute(session, null);
        }
        assertTrue(checkpointer.sessionExists("session-1"));
        assertTrue(checkpointer.sessionExists("session-2"));

        now.set(1_000_000L + 1L);
        AgentSession third = new AgentSession("session-3", new Config(), checkpointer);
        checkpointer.preAgentExecute(third, null);

        assertFalse(checkpointer.sessionExists("session-1"),
                "least recently written session should be evicted when capacity is exceeded");
        assertTrue(checkpointer.sessionExists("session-2"));
        assertTrue(checkpointer.sessionExists("session-3"));
    }

    @Test
    @DisplayName("write refreshes TTL so an active session survives")
    void testWriteRefreshesTtlForActiveSession() {
        AtomicLong now = new AtomicLong(1_000_000L);
        // TTL = 100s; write every 60s so the session stays alive across a 180s window.
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer(100_000L, 0, now::get);

        AgentSession session = new AgentSession("session-1", new Config(), checkpointer);
        checkpointer.preAgentExecute(session, null);
        for (int i = 0; i < 3; i++) {
            now.set(1_000_000L + (i + 1) * 60_000L);
            checkpointer.postAgentExecute(session);
        }
        now.set(1_000_000L + 180_001L);

        AgentSession probe = new AgentSession("session-2", new Config(), checkpointer);
        checkpointer.preAgentExecute(probe, null);

        assertTrue(checkpointer.sessionExists("session-1"),
                "a session written within the TTL window must survive");
    }

    @Test
    @DisplayName("eviction removes graph store entries for the evicted session")
    void testEvictionClearsGraphStoreEntries() {
        AtomicLong now = new AtomicLong(1_000_000L);
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer(60_000L, 0, now::get);

        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        checkpointer.preWorkflowExecute(session, null);
        checkpointer.graphStore().save("session-1", "workflow-1",
                GraphStoreState.create("workflow-1", 1, Map.of(), List.of(), Map.of(), Map.of()));
        assertTrue(checkpointer.graphStore().get("session-1", "workflow-1").isPresent());

        now.set(1_000_000L + 60_001L);
        WorkflowSession other = new WorkflowSession("workflow-2", null, "session-2", InMemoryState.create(), null);
        checkpointer.preWorkflowExecute(other, null);

        assertFalse(checkpointer.graphStore().get("session-1", "workflow-1").isPresent(),
                "graph store entries must be removed together with the evicted session");
    }

    @Test
    @DisplayName("release still clears state explicitly and does not interfere with eviction")
    void testReleaseAndEvictionCoexist() {
        AtomicLong now = new AtomicLong(1_000_000L);
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer(0, 2, now::get);

        for (String sessionId : List.of("session-1", "session-2")) {
            AgentSession session = new AgentSession(sessionId, new Config(), checkpointer);
            checkpointer.preAgentExecute(session, null);
        }
        checkpointer.release("session-1");
        assertFalse(checkpointer.sessionExists("session-1"));

        // After release the slot is free: session-3 must not evict session-2.
        now.set(1_000_000L + 1L);
        AgentSession third = new AgentSession("session-3", new Config(), checkpointer);
        checkpointer.preAgentExecute(third, null);

        assertTrue(checkpointer.sessionExists("session-2"),
                "released session must free its capacity slot");
        assertTrue(checkpointer.sessionExists("session-3"));
    }
}
