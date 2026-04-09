/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.InMemoryStore;
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

import static org.junit.jupiter.api.Assertions.*;

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
        checkpointer.graphStore().save(
                "session-1",
                "workflow-1",
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

        checkpointer.graphStore().save(
                "session-1",
                "workflow-1",
                GraphStoreState.create("workflow-1", 1, Map.of(), List.of(), Map.of(), Map.of()));

        checkpointer.release("session-1");

        assertFalse(checkpointer.graphStore().get("session-1", "workflow-1").isPresent());
    }

    @Test
    @DisplayName("graphStore exposes a real in-memory store")
    void testGraphStoreIsInMemoryStore() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        assertInstanceOf(InMemoryStore.class, checkpointer.graphStore());
    }
}
