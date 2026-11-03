/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeepAgentStateTest {

    @Test
    void testDefaults() {
        DeepAgentState state = DeepAgentState.fromSessionMap(null);
        assertEquals(0, state.getIteration());
        assertNull(state.getTaskPlan());
        assertEquals(List.of(), state.getPendingFollowUps());
    }

    @Test
    void testRoundTripSessionMap() {
        TaskPlan plan = new TaskPlan("demo", List.of(new TodoItem("t1", "first")), null);
        DeepAgentState state = new DeepAgentState(
                3,
                plan,
                Map.of("stop", true),
                List.of("msg1", "msg2"),
                new PlanModeState("plan", "normal", "slug", "legacy")
        );

        DeepAgentState loaded = DeepAgentState.fromSessionMap(state.toSessionMap());
        assertEquals(3, loaded.getIteration());
        assertNotNull(loaded.getTaskPlan());
        assertEquals("demo", loaded.getTaskPlan().getGoal());
        assertEquals("t1", loaded.getTaskPlan().getTasks().get(0).getId());
        assertEquals(List.of("msg1", "msg2"), loaded.getPendingFollowUps());
        assertEquals("plan", loaded.getPlanMode().getMode());
        assertEquals("slug", loaded.getPlanMode().getPlanSlug());
    }
}
