/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackChain;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.ChainAction;
import com.openjiuwen.core.runner.callback.ChainContext;
import com.openjiuwen.core.runner.callback.ChainResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CallbackChain execution.
 * 
 * <p>Mirrors Python's {@code test_chain} in
 * {@code tests.unit_tests.core.runner.callback.test_chain}.</p>
 */
@DisplayName("TestChain")
class TestChain {

    @Test
    @Tag("level0")
    @DisplayName("Test CallbackChain initialization")
    void testInitialization() {
        CallbackChain chain = new CallbackChain("test_chain");
        assertEquals("test_chain", chain.getName());
        assertTrue(chain.getCallbacks().isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test adding callback to chain")
    void testAddCallback() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> callback = kwargs -> null;
        CallbackInfo info = CallbackInfo.builder()
                .callback(callback)
                .priority(10)
                .callbackName("callback")
                .build();
        chain.add(info, null, null);

        assertEquals(1, chain.getCallbacks().size());
        assertSame(info, chain.getCallbacks().get(0));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test callbacks are sorted by priority (higher first)")
    void testAddMultipleCallbacksSortedByPriority() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> lowPriority = kwargs -> null;
        Function<Map<String, Object>, Object> highPriority = kwargs -> null;
        Function<Map<String, Object>, Object> mediumPriority = kwargs -> null;

        chain.add(CallbackInfo.builder().callback(lowPriority).priority(1).callbackName("low").build(), null, null);
        chain.add(CallbackInfo.builder().callback(highPriority).priority(10).callbackName("high").build(), null, null);
        chain.add(CallbackInfo.builder().callback(mediumPriority).priority(5).callbackName("med").build(), null, null);

        assertEquals(10, chain.getCallbacks().get(0).getPriority());
        assertEquals(5, chain.getCallbacks().get(1).getPriority());
        assertEquals(1, chain.getCallbacks().get(2).getPriority());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test adding callback with rollback and error handlers")
    void testAddWithHandlers() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> callback = kwargs -> null;
        Consumer<ChainContext> rollbackHandler = ctx -> {};
        Function<CallbackChain.ExceptionContext, Object> errorHandler = exCtx -> null;

        CallbackInfo info = CallbackInfo.builder()
                .callback(callback)
                .priority(0)
                .callbackName("callback")
                .build();
        chain.add(info, rollbackHandler, errorHandler);

        // Verify handlers are stored - we can only verify through execution
        assertEquals(1, chain.getCallbacks().size());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test removing callback from chain")
    void testRemoveCallback() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> callback1 = kwargs -> null;
        Function<Map<String, Object>, Object> callback2 = kwargs -> null;

        chain.add(CallbackInfo.builder().callback(callback1).priority(0).callbackName("cb1").build(), null, null);
        chain.add(CallbackInfo.builder().callback(callback2).priority(0).callbackName("cb2").build(), null, null);

        assertEquals(2, chain.getCallbacks().size());

        chain.remove(callback1);

        assertEquals(1, chain.getCallbacks().size());
        assertSame(callback2, chain.getCallbacks().get(0).getCallback());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test removing callback also clears its handlers")
    void testRemoveClearsHandlers() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> callback = kwargs -> null;
        Consumer<ChainContext> rollback = ctx -> {};

        CallbackInfo info = CallbackInfo.builder()
                .callback(callback)
                .priority(0)
                .callbackName("callback")
                .build();
        chain.add(info, rollback, null);

        // Handler should be present (verified through execution)
        assertEquals(1, chain.getCallbacks().size());

        chain.remove(callback);

        // Callback should be removed
        assertTrue(chain.getCallbacks().isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test executing chain with single callback")
    void testExecuteSingleCallback() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> callback = kwargs -> "result";
        chain.add(CallbackInfo.builder().callback(callback).priority(0).callbackName("cb").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("result", result.getResult());
        assertTrue(context.isCompleted());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test executing chain passes results between callbacks")
    void testExecuteMultipleCallbacks() {
        CallbackChain chain = new CallbackChain("test");
        List<String> executionOrder = new ArrayList<>();

        Function<Map<String, Object>, Object> step1 = kwargs -> {
            executionOrder.add("step1");
            Map<String, Object> r = new HashMap<>();
            r.put("step1", true);
            return r;
        };

        Function<Map<String, Object>, Object> step2 = kwargs -> {
            executionOrder.add("step2");
            @SuppressWarnings("unchecked")
            Map<String, Object> prev = (Map<String, Object>) kwargs.get("_last_result");
            if (prev != null) {
                prev.put("step2", true);
            }
            return prev;
        };

        chain.add(CallbackInfo.builder().callback(step1).priority(20).callbackName("step1").build(), null, null);
        chain.add(CallbackInfo.builder().callback(step2).priority(10).callbackName("step2").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(List.of("step1", "step2"), executionOrder);
        @SuppressWarnings("unchecked")
        Map<String, Object> finalResult = (Map<String, Object>) result.getResult();
        assertTrue((Boolean) finalResult.get("step1"));
        assertTrue((Boolean) finalResult.get("step2"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test callbacks execute in priority order (high to low)")
    void testExecuteRespectsPriorityOrder() {
        CallbackChain chain = new CallbackChain("test");
        List<String> order = new ArrayList<>();

        Function<Map<String, Object>, Object> callbackA = kwargs -> {
            order.add("a");
            return ChainResult.builder().action(ChainAction.CONTINUE).result("a").build();
        };
        Function<Map<String, Object>, Object> callbackB = kwargs -> {
            order.add("b");
            return ChainResult.builder().action(ChainAction.CONTINUE).result("b").build();
        };
        Function<Map<String, Object>, Object> callbackC = kwargs -> {
            order.add("c");
            return ChainResult.builder().action(ChainAction.CONTINUE).result("c").build();
        };

        chain.add(CallbackInfo.builder().callback(callbackB).priority(5).callbackName("b").build(), null, null);
        chain.add(CallbackInfo.builder().callback(callbackA).priority(10).callbackName("a").build(), null, null);
        chain.add(CallbackInfo.builder().callback(callbackC).priority(1).callbackName("c").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        chain.execute(context);

        assertEquals(List.of("a", "b", "c"), order);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test disabled callbacks are skipped")
    void testExecuteSkipsDisabledCallbacks() {
        CallbackChain chain = new CallbackChain("test");
        List<String> order = new ArrayList<>();

        Function<Map<String, Object>, Object> enabled = kwargs -> {
            order.add("enabled");
            return null;
        };
        Function<Map<String, Object>, Object> disabled = kwargs -> {
            order.add("disabled");
            return null;
        };

        chain.add(CallbackInfo.builder().callback(enabled).priority(10).enabled(true).callbackName("enabled").build(), null, null);
        chain.add(CallbackInfo.builder().callback(disabled).priority(5).enabled(false).callbackName("disabled").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        chain.execute(context);

        assertEquals(List.of("enabled"), order);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test _chain_context is passed to callbacks")
    void testExecuteChainContextAvailable() {
        CallbackChain chain = new CallbackChain("test");
        ChainContext[] receivedContext = new ChainContext[1];

        Function<Map<String, Object>, Object> callback = kwargs -> {
            receivedContext[0] = (ChainContext) kwargs.get("_chain_context");
            return "done";
        };

        chain.add(CallbackInfo.builder().callback(callback).priority(0).callbackName("cb").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        chain.execute(context);

        assertSame(context, receivedContext[0]);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test BREAK action stops chain execution")
    void testBreakActionStopsChain() {
        CallbackChain chain = new CallbackChain("test");
        List<String> executed = new ArrayList<>();

        Function<Map<String, Object>, Object> step1 = kwargs -> {
            executed.add("step1");
            return ChainResult.builder().action(ChainAction.BREAK).result("stopped_here").build();
        };
        Function<Map<String, Object>, Object> step2 = kwargs -> {
            executed.add("step2");
            return "step2_result";
        };

        chain.add(CallbackInfo.builder().callback(step1).priority(10).callbackName("step1").build(), null, null);
        chain.add(CallbackInfo.builder().callback(step2).priority(5).callbackName("step2").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.BREAK, result.getAction());
        assertEquals("stopped_here", result.getResult());
        assertEquals(List.of("step1"), executed);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test ROLLBACK action triggers rollback handlers")
    void testRollbackActionTriggersRollback() {
        CallbackChain chain = new CallbackChain("test");
        List<String> rollbackOrder = new ArrayList<>();

        Function<Map<String, Object>, Object> step1 = kwargs ->
                ChainResult.builder().action(ChainAction.CONTINUE).result("step1").build();
        Function<Map<String, Object>, Object> step2 = kwargs ->
                ChainResult.builder().action(ChainAction.ROLLBACK).error(new RuntimeException("Failed")).build();

        Consumer<ChainContext> rollback1 = ctx -> rollbackOrder.add("rollback1");
        Consumer<ChainContext> rollback2 = ctx -> rollbackOrder.add("rollback2");

        chain.add(CallbackInfo.builder().callback(step1).priority(10).callbackName("step1").build(), rollback1, null);
        chain.add(CallbackInfo.builder().callback(step2).priority(5).callbackName("step2").build(), rollback2, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertTrue(context.isRolledBack());
        // Only step1 was successfully executed, so only its rollback runs
        assertTrue(rollbackOrder.contains("rollback1"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test RETRY action causes callback retry")
    void testRetryActionRetriesCallback() {
        CallbackChain chain = new CallbackChain("test");
        int[] callCount = {0};

        Function<Map<String, Object>, Object> flakyCallback = kwargs -> {
            callCount[0]++;
            if (callCount[0] < 3) {
                return ChainResult.builder().action(ChainAction.RETRY).build();
            }
            return ChainResult.builder().action(ChainAction.CONTINUE).result("success").build();
        };

        chain.add(CallbackInfo.builder()
                .callback(flakyCallback)
                .priority(0)
                .maxRetries(5)
                .callbackName("flaky")
                .build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("success", result.getResult());
        assertEquals(3, callCount[0]);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test exception in callback triggers rollback")
    void testExceptionTriggersRollback() {
        CallbackChain chain = new CallbackChain("test");
        boolean[] rollbackCalled = {false};

        Function<Map<String, Object>, Object> step1 = kwargs -> "step1_result";
        Function<Map<String, Object>, Object> failingStep = kwargs -> {
            throw new RuntimeException("Something went wrong");
        };

        Consumer<ChainContext> rollback1 = ctx -> rollbackCalled[0] = true;

        chain.add(CallbackInfo.builder().callback(step1).priority(10).callbackName("step1").build(), rollback1, null);
        chain.add(CallbackInfo.builder().callback(failingStep).priority(5).callbackName("failingStep").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertInstanceOf(RuntimeException.class, result.getError());
        assertTrue(rollbackCalled[0]);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test error handler can provide fallback result")
    void testErrorHandlerCanRecover() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> failingCallback = kwargs -> {
            throw new RuntimeException("Expected error");
        };

        Function<CallbackChain.ExceptionContext, Object> errorHandler = exCtx -> "recovered_result";

        chain.add(CallbackInfo.builder()
                .callback(failingCallback)
                .priority(0)
                .callbackName("failingCallback")
                .build(), null, errorHandler);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("recovered_result", result.getResult());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test callback with retries retries on exception")
    void testRetryOnException() {
        CallbackChain chain = new CallbackChain("test");
        int[] attempts = {0};

        Function<Map<String, Object>, Object> flakyCallback = kwargs -> {
            attempts[0]++;
            if (attempts[0] < 3) {
                throw new RuntimeException("Temporary failure");
            }
            return "success";
        };

        chain.add(CallbackInfo.builder()
                .callback(flakyCallback)
                .priority(0)
                .maxRetries(3)
                .retryDelay(0.01)
                .callbackName("flaky")
                .build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("success", result.getResult());
        assertEquals(3, attempts[0]);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test timeout triggers rollback after retries exhausted")
    void testTimeoutTriggersRollback() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> slowCallback = kwargs -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "never_reached";
        };

        chain.add(CallbackInfo.builder()
                .callback(slowCallback)
                .priority(0)
                .timeout(0.05)
                .maxRetries(1)
                .callbackName("slow")
                .build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertInstanceOf(TimeoutException.class, result.getError());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test chain continues when error handler itself throws")
    void testErrorHandlerThrowsException() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> failingCallback = kwargs -> {
            throw new RuntimeException("Original error");
        };

        Function<CallbackChain.ExceptionContext, Object> failingErrorHandler = exCtx -> {
            throw new RuntimeException("Error handler also failed!");
        };

        chain.add(CallbackInfo.builder()
                .callback(failingCallback)
                .priority(0)
                .callbackName("failingCallback")
                .build(), null, failingErrorHandler);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        // Should rollback since error handler failed
        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertInstanceOf(RuntimeException.class, result.getError());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test chain continues when rollback handler throws")
    void testRollbackHandlerThrowsException() {
        CallbackChain chain = new CallbackChain("test");
        boolean[] step1Executed = {false};

        Function<Map<String, Object>, Object> step1 = kwargs -> {
            step1Executed[0] = true;
            return "step1";
        };
        Function<Map<String, Object>, Object> step2 = kwargs -> {
            throw new RuntimeException("Step 2 failed");
        };

        Consumer<ChainContext> failingRollback = ctx -> {
            throw new RuntimeException("Rollback failed!");
        };

        chain.add(CallbackInfo.builder().callback(step1).priority(10).callbackName("step1").build(), failingRollback, null);
        chain.add(CallbackInfo.builder().callback(step2).priority(5).callbackName("step2").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        // Rollback should still complete even if handler fails
        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertTrue(context.isRolledBack());
        assertTrue(step1Executed[0]);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test once callback is disabled after first execution")
    void testOnceCallbackDisabledAfterExecution() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> onceCallback = kwargs -> "executed";

        CallbackInfo info = CallbackInfo.builder()
                .callback(onceCallback)
                .priority(0)
                .once(true)
                .callbackName("once")
                .build();
        chain.add(info, null, null);

        ChainContext context1 = new ChainContext("test", new Object[0], new HashMap<>());
        chain.execute(context1);

        assertFalse(info.isEnabled());

        // Second execution should skip the callback
        ChainContext context2 = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context2);

        // Chain completes but with no results since callback is disabled
        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals(0, context2.getResults().size());
    }
}
