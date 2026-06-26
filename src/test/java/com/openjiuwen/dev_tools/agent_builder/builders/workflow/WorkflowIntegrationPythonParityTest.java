/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DLTransformer;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.WorkflowDesigner;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's workflow integration tests in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_workflow_integration.py}.
 */
class WorkflowIntegrationPythonParityTest {

    @Test
    void testWorkflowBuilderInitialization() {
        Model mockLlm = fakeModel();
        HistoryManager historyManager = new HistoryManager();

        WorkflowBuilder builder = new WorkflowBuilder(mockLlm, historyManager);

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getLlm()).isSameAs(mockLlm);
        assertThat(builder.getHistoryManager()).isSameAs(historyManager);
    }

    @Test
    void testWorkflowBuilderIsWorkflow() {
        WorkflowBuilder builder = new WorkflowBuilder(fakeModel(), new HistoryManager());

        assertThat(builder.isWorkflowBuilder()).isTrue();
    }

    @Test
    void testWorkflowBuilderReset() {
        WorkflowBuilder builder = new WorkflowBuilder(fakeModel(), new HistoryManager());

        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
        builder.reset();

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
    }

    @Test
    void testWorkflowBuilderGetBuildStatus() {
        WorkflowBuilder builder = new WorkflowBuilder(fakeModel(), new HistoryManager());

        Map<String, Object> status = builder.getBuildStatus();

        assertThat(status).containsKey("state");
        assertThat(status).containsEntry("state", AgentBuilderEnums.BuildState.INITIAL.getValue());
    }

    @Test
    void testCycleCheckerInitialization() {
        CycleChecker checker = new CycleChecker(fakeModel());

        assertThat(checker).isNotNull();
    }

    @Test
    void testCycleCheckerHasRequiredMethods() throws NoSuchMethodException {
        CycleChecker checker = new CycleChecker(fakeModel());

        assertThat(checker).isNotNull();
        assertThat(CycleChecker.class.getDeclaredMethod("checkAndParse", String.class)).isNotNull();
        assertThat(CycleChecker.class.getDeclaredMethod("checkMermaidCycle", String.class)).isNotNull();
        assertThat(CycleChecker.class.getDeclaredMethod("parseCycleResultJson", String.class)).isNotNull();
    }

    @Test
    void testCycleCheckerParseResultJson() {
        CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(
                "{\"need_refined\": false, \"loop_desc\": \"\"}");

        assertThat(result).isEqualTo(new CycleChecker.CycleResult(false, ""));
    }

    @Test
    void testCycleCheckerParseResultJsonWithCycle() {
        CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(
                "{\"need_refined\": true, \"loop_desc\": \"cycle detected\"}");

        assertThat(result).isEqualTo(new CycleChecker.CycleResult(true, "cycle detected"));
    }

    @Test
    void testIntentionDetectorInitialization() {
        IntentionDetector detector = new IntentionDetector(fakeModel());

        assertThat(detector).isNotNull();
    }

    @Test
    void testIntentionDetectorHasRequiredMethods() throws NoSuchMethodException {
        IntentionDetector detector = new IntentionDetector(fakeModel());

        assertThat(detector).isNotNull();
        assertThat(IntentionDetector.class.getDeclaredMethod("detectInitialInstruction", java.util.List.class))
                .isNotNull();
        assertThat(IntentionDetector.class.getDeclaredMethod("detectRefineIntent", java.util.List.class, String.class))
                .isNotNull();
    }

    @Test
    void testWorkflowBuilderHasRequiredComponents() throws ReflectiveOperationException {
        WorkflowBuilder builder = new WorkflowBuilder(fakeModel(), new HistoryManager());

        assertThat(fieldValue(builder, "intentionDetector")).isInstanceOf(IntentionDetector.class);
        assertThat(fieldValue(builder, "workflowDesigner")).isInstanceOf(WorkflowDesigner.class);
        assertThat(fieldValue(builder, "dlGenerator")).isInstanceOf(DLGenerator.class);
        assertThat(fieldValue(builder, "dlReflector")).isInstanceOf(Reflector.class);
        assertThat(fieldValue(builder, "dlTransformer")).isInstanceOf(DLTransformer.class);
        assertThat(fieldValue(builder, "cycleChecker")).isInstanceOf(CycleChecker.class);
    }

    @Test
    void testWorkflowBuilderInternalState() {
        WorkflowBuilder builder = new WorkflowBuilder(fakeModel(), new HistoryManager());

        assertThat(builder.getWorkflowName()).isNull();
        assertThat(builder.getWorkflowNameEn()).isNull();
        assertThat(builder.getWorkflowDesc()).isNull();
        assertThat(builder.getDl()).isNull();
        assertThat(builder.getMermaidCode()).isNull();
    }

    @Test
    void testDlTransformerImport() {
        DLTransformer transformer = new DLTransformer();

        assertThat(transformer).isNotNull();
    }

    @Test
    void testDlTransformerHasRequiredMethods() throws NoSuchMethodException {
        DLTransformer transformer = new DLTransformer();

        assertThat(transformer).isNotNull();
        assertThat(DLTransformer.class.getDeclaredMethod("transformToDsl", String.class)).isNotNull();
        assertThat(DLTransformer.class.getDeclaredMethod("transformToMermaid", String.class)).isNotNull();
    }

    @Test
    void testWorkflowDesignerImport() {
        WorkflowDesigner designer = new WorkflowDesigner(fakeModel());

        assertThat(designer).isNotNull();
    }

    @Test
    void testWorkflowDesignerHasRequiredMethods() throws NoSuchMethodException {
        WorkflowDesigner designer = new WorkflowDesigner(fakeModel());

        assertThat(designer).isNotNull();
        assertThat(WorkflowDesigner.class.getDeclaredMethod("design", String.class, String.class)).isNotNull();
    }

    @Test
    void testDlGeneratorImport() {
        DLGenerator generator = new DLGenerator(fakeModel());

        assertThat(generator).isNotNull();
    }

    @Test
    void testDlGeneratorHasRequiredMethods() throws NoSuchMethodException {
        DLGenerator generator = new DLGenerator(fakeModel());

        assertThat(generator).isNotNull();
        assertThat(DLGenerator.class.getDeclaredMethod("generate", String.class, Map.class)).isNotNull();
        assertThat(DLGenerator.class.getDeclaredMethod("refine", String.class, Map.class, String.class, String.class))
                .isNotNull();
    }

    private static Model fakeModel() {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(
                        "{\"need_refined\": false, \"loop_desc\": \"\"}")));
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
