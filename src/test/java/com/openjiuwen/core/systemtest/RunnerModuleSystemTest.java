/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for the runner module.
 */
@Tag("system-test")
class RunnerModuleSystemTest extends SystemTestSupport {

    @Test
    @DisplayName("Runner executes registered workflow by resource id")
    void testRunnerRunWorkflowById() {
        String workflowId = uniqueId("runner-workflow");
        String sessionId = trackSessionId("runner-workflow-session");

        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id(workflowId)
                .name(workflowId)
                .description("Runner workflow system test")
                .version("1")
                .build());
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.setEndComp(
                "end",
                new End(Map.of("responseTemplate", "runner workflow {{query}}")),
                Map.of("query", "${start.query}"),
                null
        );
        workflow.addConnection("start", "end");
        registerWorkflow(workflow);

        WorkflowOutput output = (WorkflowOutput) Runner.runWorkflow(
                workflowId,
                Map.of("query", "validated"),
                sessionId,
                null
        );

        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertTrue(containsIgnoreCase(String.valueOf(output.getResult()), "runner workflow"));
        assertTrue(containsIgnoreCase(String.valueOf(output.getResult()), "validated"));
    }

    @Test
    @DisplayName("Runner executes registered remote ReActAgent by resource id")
    void testRunnerRunManagedRemoteAgentById() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("runner-react-agent");
        String sessionId = trackSessionId("runner-agent-session");
        var agent = newRemoteReActAgent(
                agentId,
                "Reply briefly in English. If the user asks for an exact token, return that token."
        );
        registerAgent(agent);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) Runner.runAgent(
                agentId,
                Map.of(
                        "query", "Reply with the exact token RUNNER_READY.",
                        "conversation_id", sessionId
                ),
                null,
                null
        );

        assertEquals("answer", result.get("result_type"));
        assertTrue(containsIgnoreCase(flattenText(result), "RUNNER_READY"),
                () -> "Expected RUNNER_READY in output but got: " + flattenText(result));
    }
}
