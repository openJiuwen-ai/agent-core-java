/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.dev_tools.agent_builder.builders.AgentBuilderFactory;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code AgentBuilderExecutor} in
 * {@code openjiuwen/dev_tools/agent_builder/executor/executor.py}.
 */
class AgentBuilderExecutorTest {

    @BeforeEach
    void setUp() {
        AgentBuilderFactory.clearRegistry();
        Model.registerInvoker("OpenAI", (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        Model.registerInvoker("DashScope", (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
    }

    @AfterEach
    void tearDown() {
        AgentBuilderFactory.clearRegistry();
        Model.unregisterInvoker("OpenAI");
        Model.unregisterInvoker("DashScope");
    }

    @Test
    void createCoreModelMapsPythonAliasesAndProviderNames() {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("model_provider", "openai");
        modelInfo.put("model_name", "gpt-test");
        modelInfo.put("api_key", "key");
        modelInfo.put("api_base", "");
        modelInfo.put("verify_ssl", true);
        modelInfo.put("temperature", 0.2);
        modelInfo.put("max_tokens", 512);
        modelInfo.put("top_p", 0.8);
        modelInfo.put("timeout", 30);

        Model model = AgentBuilderExecutor.createCoreModel(modelInfo);

        assertThat(model.getModelClientConfig().getClientProvider()).isEqualTo("OpenAI");
        assertThat(model.getModelClientConfig().getClientId()).isEqualTo("gpt-test");
        assertThat(model.getModelClientConfig().getApiKey()).isEqualTo("key");
        assertThat(model.getModelClientConfig().getApiBase()).isEmpty();
        assertThat(model.getModelClientConfig().isVerifySsl()).isTrue();
        assertThat(model.getModelConfig().getModelName()).isEqualTo("gpt-test");
        assertThat(model.getModelConfig().getTemperature()).isEqualTo(0.2);
        assertThat(model.getModelConfig().getTopP()).isEqualTo(0.8);
        assertThat(model.getModelConfig().getMaxTokens()).isEqualTo(512);
        assertThat(model.getModelConfig().getExtraFields()).containsEntry("timeout", 30.0);
    }

    @Test
    void createCoreModelAcceptsClientProviderAndModelFallbackAliases() {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("client_provider", "DashScope");
        modelInfo.put("model", "qwen-plus");
        modelInfo.put("api_key", "key");

        Model model = AgentBuilderExecutor.createCoreModel(modelInfo);

        assertThat(model.getModelClientConfig().getClientProvider()).isEqualTo("DashScope");
        assertThat(model.getModelClientConfig().getClientId()).isEqualTo("qwen-plus");
        assertThat(model.getModelConfig().getModelName()).isEqualTo("qwen-plus");
        assertThat(model.getModelClientConfig().isVerifySsl()).isFalse();
    }

    @Test
    void createCoreModelRaisesValidationErrorWhenRequiredFieldsMissing() {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("model_provider", "openai");

        assertThatThrownBy(() -> AgentBuilderExecutor.createCoreModel(modelInfo))
                .isInstanceOf(ValidationError.class)
                .hasMessage("model_info missing required fields")
                .satisfies(error -> {
                    ValidationError validationError = (ValidationError) error;
                    assertThat(validationError.getStatus().name()).isEqualTo("COMPONENT_LLM_CONFIG_INVALID");
                    Map<?, ?> details = (Map<?, ?>) validationError.getDetails();
                    assertThat(details.get("got_keys")).isEqualTo(List.of("model_provider"));
                });
    }

    @Test
    void getHistoryManagerCreatesAndReusesBySessionId() {
        Map<String, HistoryManager> managers = new LinkedHashMap<>();

        HistoryManager first = AgentBuilderExecutor.getHistoryManager("session-1", managers);
        HistoryManager second = AgentBuilderExecutor.getHistoryManager("session-1", managers);

        assertThat(second).isSameAs(first);
        assertThat(managers).containsEntry("session-1", first);
    }

    @Test
    void constructorCreatesHistoryProgressAndBuilderWhenSessionIsNew() {
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, RecordingBuilder.class);
        Map<String, HistoryManager> histories = new LinkedHashMap<>();
        Map<String, BaseAgentBuilder> builders = new LinkedHashMap<>();

        AgentBuilderExecutor executor = new AgentBuilderExecutor(
                "build it",
                "session-1",
                "llm_agent",
                histories,
                builders,
                validModelInfo(),
                true);

        assertThat(executor.getHistoryManager()).isSameAs(histories.get("session-1"));
        assertThat(executor.getAgentBuilder()).isSameAs(builders.get("session-1"));
        assertThat(executor.getAgentBuilder()).isInstanceOf(RecordingBuilder.class);
        assertThat(executor.getProgressReporter()).isNotNull();
        assertThat(executor.getAgentBuilder().getProgressReporter()).isSameAs(executor.getProgressReporter());
    }

    @Test
    void constructorReusesExistingHistoryAndBuilder() {
        Map<String, HistoryManager> histories = new LinkedHashMap<>();
        Map<String, BaseAgentBuilder> builders = new LinkedHashMap<>();
        HistoryManager historyManager = new HistoryManager();
        RecordingBuilder builder = new RecordingBuilder(testModel(), historyManager);
        histories.put("session-1", historyManager);
        builders.put("session-1", builder);

        AgentBuilderExecutor executor = new AgentBuilderExecutor(
                "build it",
                "session-1",
                "llm_agent",
                histories,
                builders,
                validModelInfo(),
                true);

        assertThat(executor.getHistoryManager()).isSameAs(historyManager);
        assertThat(executor.getAgentBuilder()).isSameAs(builder);
    }

    @Test
    void constructorRejectsUnsupportedAgentTypeWithValidationError() {
        assertThatThrownBy(() -> new AgentBuilderExecutor(
                "build it",
                "session-1",
                "unknown",
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                validModelInfo(),
                false))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("Unsupported agent type: unknown");
    }

    @Test
    void executeAddsUserMessageDelegatesBuilderAndReturnsResult() {
        Map<String, HistoryManager> histories = new LinkedHashMap<>();
        Map<String, BaseAgentBuilder> builders = new LinkedHashMap<>();
        HistoryManager historyManager = new HistoryManager();
        RecordingBuilder builder = new RecordingBuilder(testModel(), historyManager);
        histories.put("session-1", historyManager);
        builders.put("session-1", builder);
        AgentBuilderExecutor executor = new AgentBuilderExecutor(
                "build it",
                "session-1",
                "llm_agent",
                histories,
                builders,
                validModelInfo(),
                false);

        Object result = executor.execute();

        assertThat(result).isEqualTo("executed:build it");
        assertThat(builder.getLastQuery()).isEqualTo("build it");
        assertThat(historyManager.getHistory()).containsExactly(Map.of("role", "user", "content", "build it"));
    }

    @Test
    void getBuildStatusMergesExecutorIdentityWithBuilderStatus() {
        Map<String, HistoryManager> histories = new LinkedHashMap<>();
        Map<String, BaseAgentBuilder> builders = new LinkedHashMap<>();
        HistoryManager historyManager = new HistoryManager();
        RecordingBuilder builder = new RecordingBuilder(testModel(), historyManager);
        histories.put("session-1", historyManager);
        builders.put("session-1", builder);
        AgentBuilderExecutor executor = new AgentBuilderExecutor(
                "build it",
                "session-1",
                "llm_agent",
                histories,
                builders,
                validModelInfo(),
                false);

        Map<String, Object> status = executor.getBuildStatus();

        assertThat(status)
                .containsEntry("session_id", "session-1")
                .containsEntry("agent_type", "llm_agent")
                .containsEntry("state", "initial");
        assertThat(status).containsKey("resource_count");
    }

    private static AgentBuilderExecutor.ModelInfo validModelInfo() {
        return AgentBuilderExecutor.ModelInfo.of("OpenAI", "gpt-test", "key");
    }

    private static Model testModel() {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
    }

    /**
     * Test builder used to isolate executor orchestration.
     *
     * <p>Mirrors Python's builder object stored in {@code agent_builder_map} in
     * {@code openjiuwen/dev_tools/agent_builder/executor/executor.py}.</p>
     */
    public static final class RecordingBuilder extends BaseAgentBuilder {
        private String lastQuery;

        public RecordingBuilder(Model llm, HistoryManager historyManager) {
            super(llm, historyManager);
        }

        public String getLastQuery() {
            return lastQuery;
        }

        @Override
        public Object execute(String query) {
            lastQuery = query;
            return "executed:" + query;
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
}
