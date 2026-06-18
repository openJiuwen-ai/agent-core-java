/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.dev_tools.agent_builder.builders.llm_agent.LlmAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.WorkflowBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for the agent builder factory registry.
 *
 * <p>Mirrors Python's {@code AgentBuilderFactory} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/factory.py}.</p>
 */
class AgentBuilderFactoryTest {

    @BeforeEach
    void setUp() {
        AgentBuilderFactory.clearRegistry();
    }

    @AfterEach
    void tearDown() {
        AgentBuilderFactory.clearRegistry();
    }

    @Test
    void createLazilyRegistersDefaultBuildersAndInstantiatesByAgentType() {
        Model llm = testModel();
        HistoryManager historyManager = new HistoryManager();

        BaseAgentBuilder llmBuilder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.LLM_AGENT,
                llm,
                historyManager);
        BaseAgentBuilder workflowBuilder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.WORKFLOW,
                llm,
                historyManager);

        assertThat(llmBuilder).isInstanceOf(LlmAgentBuilder.class);
        assertThat(workflowBuilder).isInstanceOf(WorkflowBuilder.class);
        assertThat(llmBuilder.getLlm()).isSameAs(llm);
        assertThat(llmBuilder.getHistoryManager()).isSameAs(historyManager);
        assertThat(AgentBuilderFactory.getSupportedTypes())
                .containsExactly(AgentBuilderEnums.AgentType.LLM_AGENT, AgentBuilderEnums.AgentType.WORKFLOW);
    }

    @Test
    void createLlmAgentBuilderReturnsBaseAgentBuilder() {
        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.LLM_AGENT,
                testModel(),
                new HistoryManager());

        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
        assertThat(builder).isInstanceOf(LlmAgentBuilder.class);
    }

    @Test
    void createWorkflowBuilderReturnsBaseAgentBuilder() {
        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.WORKFLOW,
                testModel(),
                new HistoryManager());

        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(BaseAgentBuilder.class);
        assertThat(builder).isInstanceOf(WorkflowBuilder.class);
    }

    @Test
    void createRejectsUnregisteredSupportedAgentType() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);

        assertThatThrownBy(() -> AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.WORKFLOW,
                testModel(),
                new HistoryManager()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported agent type");
    }

    @Test
    void createInitializesDefaultBuilderRegistry() {
        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT, testModel(), new HistoryManager());

        Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> registered =
                AgentBuilderFactory.getRegisteredBuilders();

        assertThat(registered)
                .containsEntry(AgentBuilderEnums.AgentType.LLM_AGENT, LlmAgentBuilder.class)
                .containsEntry(AgentBuilderEnums.AgentType.WORKFLOW, WorkflowBuilder.class);
    }

    @Test
    void createReusesExistingBuilderRegistry() {
        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT, testModel(), new HistoryManager());
        Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> snapshotAfterFirst =
                AgentBuilderFactory.getRegisteredBuilders();

        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.WORKFLOW, testModel(), new HistoryManager());
        Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> snapshotAfterSecond =
                AgentBuilderFactory.getRegisteredBuilders();

        assertThat(snapshotAfterSecond).isEqualTo(snapshotAfterFirst);
    }

    @Test
    void registerAddsCustomBuilderAndGettersReturnCopies() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);

        List<AgentBuilderEnums.AgentType> supported = AgentBuilderFactory.getSupportedTypes();
        Map<AgentBuilderEnums.AgentType, Class<? extends BaseAgentBuilder>> registered =
                AgentBuilderFactory.getRegisteredBuilders();
        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.LLM_AGENT,
                testModel(),
                new HistoryManager());

        assertThat(builder).isInstanceOf(TestBuilder.class);
        assertThat(supported).containsExactly(AgentBuilderEnums.AgentType.LLM_AGENT);
        assertThat(registered).containsEntry(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);

        registered.clear();
        assertThat(AgentBuilderFactory.getRegisteredBuilders())
                .containsEntry(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);
    }

    @Test
    void registerStoresValidBuilderClass() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);

        assertThat(AgentBuilderFactory.getRegisteredBuilders())
                .containsEntry(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);
    }

    @Test
    void registerRejectsClassesThatDoNotInheritBaseAgentBuilder() {
        assertThatThrownBy(() -> AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BaseAgentBuilder");
    }

    @Test
    void registerOverwritesExistingBuilderClass() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, SecondTestBuilder.class);

        assertThat(AgentBuilderFactory.getRegisteredBuilders())
                .containsEntry(AgentBuilderEnums.AgentType.LLM_AGENT, SecondTestBuilder.class);
    }

    @Test
    void registerAllowsAnyDeclaredAgentType() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.WORKFLOW, TestBuilder.class);

        assertThat(AgentBuilderFactory.getRegisteredBuilders())
                .containsEntry(AgentBuilderEnums.AgentType.WORKFLOW, TestBuilder.class);
    }

    @Test
    void getSupportedTypesReturnsEmptyListBeforeInitialization() {
        assertThat(AgentBuilderFactory.getSupportedTypes()).isEmpty();
    }

    @Test
    void getSupportedTypesAfterCreateIncludesDefaults() {
        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT, testModel(), new HistoryManager());

        assertThat(AgentBuilderFactory.getSupportedTypes())
                .containsExactly(AgentBuilderEnums.AgentType.LLM_AGENT, AgentBuilderEnums.AgentType.WORKFLOW);
    }

    @Test
    void getSupportedTypesAfterRegisterIncludesRegisteredOnly() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);

        assertThat(AgentBuilderFactory.getSupportedTypes())
                .containsExactly(AgentBuilderEnums.AgentType.LLM_AGENT);
    }

    @Test
    void getSupportedTypesReturnsIndependentSnapshots() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);

        List<AgentBuilderEnums.AgentType> first = AgentBuilderFactory.getSupportedTypes();
        List<AgentBuilderEnums.AgentType> second = AgentBuilderFactory.getSupportedTypes();

        assertThat(first).isNotSameAs(second);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void createRejectsUnsupportedNullAgentType() {
        assertThatThrownBy(() -> AgentBuilderFactory.create(null, testModel(), new HistoryManager()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported agent type");
    }

    @Test
    void clearRegistryRemovesExistingRegistrationsUntilNextCreate() {
        AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT, testModel(), new HistoryManager());
        assertThat(AgentBuilderFactory.getSupportedTypes()).isNotEmpty();

        AgentBuilderFactory.clearRegistry();

        assertThat(AgentBuilderFactory.getSupportedTypes()).isEmpty();
    }

    @Test
    void registerThenCreateReturnsCustomBuilder() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);

        BaseAgentBuilder builder = AgentBuilderFactory.create(
                AgentBuilderEnums.AgentType.LLM_AGENT,
                testModel(),
                new HistoryManager());

        assertThat(builder).isInstanceOf(TestBuilder.class);
    }

    @Test
    void multipleRegistrationsPreserveAllAgentTypeMappings() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class);
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.WORKFLOW, SecondTestBuilder.class);

        assertThat(AgentBuilderFactory.getRegisteredBuilders())
                .containsEntry(AgentBuilderEnums.AgentType.LLM_AGENT, TestBuilder.class)
                .containsEntry(AgentBuilderEnums.AgentType.WORKFLOW, SecondTestBuilder.class);
    }

    private static Model testModel() {
        return new Model((messages, modelConfig, modelClientConfig, options) -> CompletableFuture.completedFuture(null));
    }

    /**
     * Test builder used for custom registry parity.
     *
     * <p>Mirrors Python's registered {@code Type[BaseAgentBuilder]} in
     * {@code openjiuwen/dev_tools/agent_builder/builders/factory.py}.</p>
     */
    public static final class TestBuilder extends BaseAgentBuilder {
        public TestBuilder(Model llm, HistoryManager historyManager) {
            super(llm, historyManager);
        }

        @Override
        protected Object handleInitial(String query, List<Map<String, String>> dialogHistory) {
            return "initial";
        }

        @Override
        protected Object handleProcessing(String query, List<Map<String, String>> dialogHistory) {
            return "processing";
        }

        @Override
        protected Object handleCompleted(String query, List<Map<String, String>> dialogHistory) {
            return "completed";
        }

        @Override
        protected void resetInternalState() {
        }

        @Override
        protected boolean isWorkflowBuilderInternal() {
            return false;
        }
    }

    /**
     * Second test builder used for overwrite and multiple-registration parity.
     *
     * <p>Mirrors Python's registered {@code Type[BaseAgentBuilder]} in
     * {@code openjiuwen/dev_tools/agent_builder/builders/factory.py}.</p>
     */
    public static final class SecondTestBuilder extends BaseAgentBuilder {
        public SecondTestBuilder(Model llm, HistoryManager historyManager) {
            super(llm, historyManager);
        }

        @Override
        protected Object handleInitial(String query, List<Map<String, String>> dialogHistory) {
            return "second-initial";
        }

        @Override
        protected Object handleProcessing(String query, List<Map<String, String>> dialogHistory) {
            return "second-processing";
        }

        @Override
        protected Object handleCompleted(String query, List<Map<String, String>> dialogHistory) {
            return "second-completed";
        }

        @Override
        protected void resetInternalState() {
        }

        @Override
        protected boolean isWorkflowBuilderInternal() {
            return false;
        }
    }
}
