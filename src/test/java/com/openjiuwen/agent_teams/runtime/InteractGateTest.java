/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InteractGateTest {

    @Test
    void admitTracksInflightUntilConsumeDone() {
        InteractGate gate = new InteractGate();

        AdmissionTicket ticket = gate.admit();

        assertThat(ticket).isNotNull();
        assertThat(gate.isClosed()).isFalse();
        assertThat(gate.getInflight()).isEqualTo(1);

        gate.consumeDone(ticket);

        assertThat(gate.getInflight()).isZero();
    }

    @Test
    void closeAndDrainRejectsNewAdmissionsAfterInflightCompletes() throws Exception {
        InteractGate gate = new InteractGate();
        AdmissionTicket ticket = gate.admit();

        CompletableFuture<Void> drainFuture = CompletableFuture.runAsync(gate::closeAndDrain);
        TimeUnit.MILLISECONDS.sleep(100);
        assertThat(drainFuture).isNotDone();
        assertThat(gate.admit()).isNull();

        gate.consumeDone(ticket);

        drainFuture.get(2, TimeUnit.SECONDS);
        assertThat(gate.isClosed()).isTrue();
        assertThat(gate.getInflight()).isZero();
    }

    @Test
    void resetReopensGateAndForeignTicketsAreIgnored() {
        InteractGate gate = new InteractGate();
        InteractGate other = new InteractGate();
        AdmissionTicket ticket = gate.admit();

        gate.consumeDone(new AdmissionTicket(other));
        assertThat(gate.getInflight()).isEqualTo(1);

        gate.consumeDone(ticket);
        gate.closeAndDrain();
        gate.reset();

        assertThat(gate.isClosed()).isFalse();
        assertThat(gate.getInflight()).isZero();
        assertThat(gate.admit()).isNotNull();
    }
}
