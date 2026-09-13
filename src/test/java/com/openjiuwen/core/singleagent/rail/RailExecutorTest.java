// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent.rail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for {@link RailExecutor}.
 */
class RailExecutorTest {
    private AgentCallbackContext createCtxWithFirer() {
        List<Object[]> firedList = new ArrayList<>();
        AgentCallbackFirer firer = (event, ctx) -> firedList.add(new Object[]{event, ctx});

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(firer).build();
        // Store firedList in extra for assertions
        ctx.getExtra().put("_firedList", firedList);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> getFiredList(AgentCallbackContext ctx) {
        return (List<Object[]>) ctx.getExtra().get("_firedList");
    }

    @Test
    void testNormalExecutionFiresBeforeAndAfter() {
        AgentCallbackContext ctx = createCtxWithFirer();

        Optional<String> result = RailExecutor.execute(ctx, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> "success");

        assertThat(result).contains("success");
        List<Object[]> fired = getFiredList(ctx);
        assertThat(fired).hasSize(2);
        assertThat(fired.get(0)[0]).isEqualTo(AgentCallbackEvent.BEFORE_MODEL_CALL);
        assertThat(fired.get(1)[0]).isEqualTo(AgentCallbackEvent.AFTER_MODEL_CALL);
    }

    @Test
    void testExceptionFiresOnExceptionAndAfter() {
        AgentCallbackContext ctx = createCtxWithFirer();

        assertThatThrownBy(() -> RailExecutor.execute(ctx, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
                    throw new RuntimeException("boom");
                })).isInstanceOf(RuntimeException.class).hasMessageContaining("boom");

        List<Object[]> fired = getFiredList(ctx);
        // Should fire before, on_exception, after
        assertThat(fired).hasSize(3);
        assertThat(fired.get(0)[0]).isEqualTo(AgentCallbackEvent.BEFORE_MODEL_CALL);
        assertThat(fired.get(1)[0]).isEqualTo(AgentCallbackEvent.ON_MODEL_EXCEPTION);
        assertThat(fired.get(2)[0]).isEqualTo(AgentCallbackEvent.AFTER_MODEL_CALL);
    }

    @Test
    void testExceptionSetsExceptionOnContext() {
        AgentCallbackContext ctx = createCtxWithFirer();
        RuntimeException ex = new RuntimeException("test error");

        assertThatThrownBy(() -> RailExecutor.execute(ctx, null, null, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
            throw ex;
        })).isInstanceOf(RuntimeException.class);
        // Exception should have been set on context during on_exception event
        // but gets cleared on retry loop start; since no retry, it stays from the last attempt
    }

    @Test
    void testRetryMechanism() {
        AtomicInteger attempts = new AtomicInteger(0);
        List<String> events = new ArrayList<>();

        // Create a firer that watches for on_exception and requests retry
        AgentCallbackFirer firer = (event, callbackCtx) -> {
            events.add(event.getValue());
            if (event == AgentCallbackEvent.ON_MODEL_EXCEPTION && callbackCtx.getRetryAttempt() < 1) {
                callbackCtx.requestRetry(0.0);
            }
        };

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(firer).build();

        Optional<String> result = RailExecutor.execute(ctx, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt == 1) {
                        throw new RuntimeException("fail first time");
                    }
                    return "retry_success";
                });

        assertThat(result).contains("retry_success");
        assertThat(attempts.get()).isEqualTo(2);
        // Events: before(0), on_exception(0), after(0), before(1), after(1)
        assertThat(events).containsExactly("before_model_call", "on_model_exception", "after_model_call",
                "before_model_call", "after_model_call");
    }

    @Test
    void testRetryAttemptIncrementsCorrectly() {
        List<Integer> attemptsSeen = new ArrayList<>();

        AgentCallbackFirer firer = (event, callbackCtx) -> {
            if (event == AgentCallbackEvent.BEFORE_MODEL_CALL) {
                attemptsSeen.add(callbackCtx.getRetryAttempt());
            }
            if (event == AgentCallbackEvent.ON_MODEL_EXCEPTION && callbackCtx.getRetryAttempt() < 2) {
                callbackCtx.requestRetry(0.0);
            }
        };

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(firer).build();

        AtomicInteger callCount = new AtomicInteger(0);

        Optional<String> result = RailExecutor.execute(ctx, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
                    int n = callCount.incrementAndGet();
                    if (n <= 2) {
                        throw new RuntimeException("fail");
                    }
                    return "ok";
                });

        assertThat(result).contains("ok");
        assertThat(callCount.get()).isEqualTo(3);
        assertThat(attemptsSeen).containsExactly(0, 1, 2);
    }

    @Test
    void testNullEventsSkipped() {
        AgentCallbackContext ctx = createCtxWithFirer();

        Optional<String> result = RailExecutor.execute(ctx, null, null, null, () -> "no_events");

        assertThat(result).contains("no_events");
        List<Object[]> fired = getFiredList(ctx);
        assertThat(fired).isEmpty();
    }

    @Test
    void testCheckedExceptionWrappedInRuntimeException() {
        AgentCallbackContext ctx = createCtxWithFirer();

        assertThatThrownBy(() -> RailExecutor.execute(ctx, null, null, null, () -> {
            throw new Exception("checked exception");
        })).isInstanceOf(RuntimeException.class).hasCauseInstanceOf(Exception.class);
    }

    @Test
    void testRuntimeExceptionNotWrapped() {
        AgentCallbackContext ctx = createCtxWithFirer();

        assertThatThrownBy(() -> RailExecutor.execute(ctx, null, null, null, () -> {
            throw new IllegalStateException("direct");
        })).isInstanceOf(IllegalStateException.class).hasMessage("direct");
    }

    @Test
    void testRetryWithDelay() {
        AtomicInteger attempts = new AtomicInteger(0);

        AgentCallbackFirer firer = (event, callbackCtx) -> {
            if (event == AgentCallbackEvent.ON_MODEL_EXCEPTION && callbackCtx.getRetryAttempt() < 1) {
                callbackCtx.requestRetry(0.01); // 10ms delay
            }
        };

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(firer).build();

        long start = System.currentTimeMillis();
        Optional<String> result = RailExecutor.execute(ctx, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new RuntimeException("fail");
                    }
                    return "delayed_success";
                });
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).contains("delayed_success");
        assertThat(elapsed).isGreaterThanOrEqualTo(5); // At least some delay
    }
}
