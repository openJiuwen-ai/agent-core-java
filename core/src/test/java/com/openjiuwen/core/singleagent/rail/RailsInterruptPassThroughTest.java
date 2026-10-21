/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.session.interaction.AgentInterrupt;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * UT-R09: human-in-the-loop interruption exceptions must pass through the
 * retry ring untouched, never reaching classification or retry.
 *
 * @since 2026-10-19
 */
class RailsInterruptPassThroughTest {
    @Test
    void run_agentInterrupt_passesThroughWithoutRetry() {
        // Given an operation that always throws AgentInterrupt and a context that
        // would request a retry for any exception event, When the ring runs,
        // Then the interrupt propagates immediately and the operation ran once
        AtomicInteger executions = new AtomicInteger();
        AgentCallbackContext ctx = new AgentCallbackContext() {
            @Override
            public void fire(AgentCallbackEvent event) {
                if (event == AgentCallbackEvent.ON_MODEL_EXCEPTION) {
                    requestRetry(0.0D);
                }
            }
        };

        assertThatThrownBy(() -> Rails.run(ctx,
            AgentCallbackEvent.BEFORE_MODEL_CALL,
            AgentCallbackEvent.AFTER_MODEL_CALL,
            AgentCallbackEvent.ON_MODEL_EXCEPTION,
            () -> {
                executions.incrementAndGet();
                throw new AgentInterrupt("need human input");
            }))
            .isInstanceOf(AgentInterrupt.class);
        assertThat(executions).hasValue(1);
    }

    @Test
    void run_abortError_passesThroughWithoutRetry() {
        AtomicInteger executions = new AtomicInteger();
        AgentCallbackContext ctx = new AgentCallbackContext() {
            @Override
            public void fire(AgentCallbackEvent event) {
                if (event == AgentCallbackEvent.ON_MODEL_EXCEPTION) {
                    requestRetry(0.0D);
                }
            }
        };

        assertThatThrownBy(() -> Rails.run(ctx,
            AgentCallbackEvent.BEFORE_MODEL_CALL,
            AgentCallbackEvent.AFTER_MODEL_CALL,
            AgentCallbackEvent.ON_MODEL_EXCEPTION,
            () -> {
                executions.incrementAndGet();
                throw new AbortError("interrupt", new AgentInterrupt("need human input"));
            }))
            .isInstanceOf(AbortError.class);
        assertThat(executions).hasValue(1);
    }
}
