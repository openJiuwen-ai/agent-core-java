/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressManager;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's system-test classes in
 * {@code tests/system_tests/dev_tools/agent_builder/test_main_integration.py}.</p>
 */
class AgentBuilderMainIntegrationPythonParityTest {

    @TestFactory
    Collection<DynamicTest> pythonAgentBuilderMainIntegrationCases() {
        return List.of(
                dynamic("TestAgentBuilderEndToEnd::test_agent_builder_full_lifecycle",
                        this::agentBuilderFullLifecycle),
                dynamic("TestAgentBuilderEndToEnd::test_agent_builder_multiple_sessions",
                        this::agentBuilderMultipleSessions),
                dynamic("TestAgentBuilderEndToEnd::test_agent_builder_session_history_management",
                        this::agentBuilderSessionHistoryManagement),
                dynamic("TestAgentBuilderEndToEnd::test_agent_builder_clear_session",
                        this::agentBuilderClearSession),
                dynamic("TestAgentBuilderWorkflowIntegration::test_build_llm_agent_integration",
                        this::buildLlmAgentIntegration),
                dynamic("TestAgentBuilderWorkflowIntegration::test_build_workflow_integration",
                        this::buildWorkflowIntegration),
                dynamic("TestAgentBuilderStatusIntegration::test_get_build_status_integration",
                        this::getBuildStatusIntegration),
                dynamic("TestAgentBuilderStatusIntegration::test_get_build_status_not_found",
                        this::getBuildStatusNotFound),
                dynamic("TestAgentBuilderStatusIntegration::test_get_progress_integration",
                        this::getProgressIntegration),
                dynamic("TestAgentBuilderStateMapping::test_map_state_to_status_llm_agent",
                        this::mapStateToStatusLlmAgent),
                dynamic("TestAgentBuilderStateMapping::test_map_state_to_status_workflow",
                        this::mapStateToStatusWorkflow),
                dynamic("TestAgentBuilderStateMapping::test_map_state_to_status_unknown",
                        this::mapStateToStatusUnknown)
        );
    }

    private void agentBuilderFullLifecycle() {
        Map<String, Object> modelInfo = validModelInfo();

        AgentBuilder builder = new AgentBuilder(modelInfo);

        assertThat(builder).isNotNull();
        assertThat(builder.getModelInfo()).isSameAs(modelInfo);
    }

    private void agentBuilderMultipleSessions() {
        AgentBuilder builder = new AgentBuilder(
                validModelInfo(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                sessionAwareFactory(Map.of(
                        "session_001", map("session_id", "session_001", "status", "completed"),
                        "session_002", map("session_id", "session_002", "status", "completed")
                ), "completed")
        );

        Map<String, Object> result1 = builder.buildLlmAgent("create assistant 1", "session_001");
        Map<String, Object> result2 = builder.buildLlmAgent("create assistant 2", "session_002");

        assertThat(result1).containsEntry("session_id", "session_001");
        assertThat(result2).containsEntry("session_id", "session_002");
    }

    private void agentBuilderSessionHistoryManagement() {
        AgentBuilder builder = builderWith(map(), "completed");
        HistoryManager historyManager = new HistoryManager();
        historyManager.addUserMessage("message 1");
        historyManager.addAssistantMessage("reply 1");
        builder.getHistoryManagerMap().put("session_001", historyManager);

        List<Map<String, String>> history = builder.getSessionHistory("session_001");

        assertThat(history).hasSize(2);
    }

    private void agentBuilderClearSession() {
        AgentBuilder builder = builderWith(map(), "completed");
        HistoryManager historyManager = new HistoryManager();
        historyManager.addUserMessage("message 1");
        TrackingBuilder trackingBuilder = new TrackingBuilder();
        builder.getHistoryManagerMap().put("session_001", historyManager);
        builder.getAgentBuilderMap().put("session_001", trackingBuilder);

        assertThat(builder.getHistoryManagerMap()).containsKey("session_001");

        builder.clearSession("session_001");

        assertThat(historyManager.getHistory()).isEmpty();
        assertThat(trackingBuilder.resetCalled).isTrue();
    }

    private void buildLlmAgentIntegration() {
        RecordingFactory factory = new RecordingFactory(
                map("session_id", "session_001", "agent_type", "llm_agent", "status", "completed"),
                "completed"
        );
        AgentBuilder builder = new AgentBuilder(validModelInfo(), new LinkedHashMap<>(), new LinkedHashMap<>(), factory);

        Map<String, Object> result = builder.buildLlmAgent("create assistant", "session_001");

        assertThat(factory.calls).containsExactly("create assistant|session_001|llm_agent");
        assertThat(result)
                .containsEntry("session_id", "session_001")
                .containsEntry("agent_type", "llm_agent");
    }

    private void buildWorkflowIntegration() {
        RecordingFactory factory = new RecordingFactory(
                map("session_id", "session_001", "agent_type", "workflow", "status", "completed"),
                "completed"
        );
        AgentBuilder builder = new AgentBuilder(validModelInfo(), new LinkedHashMap<>(), new LinkedHashMap<>(), factory);

        Map<String, Object> result = builder.buildWorkflow("create workflow", "session_001");

        assertThat(factory.calls).containsExactly("create workflow|session_001|workflow");
        assertThat(result)
                .containsEntry("session_id", "session_001")
                .containsEntry("agent_type", "workflow");
    }

    private void getBuildStatusIntegration() {
        AgentBuilder builder = builderWith(map(), "completed");
        builder.getAgentBuilderMap().put("session_001", new TrackingBuilder());

        Map<String, Object> status = builder.getBuildStatus("session_001");

        assertThat(status).containsEntry("state", "processing");
    }

    private void getBuildStatusNotFound() {
        AgentBuilder builder = builderWith(map(), "completed");

        Map<String, Object> status = builder.getBuildStatus("non_existent_session");

        assertThat(status).containsEntry("state", "not_found");
    }

    private void getProgressIntegration() {
        String sessionId = "system-main-progress-session";
        ProgressManager.PROGRESS_MANAGER.removeReporter(sessionId);
        ProgressReporter reporter = ProgressManager.PROGRESS_MANAGER.createReporter(sessionId, "llm_agent");
        reporter.startStage(AgentBuilderEnums.ProgressStage.INITIALIZING, "initializing");

        Map<String, Object> progress = AgentBuilder.getProgress(sessionId);

        assertThat(progress).containsKey("steps");
        ProgressManager.PROGRESS_MANAGER.removeReporter(sessionId);
    }

    private void mapStateToStatusLlmAgent() {
        assertThat(AgentBuilder.mapStateToStatus("initial", "llm_agent")).isEqualTo("clarifying");
        assertThat(AgentBuilder.mapStateToStatus("processing", "llm_agent")).isEqualTo("processing");
        assertThat(AgentBuilder.mapStateToStatus("completed", "llm_agent")).isEqualTo("completed");
    }

    private void mapStateToStatusWorkflow() {
        assertThat(AgentBuilder.mapStateToStatus("initial", "workflow")).isEqualTo("requesting");
        assertThat(AgentBuilder.mapStateToStatus("processing", "workflow")).isEqualTo("processing");
        assertThat(AgentBuilder.mapStateToStatus("completed", "workflow")).isEqualTo("completed");
    }

    private void mapStateToStatusUnknown() {
        assertThat(AgentBuilder.mapStateToStatus("unknown", "llm_agent")).isEqualTo("unknown");
        assertThat(AgentBuilder.mapStateToStatus("invalid", "workflow")).isEqualTo("unknown");
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }

    private static AgentBuilder builderWith(Object result, String state) {
        return new AgentBuilder(validModelInfo(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                new RecordingFactory(result, state));
    }

    private static Map<String, Object> validModelInfo() {
        return map(
                "model_provider", "openai",
                "model_name", "gpt-4",
                "api_key", "test_key",
                "temperature", 0.7d,
                "top_p", 0.9d
        );
    }

    private static AgentBuilder.ExecutorFactory sessionAwareFactory(Map<String, Map<String, Object>> results,
            String state) {
        return (query, sessionId, agentType, historyManagerMap, agentBuilderMap, modelInfo, enableProgress) ->
                new AgentBuilder.BuildExecutor() {
                    @Override
                    public Object execute() {
                        return results.getOrDefault(sessionId, map());
                    }

                    @Override
                    public Map<String, Object> getBuildStatus() {
                        return Map.of("state", state);
                    }
                };
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }

    private static final class RecordingFactory implements AgentBuilder.ExecutorFactory {
        private final Object result;
        private final String state;
        private final List<String> calls = new java.util.ArrayList<>();

        private RecordingFactory(Object result, String state) {
            this.result = result;
            this.state = state;
        }

        @Override
        public AgentBuilder.BuildExecutor create(
                String query,
                String sessionId,
                String agentType,
                Map<String, HistoryManager> historyManagerMap,
                Map<String, BaseAgentBuilder> agentBuilderMap,
                Map<String, Object> modelInfo,
                boolean enableProgress) {
            calls.add(query + "|" + sessionId + "|" + agentType);
            return new AgentBuilder.BuildExecutor() {
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
