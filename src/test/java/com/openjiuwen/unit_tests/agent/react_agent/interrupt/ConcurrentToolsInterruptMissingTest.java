/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent.interrupt;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.react_agent.interrupt.test_react_agent_interrupt_concurrent_tools} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/test_react_agent_interrupt_concurrent_tools.py}.
 */
class ConcurrentToolsInterruptMissingTest {

    @Test
    void testHitlRailConcurrentToolsAllConfirmed() {
        ToolInterruptHandler.CollectResult collected = collect(
                List.of(interrupt("read"), interrupt("read")),
                List.of(toolCall("c1", "read", "{\"filepath\":\"a.txt\"}"),
                        toolCall("c2", "read", "{\"filepath\":\"b.txt\"}")));

        Map<String, Object> firstResult = ToolInterruptHandler.buildInterruptResult(collected.getPayloads());
        assertThat(firstResult.get("interrupt_ids")).isEqualTo(List.of("c1", "c2"));

        Map<String, Object> afterFirstConfirm = ToolInterruptHandler.buildInterruptResult(
                List.of(collected.getPayloads().get(1)));
        assertThat(afterFirstConfirm.get("interrupt_ids")).isEqualTo(List.of("c2"));

        List<String> executed = executeApproved(List.of("c1", "c2"), Map.of("c1", true, "c2", true));
        assertThat(executed).containsExactly("c1", "c2");
    }

    @Test
    void testHitlRailConcurrentToolsPartialRejectOneRound() {
        ToolInterruptHandler.CollectResult collected = collect(
                List.of(interrupt("read"), interrupt("read")),
                List.of(toolCall("c1", "read", "{\"filepath\":\"a.txt\"}"),
                        toolCall("c2", "read", "{\"filepath\":\"b.txt\"}")));

        assertThat(ToolInterruptHandler.buildInterruptResult(collected.getPayloads()).get("interrupt_ids"))
                .isEqualTo(List.of("c1", "c2"));

        List<String> executed = executeApproved(List.of("c1", "c2"), Map.of("c1", true, "c2", false));
        assertThat(executed).containsExactly("c1");
    }

    @Test
    void testHitlRailConcurrentToolsPartialRejectTwoRounds() {
        ToolInterruptHandler.CollectResult collected = collect(
                List.of(interrupt("read"), interrupt("read")),
                List.of(toolCall("c1", "read", "{\"filepath\":\"a.txt\"}"),
                        toolCall("c2", "read", "{\"filepath\":\"b.txt\"}")));

        Map<String, Object> remaining = ToolInterruptHandler.buildInterruptResult(
                List.of(collected.getPayloads().get(0)));
        assertThat(remaining.get("interrupt_ids")).isEqualTo(List.of("c1"));

        List<String> executed = executeApproved(List.of("c1"), Map.of("c1", true));
        assertThat(executed).containsExactly("c1");
    }

    @Test
    void testHitlRailConcurrentToolsOnePassOneInterrupt() {
        ToolInterruptHandler.CollectResult collected = collect(
                List.of(interrupt("read"), "action-result"),
                List.of(toolCall("c1", "read", "{\"filepath\":\"a.txt\"}"),
                        toolCall("c2", "action", "{\"action\":\"test\"}")));

        Map<String, Object> result = ToolInterruptHandler.buildInterruptResult(collected.getPayloads());
        assertThat(result.get("interrupt_ids")).isEqualTo(List.of("c1"));
        assertThat(collected.getInterruptedTools()).containsOnlyKeys("c1");
        assertThat(collected.getAutoConfirmMapping()).containsEntry("c1", "read");

        List<String> passedThrough = executeApproved(List.of("c2"), Map.of("c2", true));
        assertThat(passedThrough).containsExactly("c2");
    }

    private static ToolInterruptHandler.CollectResult collect(List<Object> results, List<ToolCall> toolCalls) {
        return new ToolInterruptHandler(null).collectInterrupts(results, toolCalls);
    }

    private static ToolInterruptException interrupt(String autoConfirmKey) {
        InterruptRequest request = new InterruptRequest("Please approve or reject?", Map.of(), autoConfirmKey);
        return new ToolInterruptException(request);
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }

    private static List<String> executeApproved(List<String> toolCallIds, Map<String, Boolean> approvals) {
        List<String> executed = new ArrayList<>();
        for (String toolCallId : toolCallIds) {
            if (Boolean.TRUE.equals(approvals.get(toolCallId))) {
                executed.add(toolCallId);
            }
        }
        return executed;
    }
}
