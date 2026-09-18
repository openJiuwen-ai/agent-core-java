/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBuilderMainTest {

    @Test
    void builderKeepsProvidedStateMaps() {
        Map<String, Object> modelInfo = new LinkedHashMap<>(Map.of("model_name", "qwen"));
        Map<String, HistoryManager> historyManagers = new LinkedHashMap<>();
        Map<String, BaseAgentBuilder> builders = new LinkedHashMap<>();

        AgentBuilder builder = new AgentBuilder(modelInfo, historyManagers, builders, fakeFactory("{}", "completed"));

        assertSame(modelInfo, builder.getModelInfo());
        assertSame(historyManagers, builder.getHistoryManagerMap());
        assertSame(builders, builder.getAgentBuilderMap());
    }

    @Test
    void buildAgentParsesJsonStringAsDslAndMarksCompleted() {
        AgentBuilder builder = new AgentBuilder(
                Map.of(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                fakeFactory("{\"name\":\"Test Agent\"}", "processing")
        );

        Map<String, Object> result = builder.buildLlmAgent("create", "session-1");

        assertEquals("completed", result.get("status"));
        assertEquals("session-1", result.get("session_id"));
        assertEquals("llm_agent", result.get("agent_type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dsl = (Map<String, Object>) result.get("dsl");
        assertEquals("Test Agent", dsl.get("name"));
    }

    @Test
    void buildAgentMapsPlainLlmStringToClarifyingResponse() {
        AgentBuilder builder = new AgentBuilder(
                Map.of(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                fakeFactory("need more information", "initial")
        );

        Map<String, Object> result = builder.buildLlmAgent("create", "session-1");

        assertEquals("clarifying", result.get("status"));
        assertEquals("need more information", result.get("response"));
    }

    @Test
    void buildWorkflowMapsGraphTextToProcessingMermaid() {
        AgentBuilder builder = new AgentBuilder(
                Map.of(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                fakeFactory("graph TD; A-->B", "initial")
        );

        Map<String, Object> result = builder.buildWorkflow("create", "session-1");

        assertEquals("processing", result.get("status"));
        assertEquals("graph TD; A-->B", result.get("mermaid_code"));
    }

    @Test
    void buildAgentMergesMapResultLikePythonDictUpdate() {
        AgentBuilder builder = new AgentBuilder(
                Map.of(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                fakeFactory(Map.of("status", "custom", "answer", 42), "processing")
        );

        Map<String, Object> result = builder.buildAgent("create", "session-1", "workflow");

        assertEquals("custom", result.get("status"));
        assertEquals(42, result.get("answer"));
    }

    @Test
    void sessionHistoryAndClearSessionUseSharedMaps() {
        Map<String, HistoryManager> historyManagers = new LinkedHashMap<>();
        HistoryManager historyManager = new HistoryManager();
        historyManager.addUserMessage("one");
        historyManager.addAssistantMessage("two");
        historyManagers.put("session-1", historyManager);
        Map<String, BaseAgentBuilder> builders = new LinkedHashMap<>();
        TrackingBuilder trackingBuilder = new TrackingBuilder();
        builders.put("session-1", trackingBuilder);
        AgentBuilder builder = new AgentBuilder(Map.of(), historyManagers, builders, fakeFactory("{}", "completed"));

        List<Map<String, String>> latest = builder.getSessionHistory("session-1", 1);
        assertEquals(1, latest.size());
        assertEquals("two", latest.get(0).get("content"));

        builder.clearSession("session-1");

        assertTrue(builder.getSessionHistory("session-1").isEmpty());
        assertTrue(trackingBuilder.resetCalled);
    }

    @Test
    void statusProgressAndStateMappingMatchPythonSurface() {
        AgentBuilder builder = new AgentBuilder(Map.of(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                fakeFactory("{}", "completed"));

        assertEquals(Map.of("session_id", "missing", "state", "not_found"), builder.getBuildStatus("missing"));
        assertNull(AgentBuilder.getProgress("missing"));
        assertEquals("clarifying", AgentBuilder.mapStateToStatus("initial", "llm_agent"));
        assertEquals("requesting", AgentBuilder.mapStateToStatus("initial", "workflow"));
        assertEquals("processing", AgentBuilder.mapStateToStatus("processing", "workflow"));
        assertEquals("completed", AgentBuilder.mapStateToStatus("completed", "llm_agent"));
        assertEquals("unknown", AgentBuilder.mapStateToStatus("other", "llm_agent"));
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
