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
 * Mirrors Python's {@code tests.unit_tests.dev_tools.agent_builder.executor.test_executor} in
 * {@code tests/unit_tests/dev_tools/agent_builder/executor/test_executor.py}.
 *
 * <p>Also mirrors Python's {@code tests.system_tests.dev_tools.agent_builder.executor.test_executor_integration}
 * in {@code tests/system_tests/dev_tools/agent_builder/executor/test_executor_integration.py}.</p>
 */
class AgentBuilderExecutorMissingTest {

    @BeforeEach
    void setUp() {
        AgentBuilderFactory.clearRegistry();
        AgentBuilderFactory.register(AgentBuilderEnums.AgentType.LLM_AGENT, RecordingBuilder.class);
        Model.registerInvoker("OpenAI", (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
    }

    @AfterEach
    void tearDown() {
        AgentBuilderFactory.clearRegistry();
        Model.unregisterInvoker("OpenAI");
    }

    @Test
    void createModelWithValidInfo() {
        Model model = AgentBuilderExecutor.createCoreModel(validModelInfo());

        assertThat(model).isNotNull();
        assertThat(model.getModelClientConfig().getClientProvider()).isEqualTo("OpenAI");
        assertThat(model.getModelClientConfig().getClientId()).isEqualTo("gpt-4");
    }

    @Test
    void createModelMissingProviderRaisesValidationError() {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("model_name", "gpt-4");
        modelInfo.put("api_key", "test_key");

        assertThatThrownBy(() -> AgentBuilderExecutor.createCoreModel(modelInfo))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void createModelMissingModelNameRaisesValidationError() {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("model_provider", "openai");
        modelInfo.put("api_key", "test_key");

        assertThatThrownBy(() -> AgentBuilderExecutor.createCoreModel(modelInfo))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void createModelMissingApiKeyRaisesValidationError() {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("model_provider", "openai");
        modelInfo.put("model_name", "gpt-4");

        assertThatThrownBy(() -> AgentBuilderExecutor.createCoreModel(modelInfo))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void createModelEmptyInfoRaisesValidationError() {
        assertThatThrownBy(() -> AgentBuilderExecutor.createCoreModel(Map.of()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void createModelNoneInfoRaisesValidationError() {
        assertThatThrownBy(() -> AgentBuilderExecutor.createCoreModel((Map<String, Object>) null))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void createModelProviderMapping() {
        Model model = AgentBuilderExecutor.createCoreModel(validModelInfo());

        assertThat(model).isNotNull();
        assertThat(model.getModelClientConfig().getClientProvider()).isEqualTo("OpenAI");
    }

    @Test
    void createModelWithOptionalParams() {
        Map<String, Object> modelInfo = validModelInfo();
        modelInfo.put("temperature", 0.7);
        modelInfo.put("max_tokens", 1000);
        modelInfo.put("top_p", 0.9);

        Model model = AgentBuilderExecutor.createCoreModel(modelInfo);

        assertThat(model).isNotNull();
        assertThat(model.getModelConfig().getTemperature()).isEqualTo(0.7);
        assertThat(model.getModelConfig().getMaxTokens()).isEqualTo(1000);
        assertThat(model.getModelConfig().getTopP()).isEqualTo(0.9);
    }

    @Test
    void executorCreationPreservesConstructorInputs() {
        Map<String, HistoryManager> historyManagerMap = new LinkedHashMap<>();

        AgentBuilderExecutor executor = newExecutor("test query", "session_001", historyManagerMap, false);

        assertThat(executor.getQuery()).isEqualTo("test query");
        assertThat(executor.getSessionId()).isEqualTo("session_001");
        assertThat(executor.getAgentType()).isEqualTo("llm_agent");
        assertThat(executor.getProgressReporter()).isNull();
    }

    @Test
    void executorCreatesHistoryManager() {
        Map<String, HistoryManager> historyManagerMap = new LinkedHashMap<>();

        AgentBuilderExecutor executor = newExecutor("test query", "session_001", historyManagerMap, false);

        assertThat(historyManagerMap).containsKey("session_001");
        assertThat(executor.getHistoryManager()).isSameAs(historyManagerMap.get("session_001"));
    }

    @Test
    void executorReusesHistoryManager() {
        Map<String, HistoryManager> historyManagerMap = new LinkedHashMap<>();

        AgentBuilderExecutor first = newExecutor("query 1", "session_001", historyManagerMap, false);
        AgentBuilderExecutor second = newExecutor("query 2", "session_001", historyManagerMap, false);

        assertThat(second.getHistoryManager()).isSameAs(first.getHistoryManager());
    }

    @Test
    void executorWithProgressEnabled() {
        Map<String, HistoryManager> historyManagerMap = new LinkedHashMap<>();

        AgentBuilderExecutor executor = newExecutor("test query", "session_001", historyManagerMap, true);

        assertThat(executor.getProgressReporter()).isNotNull();
    }

    @Test
    void executorInvalidAgentTypeRaisesValidationError() {
        assertThatThrownBy(() -> new AgentBuilderExecutor(
                "test query",
                "session_001",
                "invalid_type",
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                validModelInfo(),
                false))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void getBuildStatus() {
        AgentBuilderExecutor executor = newExecutor("test query", "session_001", new LinkedHashMap<>(), false);

        Map<String, Object> status = executor.getBuildStatus();

        assertThat(status.get("session_id")).isEqualTo("session_001");
        assertThat(status.get("agent_type")).isEqualTo("llm_agent");
        assertThat(status).containsKey("state");
    }

    @Test
    void getHistoryManagerStaticCreatesManager() {
        Map<String, HistoryManager> historyManagerMap = new LinkedHashMap<>();

        HistoryManager manager = AgentBuilderExecutor.getHistoryManager("session_001", historyManagerMap);

        assertThat(manager).isNotNull();
        assertThat(historyManagerMap).containsEntry("session_001", manager);
    }

    @Test
    void getHistoryManagerStaticReusesManager() {
        Map<String, HistoryManager> historyManagerMap = new LinkedHashMap<>();

        HistoryManager first = AgentBuilderExecutor.getHistoryManager("session_001", historyManagerMap);
        HistoryManager second = AgentBuilderExecutor.getHistoryManager("session_001", historyManagerMap);

        assertThat(second).isSameAs(first);
    }

    @Test
    void executeAddsUserMessage() {
        AgentBuilderExecutor executor = newExecutor("test query", "session_001", new LinkedHashMap<>(), false);

        executor.execute();

        List<Map<String, String>> history = executor.getHistoryManager().getHistory();
        assertThat(history).containsExactly(Map.of("role", "user", "content", "test query"));
    }

    @Test
    void executeReturnsResult() {
        AgentBuilderExecutor executor = newExecutor("test query", "session_001", new LinkedHashMap<>(), false);

        Object result = executor.execute();

        assertThat(result).isEqualTo("test result");
    }

    private static AgentBuilderExecutor newExecutor(
            String query,
            String sessionId,
            Map<String, HistoryManager> historyManagerMap,
            boolean enableProgress) {
        return new AgentBuilderExecutor(
                query,
                sessionId,
                "llm_agent",
                historyManagerMap,
                new LinkedHashMap<>(),
                validModelInfo(),
                enableProgress);
    }

    private static Map<String, Object> validModelInfo() {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("model_provider", "openai");
        modelInfo.put("model_name", "gpt-4");
        modelInfo.put("api_key", "test_key");
        modelInfo.put("api_base", "https://api.openai.com");
        modelInfo.put("temperature", 0.7);
        modelInfo.put("top_p", 0.9);
        return modelInfo;
    }

    /**
     * Mirrors Python's mocked builder in
     * {@code tests/unit_tests/dev_tools/agent_builder/executor/test_executor.py}.
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
            return "test result";
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
