/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * <p>Mirrors Python's {@code test_task_planning_rail_lifecycle_hooks_noop} in
 * {@code tests/unit_tests/harness/test_task_planning_rail_skeleton.py}.</p>
 */
class TaskPlanningRailSkeletonMissingTest {

    @Test
    void testTaskPlanningRailLifecycleHooksNoop() {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = new DeepAgent(new AgentCard("deep", "deep", "test"));
        CallbackContext ctx = new CallbackContext(agent, new LinkedHashMap<>(Map.of(
                "query", "build feature",
                "session_id", "sess_task_plan")));

        assertDoesNotThrow(() -> {
            rail.beforeInvoke(ctx);
            rail.beforeTaskIteration(ctx);
            rail.beforeModelCall(ctx);

            ctx.put("tool_name", "todo_write");
            ctx.put("tool_result", Map.of("todos", java.util.List.of()));
            ctx.put("tool_msg", "ok");
            rail.afterToolCall(ctx);

            rail.afterTaskIteration(ctx);
            rail.afterInvoke(ctx);
        });
    }
}
