/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the handoff tool.
 *
 * <p>Mirrors Python's {@code HandoffTool} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_tool.py}.</p>
 */
class HandoffToolTest {

    @Test
    void buildsTransferToolCardForTarget() {
        HandoffTool tool = new HandoffTool("billing", "Handles invoices.");

        assertThat(tool.getTargetId()).isEqualTo("billing");
        assertThat(tool.getCard().getId()).isEqualTo("transfer_to_billing");
        assertThat(tool.getCard().getName()).isEqualTo("transfer_to_billing");
        assertThat(tool.getCard().getDescription())
                .isEqualTo("Transfer the current task to billing for processing. Handles invoices.");
        assertThat(tool.getCard().getInputParams()).containsEntry("required", java.util.List.of("reason"));
    }

    @Test
    void invokesWithMapInputs() {
        HandoffTool tool = new HandoffTool("agent_b");

        Map<String, Object> payload = tool.invokePayload(Map.of("reason", "needs help", "message", "context"));

        assertThat(payload).containsEntry(HandoffSignal.HANDOFF_TARGET_KEY, "agent_b")
                .containsEntry(HandoffSignal.HANDOFF_MESSAGE_KEY, "context")
                .containsEntry(HandoffSignal.HANDOFF_REASON_KEY, "needs help");
    }

    @Test
    void parsesJsonStringAndFallsBackPlainStringToReason() {
        HandoffTool tool = new HandoffTool("agent_b");

        Map<String, Object> jsonPayload = tool.invokePayload("{\"reason\":\"handoff\",\"message\":\"ctx\"}");
        Map<String, Object> plainPayload = tool.invokePayload("plain reason");
        Map<String, Object> listPayload = tool.invokePayload("[1,2]");

        assertThat(jsonPayload).containsEntry(HandoffSignal.HANDOFF_REASON_KEY, "handoff")
                .containsEntry(HandoffSignal.HANDOFF_MESSAGE_KEY, "ctx");
        assertThat(plainPayload).containsEntry(HandoffSignal.HANDOFF_REASON_KEY, "plain reason")
                .containsEntry(HandoffSignal.HANDOFF_MESSAGE_KEY, "");
        assertThat(listPayload).containsEntry(HandoffSignal.HANDOFF_REASON_KEY, "")
                .containsEntry(HandoffSignal.HANDOFF_MESSAGE_KEY, "");
    }

    @Test
    void streamYieldsSingleInvokePayload() {
        HandoffTool tool = new HandoffTool("agent_b");

        Iterator<Map<String, Object>> iterator = tool.streamPayload(Map.of("reason", "route"));

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).containsEntry(HandoffSignal.HANDOFF_REASON_KEY, "route");
        assertThat(iterator.hasNext()).isFalse();
    }
}
