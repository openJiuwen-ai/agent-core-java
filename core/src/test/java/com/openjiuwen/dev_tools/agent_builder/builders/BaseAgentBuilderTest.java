/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.BuildProgress;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code BaseAgentBuilder} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/base.py}.
 */
class BaseAgentBuilderTest {

    @Test
    void constructorInitializesCoreCollaboratorsAndState() {
        Model model = modelReturning("{\"tool_id_list\": []}");
        HistoryManager historyManager = new HistoryManager();

        ConcreteAgentBuilder builder = new ConcreteAgentBuilder(model, historyManager, null);

        assertThat(builder.getLlm()).isSameAs(model);
        assertThat(builder.getHistoryManager()).isSameAs(historyManager);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getResource()).isEmpty();
    }

    @Test
    void executeDispatchesByCurrentStateAndUsesHistory() {
        ConcreteAgentBuilder builder = newBuilder("{\"tool_id_list\": []}");
        builder.getHistoryManager().addUserMessage("hello");

        Object first = builder.execute("test query");
        assertThat(first).isEqualTo("Initial: test query, history=1");
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);

        Object second = builder.execute("next query");
        assertThat(second).isEqualTo("Processing: next query, history=1");

        builder.setState(AgentBuilderEnums.BuildState.COMPLETED);
        Object third = builder.execute("final query");
        assertThat(third).isEqualTo(Map.of("result", "Completed: final query, history=1"));
    }

    @Test
    void executeRunsResourceRetrievalAndProgressCallbacks() {
        List<BuildProgress> snapshots = new ArrayList<>();
        ProgressReporter reporter = new ProgressReporter("session-1", "llm_agent");
        reporter.addCallback(snapshots::add);
        ConcreteAgentBuilder builder = newBuilder(
                reporter,
                "{\"tool_id_list\": [\"4aebb55e-1571-4a98-b353-41793b4434e3\"]}");

        builder.execute("with resource");

        assertThat(builder.getResource()).containsKeys("plugins", "plugin_dict", "tool_id_map");
        assertThat((List<?>) builder.getResource().get("plugins")).hasSize(1);
        assertThat(reporter.getProgress().getSteps())
                .extracting(step -> step.getStage())
                .contains(AgentBuilderEnums.ProgressStage.INITIALIZING,
                        AgentBuilderEnums.ProgressStage.RESOURCE_RETRIEVING);
        assertThat(snapshots).isNotEmpty();
    }

    @Test
    void updateResourceFailureDoesNotInterruptBuildFlow() {
        ConcreteAgentBuilder builder = newBuilder("[1, 2]");
        builder.setResource(Map.of("existing", List.of(Map.of("resource_id", "old"))));

        Object result = builder.execute("test query");

        assertThat(result).isEqualTo("Initial: test query, history=0");
        assertThat(builder.getResource()).containsKey("existing");
    }

    @Test
    void mergeResourceListsKeepsExistingItemsByResourceId() {
        ConcreteAgentBuilder builder = newBuilder("{\"tool_id_list\": []}");

        List<Map<String, Object>> merged = builder.mergeForTest(
                List.of(
                        Map.of("resource_id", "1", "name", "A"),
                        Map.of("resource_id", "2", "name", "B")),
                List.of(
                        Map.of("resource_id", "2", "name", "B2"),
                        Map.of("resource_id", "3", "name", "C"),
                        Map.of("name", "missing-key")));

        assertThat(merged).containsExactly(
                Map.of("resource_id", "1", "name", "A"),
                Map.of("resource_id", "2", "name", "B"),
                Map.of("resource_id", "3", "name", "C"));
    }

    @Test
    void resetClearsResourceAndDelegatesInternalReset() {
        ConcreteAgentBuilder builder = newBuilder("{\"tool_id_list\": []}");
        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);
        builder.setResource(Map.of("plugins", List.of(Map.of("resource_id", "1"))));

        builder.reset();

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getResource()).isEmpty();
        assertThat(builder.isInternalReset()).isTrue();
    }

    @Test
    void getBuildStatusCountsOnlyListValuesLikePython() {
        ConcreteAgentBuilder builder = newBuilder("{\"tool_id_list\": []}");
        builder.setResource(Map.of(
                "plugins", List.of(Map.of("resource_id", "1"), Map.of("resource_id", "2")),
                "plugin_dict", Map.of("a", 1, "b", 2)));

        Map<String, Object> status = builder.getBuildStatus();
        Map<?, ?> resourceCount = (Map<?, ?>) status.get("resource_count");

        assertThat(status).containsEntry("state", "initial");
        assertThat(resourceCount.get("plugins")).isEqualTo(2);
        assertThat(resourceCount.get("plugin_dict")).isEqualTo(1);
    }

    @Test
    void executeUnknownStateRaisesApplicationErrorAndFailsProgress() {
        ProgressReporter reporter = new ProgressReporter("session-err", "llm_agent");
        ConcreteAgentBuilder builder = newBuilder(reporter, "{\"tool_id_list\": []}");
        builder.setState(null);

        assertThatThrownBy(() -> builder.execute("bad state"))
                .isInstanceOf(ApplicationError.class)
                .satisfies(error -> assertThat(((ApplicationError) error).getStatus())
                        .isEqualTo(StatusCode.LLM_AGENT_STATE_ERROR));
        assertThat(reporter.getProgress().getCurrentStatus())
                .isEqualTo(AgentBuilderEnums.ProgressStatus.FAILED);
    }

    @Test
    void workflowAliasDelegatesToAbstractHook() {
        ConcreteAgentBuilder builder = newBuilder("{\"tool_id_list\": []}");
        builder.setWorkflowBuilder(true);

        assertThat(builder.isWorkflowBuilder()).isTrue();
        assertThat(builder.is_workflow_builder()).isTrue();
    }

    private static ConcreteAgentBuilder newBuilder(String response) {
        return newBuilder(null, response);
    }

    private static ConcreteAgentBuilder newBuilder(ProgressReporter reporter, String response) {
        return new ConcreteAgentBuilder(modelReturning(response), new HistoryManager(), reporter);
    }

    private static Model modelReturning(String content) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(content)));
    }

    private static final class ConcreteAgentBuilder extends BaseAgentBuilder {
        private boolean internalReset;
        private boolean workflowBuilder;

        private ConcreteAgentBuilder(Model llm, HistoryManager historyManager, ProgressReporter progressReporter) {
            super(llm, historyManager, progressReporter);
        }

        private void setWorkflowBuilder(boolean workflowBuilder) {
            this.workflowBuilder = workflowBuilder;
        }

        private boolean isInternalReset() {
            return internalReset;
        }

        private List<Map<String, Object>> mergeForTest(List<?> existing, List<?> updates) {
            return mergeResourceLists(existing, updates, "resource_id");
        }

        @Override
        protected Object handleInitial(String query, List<Map<String, String>> dialogHistory) {
            setState(AgentBuilderEnums.BuildState.PROCESSING);
            return "Initial: " + query + ", history=" + dialogHistory.size();
        }

        @Override
        protected Object handleProcessing(String query, List<Map<String, String>> dialogHistory) {
            return "Processing: " + query + ", history=" + dialogHistory.size();
        }

        @Override
        protected Object handleCompleted(String query, List<Map<String, String>> dialogHistory) {
            return Map.of("result", "Completed: " + query + ", history=" + dialogHistory.size());
        }

        @Override
        protected void resetInternalState() {
            internalReset = true;
        }

        @Override
        protected boolean isWorkflowBuilderInternal() {
            return workflowBuilder;
        }
    }
}
