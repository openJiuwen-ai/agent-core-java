/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test WorkflowDesigner functionality.
 * <p>
 * Mirrors Python's {@code test_workflow_designer.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_workflow_designer.py}.
 *
 */
class TestWorkflowDesigner {

    /**
     * Test WorkflowDesigner initialization.
     * <p>
     * Mirrors Python's {@code TestWorkflowDesignerInit} class.
     */
    @Nested
    class TestInit {

        @Test
        void testInitSuccess() {
            Object llm = new Object();
            WorkflowDesigner designer = new WorkflowDesigner(llm);

            assertSame(llm, designer.getLlm());
        }

        @Test
        void testInitWithNullLlm() {
            WorkflowDesigner designer = new WorkflowDesigner(null);

            assertNull(designer.getLlm());
        }
    }

    /**
     * Test WorkflowDesigner parseReflectionResult method.
     * <p>
     * Mirrors Python's {@code TestWorkflowDesignerParseReflectionResult} class.
     */
    @Nested
    class TestParseReflectionResult {

        @Test
        void testParseWithChineseSeparator() {
            String result = WorkflowDesigner.parseReflectionResult(
                    "## 闂璇勪及\n鏃犻棶棰榎n## New Workflow Design\nFinal design");

            assertTrue(result.contains("Final design"));
        }

        @Test
        void testParseWithEnglishSeparator() {
            String result = WorkflowDesigner.parseReflectionResult("Evaluation\n New Workflow Design\nFinal design");

            assertTrue(result.contains("Final design"));
        }

        @Test
        void testParseWithoutSeparator() {
            assertEquals("Just design content",
                    WorkflowDesigner.parseReflectionResult("Just design content"));
        }
    }
}
