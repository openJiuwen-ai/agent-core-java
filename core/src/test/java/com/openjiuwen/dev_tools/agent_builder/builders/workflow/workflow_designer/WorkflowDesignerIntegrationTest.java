/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestWorkflowDesignerIntegration} and related groups in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_workflow_designer_integration.py}.
 */
class WorkflowDesignerIntegrationTest {

    @Nested
    class TestWorkflowDesignerIntegration {

        @Test
        void testWorkflowDesignerInitialization() throws ReflectiveOperationException {
            RecordingModel mockLlm = recordingModel("unused");
            WorkflowDesigner designer = new WorkflowDesigner(mockLlm.model());

            assertSame(mockLlm.model(), fieldValue(designer, "llm"));
        }

        @Test
        void testBasicDesignIntegration() {
            RecordingModel mockLlm = recordingModel("Basic design result");
            WorkflowDesigner designer = new WorkflowDesigner(mockLlm.model());

            String result = designer.basicDesign("create workflow", "tool list");

            assertEquals("Basic design result", result);
            assertFalse(mockLlm.capturedMessages().isEmpty());
        }

        @Test
        void testBranchDesignIntegration() {
            RecordingModel mockLlm = recordingModel("Branch design result");
            WorkflowDesigner designer = new WorkflowDesigner(mockLlm.model());

            String result = designer.branchDesign("create workflow", "basic design");

            assertEquals("Branch design result", result);
            assertFalse(mockLlm.capturedMessages().isEmpty());
        }

        @Test
        void testReflectionEvaluationIntegration() {
            RecordingModel mockLlm = recordingModel("## New Workflow Design\nFinal design");
            WorkflowDesigner designer = new WorkflowDesigner(mockLlm.model());

            String result = designer.reflectionEvaluation("create workflow", "basic design", "branch design");

            assertTrue(result.contains("Final design"));
            assertFalse(mockLlm.capturedMessages().isEmpty());
        }
    }

    @Nested
    class TestWorkflowDesignerParseReflectionResult {

        @Test
        void testParseWithNewWorkflowDesignMarker() {
            String content = "## 问题评估\n无问题\n## New Workflow Design\nFinal design content";

            String result = WorkflowDesigner.parseReflectionResult(content);

            assertTrue(result.contains("Final design content"));
        }

        @Test
        void testParseWithoutMarker() {
            String content = "Just some design content";

            String result = WorkflowDesigner.parseReflectionResult(content);

            assertEquals("Just some design content", result);
        }

        @Test
        void testParseWithEnglishMarker() {
            String content = "Evaluation\n New Workflow Design\nFinal design";

            String result = WorkflowDesigner.parseReflectionResult(content);

            assertTrue(result.contains("Final design"));
        }
    }

    @Nested
    class TestWorkflowDesignerBasicDesign {

        @Test
        void testBasicDesignWithEmptyTools() {
            RecordingModel mockLlm = recordingModel("Design without tools");
            WorkflowDesigner designer = new WorkflowDesigner(mockLlm.model());

            String result = designer.basicDesign("create workflow", "");

            assertEquals("Design without tools", result);
        }

        @Test
        void testBasicDesignWithTools() {
            RecordingModel mockLlm = recordingModel("Design with tools");
            WorkflowDesigner designer = new WorkflowDesigner(mockLlm.model());

            String result = designer.basicDesign("create workflow", "tool1, tool2");

            assertEquals("Design with tools", result);
        }
    }

    @Nested
    class TestWorkflowDesignerBranchDesign {

        @Test
        void testBranchDesignBasic() {
            RecordingModel mockLlm = recordingModel("Branch design");
            WorkflowDesigner designer = new WorkflowDesigner(mockLlm.model());

            String result = designer.branchDesign("create workflow", "basic design");

            assertEquals("Branch design", result);
        }
    }

    private static RecordingModel recordingModel(String... responses) {
        AtomicInteger responseIndex = new AtomicInteger();
        List<List<BaseMessage>> capturedMessages = new ArrayList<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.add(new ArrayList<>(messages));
            int index = Math.min(responseIndex.getAndIncrement(), responses.length - 1);
            return CompletableFuture.completedFuture(new AssistantMessage(responses[index]));
        });
        return new RecordingModel(model, capturedMessages);
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private record RecordingModel(Model model, List<List<BaseMessage>> capturedMessages) {
    }
}
