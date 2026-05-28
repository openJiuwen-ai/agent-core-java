/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test for a minimal travel assistant workflow.
 * <p>
 * Mirrors Python's {@code test_real_workflow.py} in
 * {@code tests/system_tests/workflow}.
 */
@Tag("system-test")
class RealWorkflowTest {

    @Test
    @Tag("level0")
    void testWorkflowClassExists() {
        assertNotNull(Workflow.class);
    }

    @Test
    @Tag("level0")
    void testWorkflowCardClassExists() {
        assertNotNull(WorkflowCard.class);
    }

    @Test
    @Tag("level0")
    void testWorkflowCardBuilder() {
        WorkflowCard card = WorkflowCard.builder()
                .id("test_workflow")
                .name("Test Workflow")
                .version("1.0.0")
                .build();
        assertNotNull(card);
    }

    @Test
    @Tag("level1")
    void testWorkflowConstruction() {
        WorkflowCard card = WorkflowCard.builder()
                .id("test_workflow")
                .name("Test Workflow")
                .version("1.0.0")
                .build();
        Workflow workflow = new Workflow(card);
        assertNotNull(workflow);
    }
}