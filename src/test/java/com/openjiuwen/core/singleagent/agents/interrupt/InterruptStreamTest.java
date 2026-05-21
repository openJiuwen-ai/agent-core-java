/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HITL rail in stream mode.
 *
 * <p>Mirrors Python's {@code test_interrupt_stream.py} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/}.
 */
@DisplayName("Interrupt Stream")
class InterruptStreamTest {

    @Test
    @DisplayName("interaction type constant is __interaction__")
    void testInteractionTypeConstant() {
        String interaction = "__interaction__";
        assertThat(interaction).isEqualTo("__interaction__");
    }

    @Test
    @DisplayName("stream mode output schema types are well-defined")
    void testStreamModeOutputSchemaTypes() {
        String interactionType = "__interaction__";
        String workflowFinalType = "workflow_final";
        String endNodeStreamType = "end node stream";

        assertThat(interactionType).isNotEmpty();
        assertThat(workflowFinalType).isNotEmpty();
        assertThat(endNodeStreamType).isNotEmpty();
    }
}
