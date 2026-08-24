/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;


import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.test_deep_agent_session_state} in
 * {@code tests/unit_tests/harness/test_deep_agent_session_state.py}.</p>
 */
class DeepAgentSessionStatePythonParityTest {

    @Test
    void loadEmptyState() {
        DeepAgent agent = makeAgent();
        FakeSession session = new FakeSession();

        DeepAgentState state = agent.loadState(session);

        assertThat(state.getIteration()).isZero();
        assertThat(state.getTaskPlan()).isNull();
    }

    @Test
    void saveAndReloadState() {
        DeepAgent agent = makeAgent();
        FakeSession session = new FakeSession();
        TaskPlan plan = new TaskPlan("demo", List.of(new TodoItem("t1", "first")));
        DeepAgentState state = new DeepAgentState(3, plan, null, List.of(), null);

        agent.saveState(session, state);
        agent.clearState(session);
        DeepAgentState loaded = agent.loadState(session);

        assertThat(loaded.getIteration()).isEqualTo(3);
        assertThat(loaded.getTaskPlan()).isNotNull();
        assertThat(loaded.getTaskPlan().getGoal()).isEqualTo("demo");
        assertThat(loaded.getTaskPlan().getTasks()).hasSize(1);
        assertThat(loaded.getTaskPlan().getTasks().get(0).getId()).isEqualTo("t1");
    }

    @Test
    void saveAndReloadStateWithTodoStatus() {
        DeepAgent agent = makeAgent();
        FakeSession session = new FakeSession();
        TaskPlan plan = new TaskPlan(
                "demo",
                List.of(
                        new TodoItem("t1", "first", "", "", TodoStatus.COMPLETED, List.of(), null, null, null),
                        new TodoItem("t2", "second", "", "", TodoStatus.PENDING, List.of(), null, null, null)
                )
        );
        DeepAgentState state = new DeepAgentState(3, plan, null, List.of(), null);

        agent.saveState(session, state);
        agent.clearState(session);
        DeepAgentState loaded = agent.loadState(session);

        assertThat(loaded.getTaskPlan()).isNotNull();
        assertThat(loaded.getTaskPlan().getTasks().get(0).getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(loaded.getTaskPlan().getTasks().get(1).getStatus()).isEqualTo(TodoStatus.PENDING);
    }

    @Test
    void pendingFollowUpsRoundTrip() {
        DeepAgent agent = makeAgent();
        FakeSession session = new FakeSession();
        DeepAgentState state = new DeepAgentState(1, null, null, List.of("msg1", "msg2", "msg3"), null);

        agent.saveState(session, state);
        agent.clearState(session);
        DeepAgentState loaded = agent.loadState(session);

        assertThat(loaded.getPendingFollowUps()).containsExactly("msg1", "msg2", "msg3");
    }

    @Test
    void pendingFollowUpsDefaultsEmpty() {
        DeepAgent agent = makeAgent();
        FakeSession session = new FakeSession();
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("iteration", 2);
        legacy.put("task_plan", null);
        legacy.put("stop_condition_state", null);
        session.updateState(Map.of(DeepAgentState.SESSION_STATE_KEY, legacy));

        DeepAgentState loaded = agent.loadState(session);

        assertThat(loaded.getPendingFollowUps()).isEmpty();
    }

    private static DeepAgent makeAgent() {
        return new DeepAgent(new AgentCard("test_state", "test_state", "test_state"));
    }

    private static final class FakeSession implements AgentSessionApi {
        private final Map<String, Object> state = new LinkedHashMap<>();

        @Override
        public String getSessionId() {
            return "sess_test";
        }

        @Override
        public Object getState(String key) {
            if (key == null) {
                return new LinkedHashMap<>(state);
            }
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return Collections.emptyIterator();
        }
    }
}
