/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

class WorkflowAgentInvokeOutputTest {
    @Test
    void normalizeInvokeOutputPrefersFinalAnswerAfterHistoricalInteraction() throws Exception {
        WorkflowAgent agent = new WorkflowAgent(WorkflowAgentConfig.builder().id("invoke-output-agent").build());
        OutputSchema interaction = new OutputSchema("__interaction__", 0,
                new InteractionOutput("questioner", "provide details"));
        Map<String, Object> finalPayload = Map.of("answer", "completed");
        OutputSchema finalAnswer = new OutputSchema("workflow_final", 0, finalPayload);
        ControllerOutput rawOutput = new ControllerOutput("task_completion", List.of(interaction, finalAnswer));

        Method normalizeMethod = WorkflowAgent.class.getDeclaredMethod("normalizeInvokeOutput",
                ControllerOutput.class);
        normalizeMethod.setAccessible(true);
        ControllerOutput normalized = (ControllerOutput) normalizeMethod.invoke(agent, rawOutput);

        Map<String, Object> result = normalized.getDataAsMap();
        WorkflowOutput workflowOutput = assertInstanceOf(WorkflowOutput.class, result.get("output"));
        assertEquals(WorkflowExecutionState.COMPLETED, workflowOutput.getState());
        assertEquals(finalPayload, workflowOutput.getResult());
        assertEquals("answer", result.get("result_type"));
    }
}
