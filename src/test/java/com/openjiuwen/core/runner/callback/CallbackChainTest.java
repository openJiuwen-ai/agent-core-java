// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for callback chain execution.
 *
 * <p>Mirrors Python's {@code test_chain.py} in
 * {@code tests/unit_tests/core/runner/callback/test_chain.py}.</p>
 */
@DisplayName("Callback Chain Tests")
class CallbackChainTest {

    @Test
    @DisplayName("CallbackChain initialization")
    void testInitialization() {
        CallbackChain chain = new CallbackChain("test_chain");
        assertEquals("test_chain", chain.getName());
        assertTrue(chain.getCallbacks().isEmpty());
        assertTrue(chain.getRollbackHandlers().isEmpty());
        assertTrue(chain.getErrorHandlers().isEmpty());
    }

    @Test
    @DisplayName("Add callback")
    void testAddCallback() {
        CallbackChain chain = new CallbackChain();
        Function<Map<String, Object>, Object> callback = kwargs -> null;

        CallbackInfo info = CallbackInfo.builder().callback(callback).priority(10).build();
        chain.add(info);

        assertEquals(1, chain.getCallbacks().size());
        assertSame(info, chain.getCallbacks().get(0));
    }

    @Test
    @DisplayName("Add multiple callbacks sorted by priority")
    void testAddMultipleCallbacksSortedByPriority() {
        CallbackChain chain = new CallbackChain();

        Function<Map<String, Object>, Object> lowPriority = kwargs -> null;
        Function<Map<String, Object>, Object> highPriority = kwargs -> null;
        Function<Map<String, Object>, Object> mediumPriority = kwargs -> null;

        chain.add(CallbackInfo.builder().callback(lowPriority).priority(1).build());
        chain.add(CallbackInfo.builder().callback(highPriority).priority(10).build());
        chain.add(CallbackInfo.builder().callback(mediumPriority).priority(5).build());

        assertEquals(10, chain.getCallbacks().get(0).getPriority());
        assertEquals(5, chain.getCallbacks().get(1).getPriority());
        assertEquals(1, chain.getCallbacks().get(2).getPriority());
    }

    @Test
    @DisplayName("Add with handlers")
    void testAddWithHandlers() {
        CallbackChain chain = new CallbackChain();
        Function<Map<String, Object>, Object> callback = kwargs -> null;
        Function<ChainContext, Object> rollbackHandler = context -> null;
        BiFunction<Exception, ChainContext, Object> errorHandler = (error, context) -> null;

        CallbackInfo info = CallbackInfo.builder().callback(callback).priority(0).build();
        chain.add(info, rollbackHandler, errorHandler);

        assertSame(rollbackHandler, chain.getRollbackHandlers().get(callback));
        assertSame(errorHandler, chain.getErrorHandlers().get(callback));
    }

    @Test
    @DisplayName("Remove callback")
    void testRemoveCallback() {
        CallbackChain chain = new CallbackChain();

        Function<Map<String, Object>, Object> callback1 = kwargs -> null;
        Function<Map<String, Object>, Object> callback2 = kwargs -> null;

        chain.add(CallbackInfo.builder().callback(callback1).priority(0).build());
        chain.add(CallbackInfo.builder().callback(callback2).priority(0).build());
        assertEquals(2, chain.getCallbacks().size());

        chain.remove(callback1);

        assertEquals(1, chain.getCallbacks().size());
        assertSame(callback2, chain.getCallbacks().get(0).getCallback());
    }

    @Test
    @DisplayName("Remove clears handlers")
    void testRemoveClearsHandlers() {
        CallbackChain chain = new CallbackChain();

        Function<Map<String, Object>, Object> callback = kwargs -> null;
        Function<ChainContext, Object> rollbackHandler = context -> null;

        chain.add(CallbackInfo.builder().callback(callback).priority(0).build(), rollbackHandler);
        assertTrue(chain.getRollbackHandlers().containsKey(callback));

        chain.remove(callback);

        assertFalse(chain.getRollbackHandlers().containsKey(callback));
    }

    @Test
    @DisplayName("Execute single callback")
    void testExecuteSingleCallback() {
        CallbackChain chain = new CallbackChain();

        chain.add(CallbackInfo.builder()
                .callback(kwargs -> "result")
                .priority(0)
                .build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("result", result.getResult());
        assertTrue(context.isCompleted());
    }

    @Test
    @DisplayName("Named callback without handlers propagates successful result")
    void namedCallbackWithoutHandlersPropagatesSuccessfulResult() {
        CallbackChain chain = new CallbackChain();
        chain.add(CallbackInfo.builder()
                .callback(kwargs -> continueResult("payment_completed"))
                .callbackName("process_payment")
                .priority(0)
                .build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals("payment_completed", result.getResult());
        assertEquals(List.of("payment_completed"), context.getResults());
    }

    @Test
    @DisplayName("Execute multiple callbacks")
    void testExecuteMultipleCallbacks() {
        CallbackChain chain = new CallbackChain();
        List<String> executionOrder = new ArrayList<>();

        Function<Map<String, Object>, Object> step1 = kwargs -> {
            executionOrder.add("step1");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("step1", true);
            return result;
        };

        Function<Map<String, Object>, Object> step2 = kwargs -> {
            executionOrder.add("step2");
            @SuppressWarnings("unchecked")
            Map<String, Object> previousResult = (Map<String, Object>) kwargs.get("_last_result");
            previousResult.put("step2", true);
            return previousResult;
        };

        chain.add(CallbackInfo.builder().callback(step1).priority(20).build());
        chain.add(CallbackInfo.builder().callback(step2).priority(10).build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(List.of("step1", "step2"), executionOrder);
        assertEquals(Map.of("step1", true, "step2", true), result.getResult());
    }

    @Test
    @DisplayName("Execute respects priority order")
    void testExecuteRespectsPriorityOrder() {
        CallbackChain chain = new CallbackChain();
        List<String> order = new ArrayList<>();

        Function<Map<String, Object>, Object> callbackA = kwargs -> {
            order.add("a");
            return continueResult("a");
        };
        Function<Map<String, Object>, Object> callbackB = kwargs -> {
            order.add("b");
            return continueResult("b");
        };
        Function<Map<String, Object>, Object> callbackC = kwargs -> {
            order.add("c");
            return continueResult("c");
        };

        chain.add(CallbackInfo.builder().callback(callbackB).priority(5).build());
        chain.add(CallbackInfo.builder().callback(callbackA).priority(10).build());
        chain.add(CallbackInfo.builder().callback(callbackC).priority(1).build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        chain.execute(context).join();

        assertEquals(List.of("a", "b", "c"), order);
    }

    @Test
    @DisplayName("Execute skips disabled callbacks")
    void testExecuteSkipsDisabledCallbacks() {
        CallbackChain chain = new CallbackChain();
        List<String> order = new ArrayList<>();

        Function<Map<String, Object>, Object> enabled = kwargs -> {
            order.add("enabled");
            return null;
        };
        Function<Map<String, Object>, Object> disabled = kwargs -> {
            order.add("disabled");
            return null;
        };

        chain.add(CallbackInfo.builder().callback(enabled).priority(10).enabled(true).build());
        chain.add(CallbackInfo.builder().callback(disabled).priority(5).enabled(false).build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        chain.execute(context).join();

        assertEquals(List.of("enabled"), order);
    }

    @Test
    @DisplayName("Execute chain context available")
    void testExecuteChainContextAvailable() {
        CallbackChain chain = new CallbackChain();
        AtomicReferenceBox<ChainContext> receivedContext = new AtomicReferenceBox<>();

        chain.add(CallbackInfo.builder()
                .callback(kwargs -> {
                    receivedContext.set((ChainContext) kwargs.get("_chain_context"));
                    return "done";
                })
                .priority(0)
                .build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        chain.execute(context).join();

        assertSame(context, receivedContext.get());
    }

    @Test
    @DisplayName("Break action stops chain")
    void testBreakActionStopsChain() {
        CallbackChain chain = new CallbackChain();
        List<String> executed = new ArrayList<>();

        Function<Map<String, Object>, Object> step1 = kwargs -> {
            executed.add("step1");
            return ChainResult.builder()
                    .action(ChainAction.BREAK)
                    .result("stopped_here")
                    .build();
        };
        Function<Map<String, Object>, Object> step2 = kwargs -> {
            executed.add("step2");
            return "step2_result";
        };

        chain.add(CallbackInfo.builder().callback(step1).priority(10).build());
        chain.add(CallbackInfo.builder().callback(step2).priority(5).build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.BREAK, result.getAction());
        assertEquals("stopped_here", result.getResult());
        assertEquals(List.of("step1"), executed);
    }

    @Test
    @DisplayName("Rollback action triggers rollback")
    void testRollbackActionTriggersRollback() {
        CallbackChain chain = new CallbackChain();
        List<String> rollbackOrder = new ArrayList<>();

        Function<Map<String, Object>, Object> step1 = kwargs -> continueResult("step1");
        Function<Map<String, Object>, Object> step2 = kwargs -> ChainResult.builder()
                .action(ChainAction.ROLLBACK)
                .error(new Exception("Failed"))
                .build();
        Function<ChainContext, Object> rollback1 = context -> {
            rollbackOrder.add("rollback1");
            return null;
        };
        Function<ChainContext, Object> rollback2 = context -> {
            rollbackOrder.add("rollback2");
            return null;
        };

        chain.add(CallbackInfo.builder().callback(step1).priority(10).build(), rollback1);
        chain.add(CallbackInfo.builder().callback(step2).priority(5).build(), rollback2);

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertTrue(context.isRolledBack());
        assertEquals(List.of("rollback1"), rollbackOrder);
    }

    @Test
    @DisplayName("Retry action retries callback")
    void testRetryActionRetriesCallback() {
        CallbackChain chain = new CallbackChain();
        AtomicInteger callCount = new AtomicInteger();

        Function<Map<String, Object>, Object> flakyCallback = kwargs -> {
            int currentCount = callCount.incrementAndGet();
            if (currentCount < 3) {
                return ChainResult.builder().action(ChainAction.RETRY).build();
            }
            return continueResult("success");
        };

        chain.add(CallbackInfo.builder()
                .callback(flakyCallback)
                .priority(0)
                .maxRetries(5)
                .build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("success", result.getResult());
        assertEquals(3, callCount.get());
    }

    @Test
    @DisplayName("Exception triggers rollback")
    void testExceptionTriggersRollback() {
        CallbackChain chain = new CallbackChain();
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);

        Function<Map<String, Object>, Object> step1 = kwargs -> "step1_result";
        Function<Map<String, Object>, Object> failingStep = kwargs -> {
            throw new RuntimeException("Something went wrong");
        };
        Function<ChainContext, Object> rollback1 = context -> {
            rollbackCalled.set(true);
            return null;
        };

        chain.add(CallbackInfo.builder().callback(step1).priority(10).build(), rollback1);
        chain.add(CallbackInfo.builder().callback(failingStep).priority(5).build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertInstanceOf(RuntimeException.class, result.getError());
        assertTrue(rollbackCalled.get());
    }

    @Test
    @DisplayName("Error handler can recover")
    void testErrorHandlerCanRecover() {
        CallbackChain chain = new CallbackChain();

        Function<Map<String, Object>, Object> failingCallback = kwargs -> {
            throw new IllegalArgumentException("Expected error");
        };
        BiFunction<Exception, ChainContext, Object> errorHandler = (error, context) -> "recovered_result";

        chain.add(CallbackInfo.builder().callback(failingCallback).priority(0).build(), null, errorHandler);

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("recovered_result", result.getResult());
    }

    @Test
    @DisplayName("Retry on exception")
    void testRetryOnException() {
        CallbackChain chain = new CallbackChain();
        AtomicInteger attempts = new AtomicInteger();

        Function<Map<String, Object>, Object> flakyCallback = kwargs -> {
            int currentAttempt = attempts.incrementAndGet();
            if (currentAttempt < 3) {
                throw new RuntimeException("Temporary failure");
            }
            return "success";
        };

        chain.add(CallbackInfo.builder()
                .callback(flakyCallback)
                .priority(0)
                .maxRetries(3)
                .retryDelay(0.01)
                .build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("success", result.getResult());
        assertEquals(3, attempts.get());
    }

    @Test
    @DisplayName("Timeout triggers rollback")
    void testTimeoutTriggersRollback() {
        CallbackChain chain = new CallbackChain();

        Function<Map<String, Object>, Object> slowCallback = kwargs ->
                CompletableFuture.supplyAsync(() -> {
                    sleepQuietly(200L);
                    return "never_reached";
                });

        chain.add(CallbackInfo.builder()
                .callback(slowCallback)
                .priority(0)
                .timeout(0.05)
                .maxRetries(1)
                .build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertInstanceOf(TimeoutException.class, result.getError());
    }

    @Test
    @DisplayName("Error handler throws exception")
    void testErrorHandlerThrowsException() {
        CallbackChain chain = new CallbackChain();

        Function<Map<String, Object>, Object> failingCallback = kwargs -> {
            throw new IllegalArgumentException("Original error");
        };
        BiFunction<Exception, ChainContext, Object> failingErrorHandler = (error, context) -> {
            throw new RuntimeException("Error handler also failed!");
        };

        chain.add(CallbackInfo.builder().callback(failingCallback).priority(0).build(), null, failingErrorHandler);

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertInstanceOf(IllegalArgumentException.class, result.getError());
    }

    @Test
    @DisplayName("Rollback handler throws exception")
    void testRollbackHandlerThrowsException() {
        CallbackChain chain = new CallbackChain();
        AtomicBoolean step1Executed = new AtomicBoolean(false);

        Function<Map<String, Object>, Object> step1 = kwargs -> {
            step1Executed.set(true);
            return "step1";
        };
        Function<Map<String, Object>, Object> step2 = kwargs -> {
            throw new IllegalArgumentException("Step 2 failed");
        };
        Function<ChainContext, Object> failingRollback = context -> {
            throw new RuntimeException("Rollback failed!");
        };

        chain.add(CallbackInfo.builder().callback(step1).priority(10).build(), failingRollback);
        chain.add(CallbackInfo.builder().callback(step2).priority(5).build());

        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context).join();

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertTrue(context.isRolledBack());
        assertTrue(step1Executed.get());
    }

    @Test
    @DisplayName("Once callback disabled after execution")
    void testOnceCallbackDisabledAfterExecution() {
        CallbackChain chain = new CallbackChain();

        Function<Map<String, Object>, Object> onceCallback = kwargs -> "executed";
        CallbackInfo info = CallbackInfo.builder().callback(onceCallback).priority(0).once(true).build();
        chain.add(info);

        ChainContext context1 = new ChainContext("test", new Object[0], Map.of());
        chain.execute(context1).join();

        assertFalse(info.isEnabled());

        ChainContext context2 = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = chain.execute(context2).join();

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertTrue(context2.getResults().isEmpty());
    }

    private static ChainResult continueResult(Object value) {
        return ChainResult.builder()
                .action(ChainAction.CONTINUE)
                .result(value)
                .build();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class AtomicReferenceBox<T> {

        private T value;

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }
    }
}
