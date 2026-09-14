/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestAgentBuilder} in
 * {@code tests/unit_tests/dev_tools/agent_builder/test_main.py}.
 */
class AgentBuilderMainPythonParityTest {

    @Test
    void testBuilderCreation() {
        Map<String, Object> modelInfo = validModelInfo();
        AgentBuilder builder = new AgentBuilder(modelInfo);

        assertThat(builder.getModelInfo()).isSameAs(modelInfo);
        assertThat(builder.getHistoryManagerMap()).isEmpty();
        assertThat(builder.getAgentBuilderMap()).isEmpty();
    }

    @Test
    void testBuilderCreationWithEmptyModelInfo() {
        AgentBuilder builder = new AgentBuilder(Map.of());

        assertThat(builder.getModelInfo()).isEmpty();
        assertThat(builder.getHistoryManagerMap()).isEmpty();
        assertThat(builder.getAgentBuilderMap()).isEmpty();
    }

    @Test
    void testBuilderWithExistingMaps() {
        Map<String, HistoryManager> historyMap = new LinkedHashMap<>();
        Map<String, BaseAgentBuilder> builderMap = new LinkedHashMap<>();
        historyMap.put("session_001", new HistoryManager());

        AgentBuilder builder = new AgentBuilder(validModelInfo(), historyMap, builderMap, fakeFactory("{}", "completed"));

        assertThat(builder.getHistoryManagerMap()).isSameAs(historyMap);
        assertThat(builder.getAgentBuilderMap()).isSameAs(builderMap);
    }

    @Test
    void testBuildLlmAgent() {
        AgentBuilder builder = builderWith("{\"name\":\"Test Agent\"}", "completed");

        Map<String, Object> result = builder.buildLlmAgent("create assistant", "session_001");

        assertThat(result).containsEntry("session_id", "session_001");
        assertThat(result).containsEntry("agent_type", "llm_agent");
    }

    @Test
    void testBuildWorkflow() {
        AgentBuilder builder = builderWith("graph TD; A-->B", "processing");

        Map<String, Object> result = builder.buildWorkflow("create workflow", "session_001");

        assertThat(result).containsEntry("session_id", "session_001");
        assertThat(result).containsEntry("agent_type", "workflow");
        assertThat(result).containsEntry("mermaid_code", "graph TD; A-->B");
    }

    @Test
    void testBuildAgentWithDslResult() {
        AgentBuilder builder = builderWith("{\"agent_id\":\"123\",\"name\":\"Test\"}", "completed");

        Map<String, Object> result = builder.buildAgent("test query", "session_001", "llm_agent");

        assertThat(result).containsKey("dsl");
        @SuppressWarnings("unchecked")
        Map<String, Object> dsl = (Map<String, Object>) result.get("dsl");
        assertThat(dsl).containsEntry("agent_id", "123");
    }

    @Test
    void testBuildAgentWithStringResult() {
        AgentBuilder builder = builderWith("need more information", "initial");

        Map<String, Object> result = builder.buildAgent("test query", "session_001", "llm_agent");

        assertThat(result).containsEntry("response", "need more information");
        assertThat(result).containsEntry("status", "clarifying");
    }

    @Test
    void testBuildAgentWithDictResult() {
        AgentBuilder builder = builderWith(Map.of("key", "value", "nested", Map.of("a", 1)), "completed");

        Map<String, Object> result = builder.buildAgent("test query", "session_001", "llm_agent");

        assertThat(result).containsEntry("key", "value");
        assertThat(result.get("nested")).isEqualTo(Map.of("a", 1));
    }

    @Test
    void testGetSessionHistoryEmpty() {
        AgentBuilder builder = builderWith("{}", "completed");

        assertThat(builder.getSessionHistory("nonexistent_session")).isEmpty();
    }

    @Test
    void testGetSessionHistoryWithData() {
        AgentBuilder builder = builderWith("{}", "completed");
        HistoryManager manager = new HistoryManager();
        manager.addUserMessage("Hello");
        manager.addAssistantMessage("Hi!");
        builder.getHistoryManagerMap().put("session_001", manager);

        List<Map<String, String>> history = builder.getSessionHistory("session_001");

        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("role", "user");
        assertThat(history.get(1)).containsEntry("role", "assistant");
    }

    @Test
    void testGetSessionHistoryWithLimit() {
        AgentBuilder builder = builderWith("{}", "completed");
        HistoryManager manager = new HistoryManager();
        manager.addUserMessage("Message 1");
        manager.addUserMessage("Message 2");
        manager.addUserMessage("Message 3");
        builder.getHistoryManagerMap().put("session_001", manager);

        List<Map<String, String>> history = builder.getSessionHistory("session_001", 2);

        assertThat(history).hasSize(2);
    }

    @Test
    void testClearSession() {
        AgentBuilder builder = builderWith("{}", "completed");
        HistoryManager manager = new HistoryManager();
        manager.addUserMessage("Test");
        TrackingBuilder trackingBuilder = new TrackingBuilder();
        builder.getHistoryManagerMap().put("session_001", manager);
        builder.getAgentBuilderMap().put("session_001", trackingBuilder);

        builder.clearSession("session_001");

        assertThat(manager.getHistory()).isEmpty();
        assertThat(trackingBuilder.resetCalled).isTrue();
    }

    @Test
    void testClearNonexistentSession() {
        AgentBuilder builder = builderWith("{}", "completed");

        builder.clearSession("nonexistent_session");

        assertThat(builder.getHistoryManagerMap()).isEmpty();
        assertThat(builder.getAgentBuilderMap()).isEmpty();
    }

    @Test
    void testGetBuildStatusNonexistent() {
        AgentBuilder builder = builderWith("{}", "completed");

        Map<String, Object> status = builder.getBuildStatus("nonexistent_session");

        assertThat(status).containsEntry("session_id", "nonexistent_session");
        assertThat(status).containsEntry("state", "not_found");
    }

    @Test
    void testGetBuildStatusExisting() {
        AgentBuilder builder = builderWith("{}", "completed");
        TrackingBuilder trackingBuilder = new TrackingBuilder();
        builder.getAgentBuilderMap().put("session_001", trackingBuilder);

        Map<String, Object> status = builder.getBuildStatus("session_001");

        assertThat(status).containsEntry("state", "processing");
    }

    @Test
    void testGetProgressNonexistent() {
        assertThat(AgentBuilder.getProgress("nonexistent_session")).isNull();
    }

    @Test
    void testMapStateToStatusLlmAgent() {
        assertThat(AgentBuilder.mapStateToStatus("initial", "llm_agent")).isEqualTo("clarifying");
        assertThat(AgentBuilder.mapStateToStatus("processing", "llm_agent")).isEqualTo("processing");
        assertThat(AgentBuilder.mapStateToStatus("completed", "llm_agent")).isEqualTo("completed");
    }

    @Test
    void testMapStateToStatusWorkflow() {
        assertThat(AgentBuilder.mapStateToStatus("initial", "workflow")).isEqualTo("requesting");
        assertThat(AgentBuilder.mapStateToStatus("processing", "workflow")).isEqualTo("processing");
        assertThat(AgentBuilder.mapStateToStatus("completed", "workflow")).isEqualTo("completed");
    }

    @Test
    void testMapStateToStatusUnknown() {
        assertThat(AgentBuilder.mapStateToStatus("unknown", "llm_agent")).isEqualTo("unknown");
        assertThat(AgentBuilder.mapStateToStatus("invalid", "workflow")).isEqualTo("unknown");
    }

    private static AgentBuilder builderWith(Object result, String state) {
        return new AgentBuilder(validModelInfo(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                fakeFactory(result, state));
    }

    private static Map<String, Object> validModelInfo() {
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("model_provider", "openai");
        modelInfo.put("model_name", "gpt-4");
        modelInfo.put("api_key", "test_key");
        modelInfo.put("temperature", 0.7d);
        modelInfo.put("top_p", 0.9d);
        return modelInfo;
    }

    private static AgentBuilder.ExecutorFactory fakeFactory(Object result, String state) {
        return (query, sessionId, agentType, historyManagerMap, agentBuilderMap, modelInfo, enableProgress) ->
                new AgentBuilder.BuildExecutor() {
                    @Override
                    public Object execute() {
                        return result;
                    }

                    @Override
                    public Map<String, Object> getBuildStatus() {
                        return Map.of("state", state);
                    }
                };
    }

    private static final class TrackingBuilder extends BaseAgentBuilder {
        private boolean resetCalled;

        private TrackingBuilder() {
            super(new Model((messages, modelConfig, modelClientConfig, options) ->
                    CompletableFuture.completedFuture(null)), new HistoryManager());
            setState(AgentBuilderEnums.BuildState.PROCESSING);
        }

        @Override
        public void reset() {
            resetCalled = true;
            super.reset();
        }

        @Override
        protected Object handleInitial(String query, List<Map<String, String>> dialogHistory) {
            return Map.of();
        }

        @Override
        protected Object handleProcessing(String query, List<Map<String, String>> dialogHistory) {
            return Map.of();
        }

        @Override
        protected Object handleCompleted(String query, List<Map<String, String>> dialogHistory) {
            return Map.of();
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
