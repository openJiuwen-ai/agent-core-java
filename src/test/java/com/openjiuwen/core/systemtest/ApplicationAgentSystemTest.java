/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for application-layer agents.
 */
@Tag("system-test")
class ApplicationAgentSystemTest extends SystemTestSupport {

    @Test
    @DisplayName("LlmAgent answers direct user query with remote model")
    void testLlmAgentDirectAnswer() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("app-llm-agent");
        String sessionId = trackSessionId("app-llm-session");

        var agent = newRemoteLlmAgent(
                agentId,
                "Reply in English. If no tool or workflow is needed, answer directly and keep it short."
        );

        InvocationCapture capture = streamAgent(
                agent,
                Map.of(
                        "query", "Reply with the exact token ORBIT_OK and nothing else.",
                        "conversation_id", sessionId
                ),
                sessionId
        );

        assertTrue(containsIgnoreCase(capture.flattenedText(), "ORBIT_OK"),
                () -> "Expected ORBIT_OK in output but got: " + capture.flattenedText());
    }

    @Test
    @DisplayName("WorkflowAgent executes registered workflow end-to-end")
    void testWorkflowAgentInvokesRegisteredWorkflow() {
        String workflowBaseId = uniqueId("app-workflow");
        String workflowVersion = "1";
        String workflowResourceId = workflowBaseId + "_" + workflowVersion;
        String workflowName = uniqueId("EchoWorkflow");

        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id(workflowResourceId)
                .name(workflowName)
                .description("Echo workflow used by application system tests")
                .version(workflowVersion)
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string")
                        ),
                        "required", java.util.List.of("query")
                ))
                .build());
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.setEndComp(
                "end",
                new End(Map.of("responseTemplate", "workflow handled {{query}}")),
                Map.of("query", "${start.query}"),
                null
        );
        workflow.addConnection("start", "end");
        registerWorkflow(workflow);

        WorkflowSchema workflowSchema = WorkflowSchema.builder()
                .id(workflowBaseId)
                .name(workflowName)
                .version(workflowVersion)
                .description("Echo workflow schema")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string")
                        ),
                        "required", java.util.List.of("query")
                ))
                .build();

        WorkflowAgent agent = new WorkflowAgent(WorkflowAgentConfig.builder()
                .id(uniqueId("workflow-agent"))
                .description("workflow application agent")
                .workflows(java.util.List.of(workflowSchema))
                .build());

        String sessionId = trackSessionId("workflow-agent-session");
        InvocationCapture capture = streamAgent(
                agent,
                Map.of(
                        "query", "runner coverage",
                        "conversation_id", sessionId
                ),
                sessionId
        );

        assertTrue(containsIgnoreCase(capture.flattenedText(), "workflow handled"),
                () -> "Expected workflow response in output but got: " + capture.flattenedText());
        assertTrue(containsIgnoreCase(capture.flattenedText(), "runner coverage"),
                () -> "Expected original query in output but got: " + capture.flattenedText());
    }
}
