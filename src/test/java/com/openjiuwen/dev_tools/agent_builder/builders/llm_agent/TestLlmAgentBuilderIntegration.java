/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.dev_tools.agent_builder.builders.LlmAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for LLM agent builder integration.
 * <p>
 * Mirrors Python's {@code test_llm_agent_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.llm_agent}.
 */
class TestLlmAgentBuilderIntegration {

    @Nested
    class TestLlmAgentBuilderIntegrationInner {

        @Test
        void llmAgentBuilderInitialization() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }

        @Test
        void llmAgentBuilderComponentsInitialization() {
            assertThat(new Clarifier(null)).isNotNull();
            assertThat(new Generator(null)).isNotNull();
            assertThat(new IntentionDetector(null)).isNotNull();
            assertThat(new Transformer()).isNotNull();
        }

        @Test
        void llmAgentBuilderStateStartsInitial() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }

        @Test
        void llmAgentBuilderIsNotWorkflow() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);
            assertThat(builder).isNotNull();
            assertThat(builder.getClass().getSimpleName()).isEqualTo("LlmAgentBuilder");
        }

        @Test
        void llmAgentBuilderReset() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);
            builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
            builder.reset();
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        }
    }

    @Nested
    class TestClarifierIntegration {

        @Test
        void clarifierInitialization() {
            assertThat(new Clarifier(null)).isNotNull();
        }

        @Test
        void clarifierHasRequiredMethods() {
            assertThat(Clarifier.class.getDeclaredMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .contains("clarify");
        }
    }

    @Nested
    class TestGeneratorIntegration {

        @Test
        void generatorInitialization() {
            assertThat(new Generator(null)).isNotNull();
        }

        @Test
        void generatorHasRequiredMethods() {
            assertThat(Generator.class.getDeclaredMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .contains("generate", "parseInfo");
        }
    }

    @Nested
    class TestIntentionDetectorIntegration {

        @Test
        void intentionDetectorInitialization() {
            assertThat(new IntentionDetector(null)).isNotNull();
        }

        @Test
        void intentionDetectorHasRequiredMethods() {
            assertThat(IntentionDetector.class.getDeclaredMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .contains("detectRefineIntent");
        }
    }

    @Nested
    class TestTransformerIntegration {

        @Test
        void transformerInitialization() {
            assertThat(new Transformer()).isNotNull();
        }

        @Test
        void transformerTransformMethod() {
            assertThat(Transformer.class.getDeclaredMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .contains("transformToDsl");
        }
    }

    @Nested
    class TestLlmAgentBuilderWorkflow {

        @Test
        void builderGetBuildStatus() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);
            assertThat(builder.getBuildStatus()).containsEntry("state", AgentBuilderEnums.BuildState.INITIAL.getValue());
        }

        @Test
        void builderWithProgressReporter() {
            LlmAgentBuilder builder = new LlmAgentBuilder(new ProgressReporter());
            assertThat(builder.getProgressReporter()).isNotNull();
        }

        @Test
        void builderTransitionsToProcessingOnInitialBuild() {
            LlmAgentBuilder builder = new LlmAgentBuilder(null);
            Map<String, Object> result = builder.build(Map.of("query", "create llm agent"), List.of());

            assertThat(result).containsEntry("status", "clarifying");
            assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
        }
    }
}
