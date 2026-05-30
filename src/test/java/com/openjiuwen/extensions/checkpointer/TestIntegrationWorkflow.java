/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer;

import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test integration workflow functionality.
 *
 * <p>Mirrors the Redis-backed workflow checkpoint behavior in
 * {@code tests_unit_tests.extensions.checkpointer.test_integration_workflow}.
 */
class TestIntegrationWorkflow {

    @Nested
    class TestWorkflowOperations {

        @Test
        void testWorkflowCheckpoint() {
            RedisCheckpointer checkpointer = checkpointer();
            WorkflowSession session = workflowSession();
            WorkflowCommitState state = (WorkflowCommitState) session.state();
            state.updateGlobal(Map.of("persisted", "value"));
            state.updateWorkflow(Map.of("step", 1));
            state.commit();

            checkpointer.postWorkflowExecute(session, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);

            assertTrue(checkpointer.sessionExists("session-1"));
        }

        @Test
        void testWorkflowRestore() {
            RedisCheckpointer checkpointer = checkpointer();
            WorkflowSession saved = workflowSession();
            WorkflowCommitState savedState = (WorkflowCommitState) saved.state();
            savedState.updateGlobal(Map.of("persisted", "value"));
            savedState.updateWorkflow(Map.of("step", 1));
            savedState.commit();
            checkpointer.postWorkflowExecute(saved, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);

            WorkflowSession restored = workflowSession();
            checkpointer.preWorkflowExecute(restored, new InteractiveInput("resume"));

            WorkflowCommitState restoredState = (WorkflowCommitState) restored.state();
            assertEquals("value", restored.state().getGlobal("persisted"));
            assertEquals(1, restoredState.getWorkflow("step"));
        }

        @Test
        void testWorkflowResume() {
            RedisCheckpointer checkpointer = checkpointer();
            WorkflowSession saved = workflowSession();
            WorkflowCommitState state = (WorkflowCommitState) saved.state();
            state.updateWorkflow(Map.of("awaiting", "user"));
            state.commit();
            checkpointer.postWorkflowExecute(saved, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);

            WorkflowSession resumed = workflowSession();
            InteractiveInput input = new InteractiveInput();
            input.update("ask", Map.of("answer", "done"));
            assertDoesNotThrow(() -> checkpointer.preWorkflowExecute(resumed, input));

            assertEquals("user", ((WorkflowCommitState) resumed.state()).getWorkflow("awaiting"));
        }
    }

    private RedisCheckpointer checkpointer() {
        return new RedisCheckpointer(new RedisStore(new TestAgentStorage.FakeRedisClient()), null);
    }

    private WorkflowSession workflowSession() {
        return new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
    }
}
