/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_deep_agent_session_state} in
 * {@code tests.unit_tests.harness.test_deep_agent_session_state}.
 */
class TestDeepAgentSessionState {

    private DeepAgent newAgent() {
        AgentCard card = new AgentCard();
        card.setName("test_state");
        return new DeepAgent(card);
    }

    private AgentSessionApi newSession() {
        return new AgentSessionApi("sess_test");
    }

    @Test
    @Tag("level0")
    @DisplayName("load_state returns defaults for empty session")
    void testLoadEmptyState() {
        DeepAgent agent = newAgent();
        DeepAgentState state = agent.loadState(newSession());

        assertEquals(0, state.getIteration());
        assertNull(state.getTaskPlan());
    }

    @Test
    @Tag("level0")
    @DisplayName("save_state and load_state round-trip task plan")
    void testSaveAndReloadState() {
        DeepAgent agent = newAgent();
        AgentSessionApi session = newSession();

        TaskPlan plan = new TaskPlan("demo", List.of(new TodoItem("t1", "first")));
        DeepAgentState state = new DeepAgentState();
        state.setIteration(3);
        state.setTaskPlan(plan);

        agent.saveState(session, state);
        agent.clearState(session);

        DeepAgentState loaded = agent.loadState(session);
        assertEquals(3, loaded.getIteration());
        assertNotNull(loaded.getTaskPlan());
        assertEquals("demo", loaded.getTaskPlan().getGoal());
        assertEquals(1, loaded.getTaskPlan().getTasks().size());
        assertEquals("t1", loaded.getTaskPlan().getTasks().get(0).getId());
        assertEquals("first", loaded.getTaskPlan().getTasks().get(0).getContent());
    }

    @Test
    @Tag("level0")
    @DisplayName("todo status survives session round-trip")
    void testSaveAndReloadStateWithTodoStatus() {
        DeepAgent agent = newAgent();
        AgentSessionApi session = newSession();

        TodoItem todo = new TodoItem("t1", "first");
        todo.setStatus(TodoStatus.IN_PROGRESS);
        TaskPlan plan = new TaskPlan("demo", List.of(todo));

        DeepAgentState state = new DeepAgentState();
        state.setTaskPlan(plan);
        agent.saveState(session, state);
        agent.clearState(session);

        DeepAgentState loaded = agent.loadState(session);
        assertNotNull(loaded.getTaskPlan());
        assertEquals(TodoStatus.IN_PROGRESS, loaded.getTaskPlan().getTasks().get(0).getStatus());
    }

    @Test
    @Tag("level0")
    @DisplayName("pending follow ups survive session round-trip")
    void testPendingFollowUpsRoundTrip() {
        DeepAgent agent = newAgent();
        AgentSessionApi session = newSession();

        DeepAgentState state = new DeepAgentState();
        state.setPendingFollowUps(List.of("follow-1", "follow-2"));
        agent.saveState(session, state);
        agent.clearState(session);

        DeepAgentState loaded = agent.loadState(session);
        assertEquals(List.of("follow-1", "follow-2"), loaded.getPendingFollowUps());
    }

    @Test
    @Tag("level0")
    @DisplayName("pending follow ups default to empty list")
    void testPendingFollowUpsDefaultsEmpty() {
        DeepAgentState state = new DeepAgentState();
        assertNotNull(state.getPendingFollowUps());
        assertTrue(state.getPendingFollowUps().isEmpty());
    }
}
