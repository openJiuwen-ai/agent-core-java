/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Focused tests for rail decorator support.
 *
 * <p>Mirrors Python's {@code rail} decorator in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
class RailsTest {

    @Test
    void forceFinishFromBeforeReturnsRequestedResultAndSkipsBody() {
        RecordingContext context = new RecordingContext();
        context.onBefore = () -> context.requestForceFinish(Map.of("output", "blocked", "result_type", "answer"));
        AtomicInteger calls = new AtomicInteger();

        Object result = Rails.run(
                context,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    calls.incrementAndGet();
                    return Map.of("output", "body");
                }
        );

        assertThat(result).isEqualTo(Map.of("output", "blocked", "result_type", "answer"));
        assertThat(calls).hasValue(0);
        assertThat(context.events).containsExactly(
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL
        );
    }

    @Test
    void cancellationSkipsAfterAndExceptionCallbacks() {
        RecordingContext context = new RecordingContext();

        assertThatThrownBy(() -> Rails.run(
                context,
                AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL,
                AgentCallbackEvent.ON_TOOL_EXCEPTION,
                () -> {
                    throw new CancellationException("cancelled");
                }
        )).isInstanceOf(CancellationException.class);

        assertThat(context.events).containsExactly(AgentCallbackEvent.BEFORE_TOOL_CALL);
    }

    @Test
    void exceptionHookCanRequestRetry() {
        RecordingContext context = new RecordingContext();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Integer> retryAttemptAtException = new AtomicReference<>();
        context.onException = () -> {
            retryAttemptAtException.set(context.getRetryAttempt());
            context.requestRetry(0.0D);
        };

        Object result = Rails.run(
                context,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    if (calls.getAndIncrement() == 0) {
                        throw new IllegalStateException("transient");
                    }
                    return "ok";
                }
        );

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        assertThat(retryAttemptAtException).hasValue(0);
        assertThat(context.getRetryAttempt()).isEqualTo(1);
        assertThat(context.events).containsExactly(
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL
        );
    }

    private static final class RecordingContext extends AgentCallbackContext {
        private final List<AgentCallbackEvent> events = new ArrayList<>();
        private Runnable onBefore;
        private Runnable onException;

        @Override
        public void fire(AgentCallbackEvent event) {
            events.add(event);
            if (AgentCallbackEvent.BEFORE_MODEL_CALL == event && onBefore != null) {
                onBefore.run();
            }
            if (AgentCallbackEvent.ON_MODEL_EXCEPTION == event && onException != null) {
                onException.run();
            }
        }
    }
}
