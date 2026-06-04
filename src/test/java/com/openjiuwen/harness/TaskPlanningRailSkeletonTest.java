/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TaskPlanningRail lifecycle behavior.
 * <p>
 * Mirrors Python's {@code test_task_planning_rail_skeleton} in
 * {@code tests.unit_tests.harness.test_task_planning_rail_skeleton}.
 */
@Tag("unit-test")
class TaskPlanningRailSkeletonTest {

    @Test
    @DisplayName("TaskPlanningRail lifecycle hooks are safe and initialize todo state")
    void testTaskPlanningRailLifecycleHooksNoop() {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = new DeepAgent(AgentCard.builder().name("deep").description("test").build());
        AgentSessionApi session = new AgentSessionApi("sess_task_plan");
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .inputs(InvokeInputs.builder().query("build feature").conversationId("conv").build())
                .session(session)
                .build();

        rail.beforeInvoke(ctx);
        rail.beforeTaskIteration(ctx);
        rail.beforeModelCall(ctx);
        ctx.setInputs(ToolCallInputs.builder().toolName("todo_write").toolResult(Map.of("todos", List.of())).build());
        rail.afterToolCall(ctx);
        ctx.setInputs(new TaskIterationInputs());
        rail.afterTaskIteration(ctx);
        ctx.setInputs(InvokeInputs.builder().query("build feature").conversationId("conv").build());
        rail.afterInvoke(ctx);

        Object todosObj = session.getState("harness.todos");
        List<?> todos = assertInstanceOf(List.class, todosObj);
        assertEquals(1, todos.size());
        Map<?, ?> first = assertInstanceOf(Map.class, todos.get(0));
        assertEquals("build feature", first.get("content"));
        assertEquals("pending", first.get("status"));
        assertTrue(first.containsKey("priority"));
    }
}
