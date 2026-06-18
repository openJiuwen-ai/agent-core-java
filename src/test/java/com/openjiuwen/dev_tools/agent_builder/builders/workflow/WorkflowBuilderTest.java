/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderConstants;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for the workflow builder state machine.
 *
 * <p>Mirrors Python's {@code WorkflowBuilder} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/builder.py}.</p>
 */
class WorkflowBuilderTest {

    @Test
    void constructorInitializesStateAndCollaborators() {
        HistoryManager historyManager = new HistoryManager();
        WorkflowBuilder builder = new WorkflowBuilder(modelReturning("{\"tool_id_list\": []}"), historyManager);

        assertThat(builder.getLlm()).isNotNull();
        assertThat(builder.getHistoryManager()).isSameAs(historyManager);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getWorkflowName()).isNull();
        assertThat(builder.getWorkflowNameEn()).isNull();
        assertThat(builder.getWorkflowDesc()).isNull();
        assertThat(builder.getDl()).isNull();
        assertThat(builder.getMermaidCode()).isNull();
        assertThat(builder.getProgressReporter()).isNull();
        assertThat(builder.getResource()).isEmpty();
        assertThat(builder.isWorkflowBuilder()).isTrue();
    }

    @Test
    void initialStateRequestsMoreInformationWhenIntentIsIncomplete() {
        HistoryManager historyManager = new HistoryManager();
        historyManager.addUserMessage("帮我做点事");
        WorkflowBuilder builder = new WorkflowBuilder(modelReturning("{\"provide_process\": false}"), historyManager);

        String response = builder.handleInitial("帮我做点事", historyManager.getHistory());

        assertThat(response).isEqualTo(AgentBuilderConstants.WORKFLOW_REQUEST_CONTENT);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
        assertThat(historyManager.getHistory().get(historyManager.getHistory().size() - 1))
                .containsEntry("role", "assistant")
                .containsEntry("content", AgentBuilderConstants.WORKFLOW_REQUEST_CONTENT);
    }

    @Test
    void initialStateDesignsWorkflowGeneratesDlAndMermaid() {
        HistoryManager historyManager = new HistoryManager();
        historyManager.addUserMessage("创建审批工作流");
        WorkflowBuilder builder = new WorkflowBuilder(modelReturning(
                "{\"provide_process\": true}",
                "basic design",
                "branch design",
                "## New Workflow Design\nfinal design",
                validDl(),
                "{\"need_refined\": false, \"loop_desc\": \"\"}"), historyManager);

        String mermaid = builder.handleInitial("创建审批工作流", historyManager.getHistory());

        assertThat(mermaid).contains("graph TD").contains("node_start").contains("node_end");
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
        assertThat(builder.getWorkflowName()).isEqualTo("创建审批工作流");
        assertThat(builder.getWorkflowNameEn()).isEqualTo("workflow");
        assertThat(builder.getWorkflowDesc()).isEqualTo("final design");
        assertThat(builder.getDl()).isEqualTo(validDl());
        assertThat(builder.getMermaidCode()).isEqualTo(mermaid);
        assertThat(historyManager.getHistory())
                .anySatisfy(message -> assertThat(message.get("content"))
                        .isEqualTo(AgentBuilderConstants.WORKFLOW_DESIGN_RESPONSE_CONTENT + "final design"))
                .anySatisfy(message -> assertThat(message.get("content")).isEqualTo(validDl()));
    }

    @Test
    void processingStateTransformsExistingDlToDslWhenNoRefineIntentAndResets() {
        HistoryManager historyManager = new HistoryManager();
        historyManager.addUserMessage("创建审批工作流");
        WorkflowBuilder builder = new WorkflowBuilder(modelReturning(
                "{\"provide_process\": true}",
                "basic design",
                "branch design",
                "## New Workflow Design\nfinal design",
                validDl(),
                "{\"need_refined\": false, \"loop_desc\": \"\"}",
                "{\"need_refined\": false}"), historyManager);

        builder.handleInitial("创建审批工作流", historyManager.getHistory());
        String dsl = builder.handleProcessing("确认生成", historyManager.getHistory());

        assertThat(dsl).contains("\"nodes\"").contains("node_start").contains("node_end");
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getWorkflowName()).isNull();
        assertThat(builder.getWorkflowNameEn()).isNull();
        assertThat(builder.getWorkflowDesc()).isNull();
        assertThat(builder.getDl()).isNull();
        assertThat(builder.getMermaidCode()).isNull();
    }

    private static Model modelReturning(String... responses) {
        ArrayDeque<String> queuedResponses = new ArrayDeque<>(List.of(responses));
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            if (queuedResponses.isEmpty()) {
                throw new AssertionError("No queued model response for messages: " + messageContents(messages));
            }
            return CompletableFuture.completedFuture(new AssistantMessage(queuedResponses.removeFirst()));
        });
    }

    private static List<String> messageContents(List<BaseMessage> messages) {
        List<String> result = new ArrayList<>();
        for (BaseMessage message : messages) {
            result.add(String.valueOf(message.getContent()));
        }
        return result;
    }

    private static String validDl() {
        return """
                [
                  {
                    "id": "node_start",
                    "type": "Start",
                    "description": "Start",
                    "parameters": {
                      "outputs": [
                        {"name": "query", "description": "用户输入"}
                      ]
                    },
                    "next": "node_end"
                  },
                  {
                    "id": "node_end",
                    "type": "End",
                    "description": "End",
                    "parameters": {
                      "inputs": [
                        {"name": "result", "value": "${node_start.query}"}
                      ],
                      "configs": {
                        "template": "${node_start.query}"
                      }
                    }
                  }
                ]
                """;
    }
}
