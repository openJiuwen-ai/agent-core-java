/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TracerEnumsTest {
    @Test
    @DisplayName("InvokeType preserves Python enum payloads")
    void testInvokeTypeValues() {
        Map<InvokeType, String> expected = Map.of(
                InvokeType.PROMPT, "prompt",
                InvokeType.LLM, "llm",
                InvokeType.PLUGIN, "plugin",
                InvokeType.WORKFLOW, "workflow",
                InvokeType.CHAIN, "chain",
                InvokeType.RETRIEVER, "retriever",
                InvokeType.EVALUATOR, "evaluator"
        );

        expected.forEach((key, value) -> assertEquals(value, key.getValue()));
    }

    @Test
    @DisplayName("NodeStatus preserves Python enum payloads")
    void testNodeStatusValues() {
        Map<NodeStatus, String> expected = Map.of(
                NodeStatus.START, "start",
                NodeStatus.FINISH, "finish",
                NodeStatus.RUNNING, "running",
                NodeStatus.INTERRUPTED, "interrupted",
                NodeStatus.ERROR, "error"
        );

        expected.forEach((key, value) -> assertEquals(value, key.getValue()));
    }
}
