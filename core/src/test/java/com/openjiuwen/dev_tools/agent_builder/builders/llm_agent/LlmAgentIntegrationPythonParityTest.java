/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's LLM-agent builder integration tests in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/llm_agent/test_llm_agent_integration.py}.
 */
class LlmAgentIntegrationPythonParityTest {

    @Test
    void testLlmAgentBuilderInitialization() {
        Model mockLlm = fakeModel();
        HistoryManager historyManager = new HistoryManager();

        LlmAgentBuilder builder = new LlmAgentBuilder(mockLlm, historyManager);

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getLlm()).isSameAs(mockLlm);
        assertThat(builder.getHistoryManager()).isSameAs(historyManager);
    }

    @Test
    void testLlmAgentBuilderComponentsInitialization() throws ReflectiveOperationException {
        LlmAgentBuilder builder = new LlmAgentBuilder(fakeModel(), new HistoryManager());

        assertThat(fieldValue(builder, "clarifier")).isInstanceOf(Clarifier.class);
        assertThat(fieldValue(builder, "generator")).isInstanceOf(Generator.class);
        assertThat(fieldValue(builder, "intentionDetector")).isInstanceOf(IntentionDetector.class);
        assertThat(fieldValue(builder, "transformer")).isInstanceOf(Transformer.class);
    }

    @Test
    void testLlmAgentBuilderIsNotWorkflow() {
        LlmAgentBuilder builder = new LlmAgentBuilder(fakeModel(), new HistoryManager());

        assertThat(builder.isWorkflowBuilder()).isFalse();
    }

    @Test
    void testLlmAgentBuilderReset() {
        LlmAgentBuilder builder = new LlmAgentBuilder(fakeModel(), new HistoryManager());

        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
        builder.reset();

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
    }

    @Test
    void testClarifierInitialization() {
        Clarifier clarifier = new Clarifier(fakeModel());

        assertThat(clarifier).isNotNull();
    }

    @Test
    void testClarifierHasRequiredMethods() throws NoSuchMethodException {
        Clarifier clarifier = new Clarifier(fakeModel());

        assertThat(clarifier).isNotNull();
        assertThat(Clarifier.class.getDeclaredMethod("clarify", String.class, Map.class)).isNotNull();
    }

    @Test
    void testGeneratorInitialization() {
        Generator generator = new Generator(fakeModel());

        assertThat(generator).isNotNull();
    }

    @Test
    void testGeneratorHasRequiredMethods() throws NoSuchMethodException {
        Generator generator = new Generator(fakeModel());

        assertThat(generator).isNotNull();
        assertThat(Generator.class.getDeclaredMethod("generate", String.class, String.class, String.class, Map.class))
                .isNotNull();
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
        assertThat(IntentionDetector.class.getDeclaredMethod("detectRefineIntent", String.class, String.class))
                .isNotNull();
    }

    @Test
    void testTransformerInitialization() {
        Transformer transformer = new Transformer();

        assertThat(transformer).isNotNull();
    }

    @Test
    void testTransformerTransformMethod() throws NoSuchMethodException {
        Transformer transformer = new Transformer();

        assertThat(transformer).isNotNull();
        assertThat(Transformer.class.getDeclaredMethod("transformToDsl", Map.class, Map.class)).isNotNull();
    }

    @Test
    void testBuilderGetBuildStatus() {
        LlmAgentBuilder builder = new LlmAgentBuilder(fakeModel(), new HistoryManager());

        Map<String, Object> status = builder.getBuildStatus();

        assertThat(status).containsKey("state");
        assertThat(status).containsEntry("state", AgentBuilderEnums.BuildState.INITIAL.getValue());
    }

    @Test
    void testBuilderWithProgressReporter() {
        ProgressReporter progressReporter = new ProgressReporter("test_session", "llm_agent");
        LlmAgentBuilder builder = new LlmAgentBuilder(fakeModel(), new HistoryManager());

        builder.setProgressReporter(progressReporter);

        assertThat(builder.getProgressReporter()).isSameAs(progressReporter);
    }

    private static Model fakeModel() {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("{}")));
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
