/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for WorkflowDesigner integration.
 * <p>
 * Mirrors Python's {@code test_workflow_designer_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.workflow_designer}.
 */
class TestWorkflowDesignerIntegration {

    public static final class RecordingLlm {
        private String responseContent = "";
        private int callCount;
        private Object lastMessages;

        public AssistantMessage invoke(Object messages) {
            this.lastMessages = messages;
            this.callCount += 1;
            return new AssistantMessage(responseContent);
        }
    }

    @Nested
    class TestWorkflowDesignerIntegrationInner {

        @Test
        void testWorkflowDesignerInitialization() {
            RecordingLlm llm = new RecordingLlm();
            WorkflowDesigner designer = new WorkflowDesigner(llm);

            assertThat(designer.getLlm()).isSameAs(llm);
        }

        @Test
        void testBasicDesignIntegration() {
            RecordingLlm llm = new RecordingLlm();
            llm.responseContent = "Basic design result";
            WorkflowDesigner designer = new WorkflowDesigner(llm);

            String result = designer.basicDesign("create workflow", "tool list");

            assertThat(result).isEqualTo("Basic design result");
            assertThat(llm.callCount).isEqualTo(1);
            assertThat(llm.lastMessages).isNotNull();
        }

        @Test
        void testBranchDesignIntegration() {
            RecordingLlm llm = new RecordingLlm();
            llm.responseContent = "Branch design result";
            WorkflowDesigner designer = new WorkflowDesigner(llm);

            String result = designer.branchDesign("create workflow", "basic design");

            assertThat(result).isEqualTo("Branch design result");
            assertThat(llm.callCount).isEqualTo(1);
        }

        @Test
        void testReflectionEvaluationIntegration() {
            RecordingLlm llm = new RecordingLlm();
            llm.responseContent = "## New Workflow Design\nFinal design";
            WorkflowDesigner designer = new WorkflowDesigner(llm);

            String result = designer.reflectionEvaluation("create workflow", "basic design", "branch design");

            assertThat(result).contains("Final design");
            assertThat(llm.callCount).isEqualTo(1);
        }
    }

    @Nested
    class TestWorkflowDesignerParseReflectionResult {

        @Test
        void testParseWithNewWorkflowDesignMarker() {
            String result = WorkflowDesigner.parseReflectionResult(
                    "## 问题评估\n无问题\n## New Workflow Design\nFinal design content"
            );

            assertThat(result).contains("Final design content");
        }

        @Test
        void testParseWithoutMarker() {
            assertThat(WorkflowDesigner.parseReflectionResult("Just some design content"))
                    .isEqualTo("Just some design content");
        }

        @Test
        void testParseWithEnglishMarker() {
            String result = WorkflowDesigner.parseReflectionResult("Evaluation\n New Workflow Design\nFinal design");

            assertThat(result).contains("Final design");
        }
    }

    @Nested
    class TestWorkflowDesignerBasicDesign {

        @Test
        void testBasicDesignWithEmptyTools() {
            RecordingLlm llm = new RecordingLlm();
            llm.responseContent = "Design without tools";
            WorkflowDesigner designer = new WorkflowDesigner(llm);

            assertThat(designer.basicDesign("create workflow", "")).isEqualTo("Design without tools");
        }

        @Test
        void testBasicDesignWithTools() {
            RecordingLlm llm = new RecordingLlm();
            llm.responseContent = "Design with tools";
            WorkflowDesigner designer = new WorkflowDesigner(llm);

            assertThat(designer.basicDesign("create workflow", "tool1, tool2")).isEqualTo("Design with tools");
        }
    }

    @Nested
    class TestWorkflowDesignerBranchDesign {

        @Test
        void testBranchDesignBasic() {
            RecordingLlm llm = new RecordingLlm();
            llm.responseContent = "Branch design";
            WorkflowDesigner designer = new WorkflowDesigner(llm);

            assertThat(designer.branchDesign("create workflow", "basic design")).isEqualTo("Branch design");
        }
    }
}
