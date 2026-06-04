/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.WorkflowBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DlTransformer;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.WorkflowDesigner;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for Workflow builder module.
 * <p>
 * Mirrors Python's {@code test_workflow_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestWorkflowIntegration {

    @Nested
    class TestWorkflowBuilderIntegration {

        @Test
        void testWorkflowBuilderInitialization() {
            Object llm = new Object();
            HistoryManager historyManager = new HistoryManager();
            WorkflowBuilder builder = new WorkflowBuilder(llm, historyManager);

            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
            assertThat(builder.getLlm()).isSameAs(llm);
            assertThat(builder.getHistoryManager()).isSameAs(historyManager);
        }

        @Test
        void testWorkflowBuilderIsWorkflow() {
            WorkflowBuilder builder = new WorkflowBuilder(new Object(), new HistoryManager());

            assertThat(builder.isWorkflowBuilder()).isTrue();
        }

        @Test
        void testWorkflowBuilderReset() {
            WorkflowBuilder builder = new WorkflowBuilder(new Object(), new HistoryManager());

            builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
            builder.reset();

            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }

        @Test
        void testWorkflowBuilderGetBuildStatus() {
            WorkflowBuilder builder = new WorkflowBuilder(new Object(), new HistoryManager());

            Map<String, Object> status = builder.getBuildStatus();

            assertThat(status).containsKey("state");
            assertThat(status.get("state")).isEqualTo(AgentBuilderEnums.BuildState.INITIAL.getValue());
        }
    }

    @Nested
    class TestCycleCheckerIntegration {

        @Test
        void testCycleCheckerInitialization() {
            Object llm = new Object();
            CycleChecker checker = new CycleChecker(llm);

            assertThat(checker).isNotNull();
            assertThat(checker.getLlm()).isSameAs(llm);
        }

        @Test
        void testCycleCheckerHasRequiredMethods() {
            CycleChecker checker = new CycleChecker(new Object());

            assertThat(checker).isNotNull();
            assertThat(CycleChecker.parseCycleResultJson("{\"need_refined\": false, \"loop_desc\": \"\"}"))
                    .isNotNull();
        }

        @Test
        void testCycleCheckerParseResultJson() {
            CycleChecker.CycleResult result =
                    CycleChecker.parseCycleResultJson("{\"need_refined\": false, \"loop_desc\": \"\"}");

            assertThat(result.needRefined()).isFalse();
            assertThat(result.loopDesc()).isEqualTo("");
        }

        @Test
        void testCycleCheckerParseResultJsonWithCycle() {
            CycleChecker.CycleResult result =
                    CycleChecker.parseCycleResultJson("{\"need_refined\": true, \"loop_desc\": \"检测到环\"}");

            assertThat(result.needRefined()).isTrue();
            assertThat(result.loopDesc()).isEqualTo("检测到环");
        }
    }

    @Nested
    class TestWorkflowIntentionDetectorIntegration {

        @Test
        void testIntentionDetectorInitialization() {
            IntentionDetector detector = new IntentionDetector(new Object());

            assertThat(detector).isNotNull();
        }

        @Test
        void testIntentionDetectorHasRequiredMethods() {
            IntentionDetector detector = new IntentionDetector(new Object());

            assertThat(detector.getLlm()).isNotNull();
            assertThat(detector.detectInitialInstruction(List.of())).isFalse();
            assertThat(detector.detectRefineIntent(List.of(), "graph TD")).isFalse();
        }
    }

    @Nested
    class TestWorkflowBuilderComponents {

        @Test
        void testWorkflowBuilderHasRequiredComponents() {
            WorkflowBuilder builder = new WorkflowBuilder(new Object(), new HistoryManager());

            assertThat(builder.getIntentionDetector()).isNotNull();
            assertThat(builder.getWorkflowDesigner()).isNotNull();
            assertThat(builder.getDlGenerator()).isNotNull();
            assertThat(builder.getDlReflector()).isNotNull();
            assertThat(builder.getDlTransformer()).isNotNull();
            assertThat(builder.getCycleChecker()).isNotNull();
        }

        @Test
        void testWorkflowBuilderInternalState() {
            WorkflowBuilder builder = new WorkflowBuilder(new Object(), new HistoryManager());

            assertThat(builder.getWorkflowName()).isNull();
            assertThat(builder.getWorkflowNameEn()).isNull();
            assertThat(builder.getWorkflowDesc()).isNull();
            assertThat(builder.getDl()).isNull();
            assertThat(builder.getMermaidCode()).isNull();
        }
    }

    @Nested
    class TestDLTransformerIntegration {

        @Test
        void testDlTransformerImport() {
            DlTransformer transformer = new DlTransformer();

            assertThat(transformer).isNotNull();
        }

        @Test
        void testDlTransformerHasRequiredMethods() {
            DlTransformer transformer = new DlTransformer();

            assertThat(transformer.transformToDsl("[{\"id\":\"start\",\"type\":\"Start\",\"next\":\"end\"},{\"id\":\"end\",\"type\":\"End\"}]"))
                    .isNotBlank();
            assertThat(DlTransformer.transformToMermaid("[{\"id\":\"start\",\"type\":\"Start\",\"next\":\"end\"},{\"id\":\"end\",\"type\":\"End\"}]"))
                    .contains("graph");
        }
    }

    @Nested
    class TestWorkflowDesignerIntegrationInner {

        @Test
        void testWorkflowDesignerImport() {
            WorkflowDesigner designer = new WorkflowDesigner(new Object());

            assertThat(designer).isNotNull();
        }

        @Test
        void testWorkflowDesignerHasRequiredMethods() {
            WorkflowDesigner designer = new WorkflowDesigner();

            assertThat(designer.design("query", "tools")).isNotNull();
        }
    }

    @Nested
    class TestDLGeneratorIntegration {

        @Test
        void testDlGeneratorImport() {
            DlGenerator generator = new DlGenerator(new Object());

            assertThat(generator).isNotNull();
        }

        @Test
        void testDlGeneratorHasRequiredMethods() {
            DlGenerator generator = new DlGenerator(null);

            assertThat(generator.generate("query", Map.of())).isNotNull();
            assertThat(generator.refine("query", Map.of(), "[]", "graph TD")).isNotNull();
        }
    }

    @Nested
    class TestWorkflowBuilderTypeIntegration {

        @Test
        void testWorkflowBuilderIsBaseAgentBuilder() {
            WorkflowBuilder builder = new WorkflowBuilder(new Object(), new HistoryManager());

            assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
        }

        @Test
        void testCycleCheckerGraphCycleHelpers() {
            assertThat(CycleChecker.hasCycle(Map.of("start", List.of("end"), "end", List.of()))).isFalse();
            assertThat(CycleChecker.hasCycle(Map.of("start", List.of("start")))).isTrue();
        }
    }
}
