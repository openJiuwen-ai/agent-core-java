package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressStage;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressStatus;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressStep;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBuilderCompatibilityTest {

    @Test
    void builderCreationRetainsModelInfoAndSharedMaps() {
        Map<String, Object> modelInfo = Map.of("model_provider", "openai", "model_name", "gpt-4");
        Map<String, HistoryManager> historyMap = new ConcurrentHashMap<>();
        Map<String, AgentBuilder.AgentBuilderSession> sessionMap = new ConcurrentHashMap<>();

        AgentBuilder builder = new AgentBuilder(modelInfo, historyMap, sessionMap);

        assertThat(builder.getModelInfo()).containsEntry("model_provider", "openai");
        assertThat(builder.getHistoryManagerMap()).isSameAs(historyMap);
        assertThat(builder.getAgentBuilderMap()).isSameAs(sessionMap);
    }

    @Test
    void buildAgentShouldParseDslJsonResponsesIntoCompletedResult() {
        AgentBuilder builder = new AgentBuilder(Map.of("model_name", "gpt-4"));

        Map<String, Object> result = builder.buildAgent("{\"agent_id\":\"123\",\"name\":\"Test\"}", "session_001", "llm_agent");

        assertThat(result).containsEntry("session_id", "session_001");
        assertThat(result).containsEntry("agent_type", "llm_agent");
        assertThat(result).containsEntry("status", "completed");
        @SuppressWarnings("unchecked")
        Map<String, Object> dsl = (Map<String, Object>) result.get("dsl");
        assertThat(dsl).containsEntry("agent_id", "123");
    }

    @Test
    void buildWorkflowShouldReturnMermaidDraftAsProcessing() {
        AgentBuilder builder = new AgentBuilder();

        Map<String, Object> result = builder.buildWorkflow("graph TD; A-->B", "workflow_session");

        assertThat(result).containsEntry("status", "processing");
        assertThat(result).containsEntry("agent_type", "workflow");
        assertThat(String.valueOf(result.get("mermaid_code"))).contains("graph TD");
    }

    @Test
    void buildAgentShouldReturnClarificationWhenInputIsUnderspecified() {
        AgentBuilder builder = new AgentBuilder();

        Map<String, Object> result = builder.buildLlmAgent("做一个", "session_002");

        assertThat(result).containsEntry("status", "clarifying");
        assertThat(String.valueOf(result.get("response"))).contains("clarify");
    }

    @Test
    void getSessionHistoryAndClearSessionShouldWork() {
        AgentBuilder builder = new AgentBuilder();
        builder.buildLlmAgent("Build an assistant that triages support requests.", "session_003");

        assertThat(builder.getSessionHistory("session_003")).hasSize(2);
        assertThat(builder.getBuildStatus("session_003")).containsEntry("state", "completed");

        builder.clearSession("session_003");

        assertThat(builder.getSessionHistory("session_003")).isEmpty();
        assertThat(builder.getBuildStatus("session_003")).containsEntry("state", "not_found");
    }

    @Test
    void getProgressShouldReturnProgressMapWhenSessionExists() {
        AgentBuilder builder = new AgentBuilder();
        builder.buildWorkflow("Create a workflow with explicit steps for onboarding review and approval.", "session_004");

        Map<String, Object> progress = AgentBuilder.getProgress("session_004");
        assertThat(progress).isNotNull();
        assertThat(progress).containsEntry("session_id", "session_004");
        assertThat(progress).containsKey("steps");
    }

    @Test
    void mapStateToStatusShouldRetainPythonCompatibleLabels() {
        assertThat(AgentBuilder.mapStateToStatus("initial", "llm_agent")).isEqualTo("clarifying");
        assertThat(AgentBuilder.mapStateToStatus("initial", "workflow")).isEqualTo("requesting");
        assertThat(AgentBuilder.mapStateToStatus("processing", "workflow")).isEqualTo("processing");
        assertThat(AgentBuilder.mapStateToStatus("completed", "llm_agent")).isEqualTo("completed");
        assertThat(AgentBuilder.mapStateToStatus("unknown", "llm_agent")).isEqualTo("unknown");
    }

    @Test
    void progressStepShouldSerializeEnumsAndDetails() {
        ProgressStep step = ProgressStep.builder()
                .stage(ProgressStage.INITIALIZING)
                .status(ProgressStatus.RUNNING)
                .message("Starting")
                .details(new LinkedHashMap<>(Map.of("count", 1)))
                .duration(1.5)
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        Map<String, Object> result = step.toMap();
        assertThat(result).containsEntry("stage", "initializing");
        assertThat(result).containsEntry("status", "running");
        assertThat(result).containsEntry("message", "Starting");
    }
}
