/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test builder factory functionality.
 * <p>
 * Mirrors Python's {@code test_factory.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/test_factory.py}.
 */
class TestFactory {

    @BeforeEach
    void setUp() {
        AgentBuilderFactory.clearRegistry();
    }

    @AfterEach
    void tearDown() {
        AgentBuilderFactory.clearRegistry();
    }

    @Test
    void testCreateLlmAgentBuilder() {
        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.LLM_AGENT, new Object(), new HistoryManager());

        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
    }

    @Test
    void testCreateWorkflowBuilder() {
        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.WORKFLOW, new Object(), new HistoryManager());

        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
    }

    @Test
    void testCreateUnsupportedAgentType() {
        AgentBuilderFactory.clearRegistry();
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, StubLlmBuilder.class);

        assertThatThrownBy(() -> AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.WORKFLOW, new Object(), new HistoryManager()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported agent type");
    }

    @Test
    void testCreateInitializesBuildersDict() {
        AgentBuilderFactory.clearRegistry();

        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT, new Object(), new HistoryManager());

        Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> registered =
                AgentBuilderFactory.getRegisteredBuilders();
        assertThat(registered).containsKeys(AgentBuilderEnums.AgentType.LLM_AGENT, AgentBuilderEnums.AgentType.WORKFLOW);
    }

    @Test
    void testCreateReusesExistingBuildersDict() {
        AgentBuilderFactory.clearRegistry();

        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT, new Object(), new HistoryManager());
        Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> first =
                AgentBuilderFactory.getRegisteredBuilders();

        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.WORKFLOW, new Object(), new HistoryManager());
        Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> second =
                AgentBuilderFactory.getRegisteredBuilders();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void testRegisterValidBuilder() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, ValidBuilder.class);

        Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> registered =
                AgentBuilderFactory.getRegisteredBuilders();
        assertThat(registered).containsEntry(AgentBuilderEnums.AgentType.LLM_AGENT, ValidBuilder.class);
    }

    @Test
    void testRegisterInvalidBuilderRaisesTypeError() {
        assertThatThrownBy(() -> AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, InvalidBuilder.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must inherit from BaseAgentBuilder");
    }

    @Test
    void testRegisterOverwritesExistingBuilder() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, FirstBuilder.class);
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, SecondBuilder.class);

        assertThat(AgentBuilderFactory.getRegisteredBuilders())
                .containsEntry(AgentBuilderEnums.AgentType.LLM_AGENT, SecondBuilder.class);
    }

    @Test
    void testRegisterCustomAgentType() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, ValidBuilder.class);

        assertThat(AgentBuilderFactory.getRegisteredBuilders()).containsKey(AgentBuilderEnums.AgentType.LLM_AGENT);
    }

    @Test
    void testGetSupportedTypesEmpty() {
        assertThat(AgentBuilderFactory.getSupportedTypes()).isEmpty();
    }

    @Test
    void testGetSupportedTypesAfterCreate() {
        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT, new Object(), new HistoryManager());

        List<AgentBuilderEnums.AgentType> types = AgentBuilderFactory.getSupportedTypes();
        assertThat(types).contains(AgentBuilderEnums.AgentType.LLM_AGENT, AgentBuilderEnums.AgentType.WORKFLOW);
        assertThat(types).hasSize(2);
    }

    @Test
    void testGetSupportedTypesAfterRegister() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, ValidBuilder.class);

        List<AgentBuilderEnums.AgentType> types = AgentBuilderFactory.getSupportedTypes();
        assertThat(types).contains(AgentBuilderEnums.AgentType.LLM_AGENT);
        assertThat(types).hasSize(1);
    }

    @Test
    void testGetSupportedTypesReturnsCopy() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, ValidBuilder.class);

        List<AgentBuilderEnums.AgentType> first = AgentBuilderFactory.getSupportedTypes();
        List<AgentBuilderEnums.AgentType> second = AgentBuilderFactory.getSupportedTypes();

        assertThat(first).isNotSameAs(second);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void testRegisterThenCreate() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, CustomBuilder.class);

        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.LLM_AGENT, new Object(), new HistoryManager());

        assertThat(builder).isInstanceOf(CustomBuilder.class);
        assertThat(((CustomBuilder) builder).customInitialized).isTrue();
    }

    @Test
    void testMultipleRegistrations() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, FirstBuilder.class);
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.WORKFLOW, SecondBuilder.class);

        Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> registered =
                AgentBuilderFactory.getRegisteredBuilders();
        assertThat(registered).hasSize(2);
        assertThat(registered).containsEntry(AgentBuilderEnums.AgentType.LLM_AGENT, FirstBuilder.class);
        assertThat(registered).containsEntry(AgentBuilderEnums.AgentType.WORKFLOW, SecondBuilder.class);
    }

    private static class StubLlmBuilder extends BaseAgentBuilder {
        private StubLlmBuilder(Object llm, HistoryManager historyManager) {
            super(llm, historyManager, null);
        }

        @Override
        protected Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of();
        }

        @Override
        protected Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of();
        }
    }

    private static class ValidBuilder extends BaseAgentBuilder {
        private ValidBuilder(Object llm, HistoryManager historyManager) {
            super(llm, historyManager, null);
        }

        @Override
        protected Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of();
        }

        @Override
        protected Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of();
        }
    }

    private static final class InvalidBuilder {
    }

    private static class FirstBuilder extends ValidBuilder {
        private FirstBuilder(Object llm, HistoryManager historyManager) {
            super(llm, historyManager);
        }
    }

    private static class SecondBuilder extends ValidBuilder {
        private SecondBuilder(Object llm, HistoryManager historyManager) {
            super(llm, historyManager);
        }
    }

    private static class CustomBuilder extends BaseAgentBuilder {
        private final boolean customInitialized;

        private CustomBuilder(Object llm, HistoryManager historyManager) {
            super(llm, historyManager, null);
            this.customInitialized = true;
        }

        @Override
        protected Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of("result", "initial");
        }

        @Override
        protected Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of("result", "processing");
        }

        @Override
        protected Map<String, Object> handleCompleted(Map<String, Object> query, List<Map<String, Object>> history) {
            return Map.of("state", "custom");
        }
    }
}
