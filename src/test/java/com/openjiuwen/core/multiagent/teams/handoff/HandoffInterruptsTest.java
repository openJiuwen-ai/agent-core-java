/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.session.interaction.AgentInterrupt;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for handoff interrupt signal extraction.
 *
 * <p>Mirrors Python's {@code TeamInterruptSignal} and {@code extract_interrupt_signal} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/interrupt.py}.</p>
 */
class HandoffInterruptsTest {

    @Test
    void extractsSignalFromInterruptResultMap() {
        Map<String, Object> result = Map.of("result_type", "interrupt", "message", "pause");

        Optional<TeamInterruptSignal> signal = HandoffInterrupts.extractInterruptSignal(result);

        assertThat(signal).isPresent();
        assertThat(signal.orElseThrow().getResult()).isEqualTo(result);
        assertThat(signal.orElseThrow().getMessage()).isEmpty();
    }

    @Test
    void extractsSignalFromAgentInterruptException() {
        Optional<TeamInterruptSignal> signal = HandoffInterrupts.extractInterruptSignal(
                null,
                new AgentInterrupt("need human input")
        );

        assertThat(signal).isPresent();
        assertThat(signal.orElseThrow().getMessage()).contains("need human input");
        assertThat(signal.orElseThrow().getResult()).isEqualTo(Map.of(
                "result_type", "interrupt",
                "message", "need human input"
        ));
    }

    @Test
    void returnsEmptyWhenNoInterruptIsDetected() {
        assertThat(HandoffInterrupts.extractInterruptSignal(Map.of("result_type", "normal"))).isEmpty();
        assertThat(HandoffInterrupts.extractInterruptSignal(null, new IllegalStateException("boom"))).isEmpty();
    }

    @Test
    void nullSessionFlushIsNoOp() {
        HandoffInterrupts.flushTeamSession(null);
    }
}
