/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps repeated budget-test model responses protocol-valid without changing explicit scripted responses.
 *
 * @since 2026-11-06
 */
class RunBudgetTestFixtureTest {
    @Test
    void repeatedTailUsesFreshCallsAndPreservesEarlierMessages() {
        AssistantMessage tail = RunBudgetTestFixture.toolCall("continue", "echo", 1);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(List.of(), tail);
        ModelInvokeOptions options = ModelInvokeOptions.builder().build();

        AssistantMessage first = model.model().invoke(List.of(), options).toCompletableFuture().join();
        AssistantMessage second = model.model().invoke(List.of(), options).toCompletableFuture().join();
        List<ToolCall> streamedCalls = model.model().stream(List.of(), options).next().getToolCalls();

        assertThat(first.getToolCalls()).extracting(ToolCall::getId).containsExactly("tc-1-tail-1");
        assertThat(second.getToolCalls()).extracting(ToolCall::getId).containsExactly("tc-1-tail-2");
        assertThat(streamedCalls).extracting(ToolCall::getId).containsExactly("tc-1-tail-3");
        assertThat(tail.getToolCalls()).extracting(ToolCall::getId).containsExactly("tc-1");
        assertThat(second.getToolCalls().get(0).getName()).isEqualTo("echo");
        assertThat(second.getToolCalls().get(0).getArguments()).isEqualTo("{}");
    }

    @Test
    void explicitScriptStillPreservesDuplicateCallIdsForProtocolTests() {
        AssistantMessage response = RunBudgetTestFixture.toolCall("replay", "echo", 1);
        RunBudgetTestFixture.ScriptedModel model = RunBudgetTestFixture.scriptedModel(
                List.of(response, response), RunBudgetTestFixture.answer("done"));
        ModelInvokeOptions options = ModelInvokeOptions.builder().build();

        AssistantMessage first = model.model().invoke(List.of(), options).toCompletableFuture().join();
        AssistantMessage second = model.model().invoke(List.of(), options).toCompletableFuture().join();

        assertThat(first).isSameAs(response);
        assertThat(second).isSameAs(response);
    }
}
