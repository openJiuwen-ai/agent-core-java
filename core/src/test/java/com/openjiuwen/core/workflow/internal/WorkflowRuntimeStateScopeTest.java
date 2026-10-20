/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.common.constants.Constant;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkflowRuntimeStateScopeTest {
    @Test
    void loopReadsEnclosingInputsWithoutReplacingLocalValues() {
        WorkflowRuntimeState root = seeded(Map.of(Constant.LOOP_ID, "loop",
                "local", Map.of("value", "body")));
        Map<String, Object> inputs = root.createNodeState("loop.ask", "loop").getInputs(
                Map.of("question", "${start.question}", "local", "${local.value}"));
        assertEquals("outer", inputs.get("question"));
        assertEquals("body", inputs.get("local"));
    }

    @Test
    void ordinarySubworkflowDoesNotReadUndeclaredParentInputs() {
        WorkflowRuntimeState root = seeded(Map.of("local", Map.of("value", "child")));
        Map<String, Object> inputs = root.createNodeState("loop.ask", "loop").getInputs(
                Map.of("question", "${start.question}", "local", "${local.value}"));
        assertNull(inputs.get("question"));
        assertEquals("child", inputs.get("local"));
    }

    private static WorkflowRuntimeState seeded(Map<String, Object> loopState) {
        WorkflowRuntimeState root = WorkflowRuntimeState.create();
        root.getIoState().updateById("seed", Map.of("start", Map.of("question", "outer"),
                "local", Map.of("value", "parent"), "loop", loopState));
        root.commit();
        return root;
    }
}
