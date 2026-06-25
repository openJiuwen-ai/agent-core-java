/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolLifecycleOutputFactoryTest {

    @Test
    void buildToolCallOutputBackfillsMissingIdAndKeepsArguments() {
        ToolCall toolCall = ToolCall.builder()
                .name("lookupEnv")
                .arguments("{\"key\":\"env\"}")
                .build();

        OutputSchema output = ToolLifecycleOutputFactory.buildToolCallOutput(toolCall, 3);

        assertThat(output.getType()).isEqualTo("tool_call");
        assertThat(output.getIndex()).isEqualTo(3);
        assertThat(toolCall.getId()).isNotBlank();
        assertThat(payload(output))
                .containsEntry("tool_call_id", toolCall.getId())
                .containsEntry("tool_name", "lookupEnv")
                .containsEntry("arguments", "{\"key\":\"env\"}");
    }

    @Test
    void buildToolResultOutputEmitsCompletedResult() {
        ToolCall toolCall = ToolCall.builder()
                .id("call-1")
                .name("lookupEnv")
                .arguments("{\"key\":\"env\"}")
                .build();
        AbilityManager.ExecutionResult result = new AbilityManager.ExecutionResult(
                Map.of("result", "prod"),
                new ToolMessage("{result=prod}", "call-1", "lookupEnv")
        );

        OutputSchema output = ToolLifecycleOutputFactory.buildToolResultOutput(toolCall, result, 4);

        assertThat(output.getType()).isEqualTo("tool_result");
        assertThat(output.getIndex()).isEqualTo(4);
        assertThat(payload(output))
                .containsEntry("tool_call_id", "call-1")
                .containsEntry("tool_name", "lookupEnv")
                .containsEntry("status", "completed")
                .containsEntry("result", "{result=prod}");
    }

    @Test
    void buildToolResultOutputEmitsErrorForExecutionExceptionMessage() {
        ToolCall toolCall = ToolCall.builder()
                .id("call-2")
                .name("lookupEnv")
                .arguments("{\"key\":\"env\"}")
                .build();
        AbilityManager.ExecutionResult result = new AbilityManager.ExecutionResult(
                null,
                new ToolMessage("Ability execution error: boom", "call-2", "lookupEnv")
        );

        OutputSchema output = ToolLifecycleOutputFactory.buildToolResultOutput(toolCall, result, 5);

        assertThat(payload(output))
                .containsEntry("tool_call_id", "call-2")
                .containsEntry("tool_name", "lookupEnv")
                .containsEntry("status", "error")
                .containsEntry("error", "Ability execution error: boom");
    }

    @Test
    void buildToolResultOutputEmitsErrorForSuccessFalseMap() {
        ToolCall toolCall = ToolCall.builder()
                .id("call-3")
                .name("lookupEnv")
                .arguments("{\"key\":\"env\"}")
                .build();
        AbilityManager.ExecutionResult result = new AbilityManager.ExecutionResult(
                Map.of("success", false, "error", "not allowed"),
                new ToolMessage("not allowed", "call-3", "lookupEnv")
        );

        OutputSchema output = ToolLifecycleOutputFactory.buildToolResultOutput(toolCall, result, 6);

        assertThat(payload(output))
                .containsEntry("tool_call_id", "call-3")
                .containsEntry("tool_name", "lookupEnv")
                .containsEntry("status", "error")
                .containsEntry("error", "not allowed");
    }

    @Test
    void buildToolResultOutputPrefersResultErrorFieldOverToolMessage() {
        ToolCall toolCall = ToolCall.builder()
                .id("call-prio")
                .name("lookupEnv")
                .arguments("{\"key\":\"env\"}")
                .build();
        AbilityManager.ExecutionResult result = new AbilityManager.ExecutionResult(
                Map.of("success", false, "error", "from-field"),
                new ToolMessage("from-message", "call-prio", "lookupEnv")
        );

        OutputSchema output = ToolLifecycleOutputFactory.buildToolResultOutput(toolCall, result, 7);

        assertThat(payload(output))
                .containsEntry("tool_call_id", "call-prio")
                .containsEntry("tool_name", "lookupEnv")
                .containsEntry("status", "error")
                .containsEntry("error", "from-field");
    }

    @Test
    void buildToolResultOutputFallsBackToToolMessageWhenNoErrorField() {
        ToolCall toolCall = ToolCall.builder()
                .id("call-fallback")
                .name("lookupEnv")
                .arguments("{\"key\":\"env\"}")
                .build();
        AbilityManager.ExecutionResult result = new AbilityManager.ExecutionResult(
                Map.of("success", false),
                new ToolMessage("from-message", "call-fallback", "lookupEnv")
        );

        OutputSchema output = ToolLifecycleOutputFactory.buildToolResultOutput(toolCall, result, 8);

        assertThat(payload(output))
                .containsEntry("tool_call_id", "call-fallback")
                .containsEntry("tool_name", "lookupEnv")
                .containsEntry("status", "error")
                .containsEntry("error", "from-message");
    }

    @Test
    void buildToolResultOutputEmitsCompletedWithEmptyResultForNullResultValue() {
        ToolCall toolCall = ToolCall.builder()
                .id("call-null")
                .name("lookupEnv")
                .arguments("{\"key\":\"env\"}")
                .build();
        AbilityManager.ExecutionResult result = new AbilityManager.ExecutionResult(
                null,
                new ToolMessage("", "call-null", "lookupEnv")
        );

        OutputSchema output = ToolLifecycleOutputFactory.buildToolResultOutput(toolCall, result, 9);

        assertThat(payload(output))
                .containsEntry("tool_call_id", "call-null")
                .containsEntry("tool_name", "lookupEnv")
                .containsEntry("status", "completed")
                .containsEntry("result", "");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(OutputSchema output) {
        assertThat(output.getPayload()).isInstanceOf(Map.class);
        return (Map<String, Object>) output.getPayload();
    }
}
