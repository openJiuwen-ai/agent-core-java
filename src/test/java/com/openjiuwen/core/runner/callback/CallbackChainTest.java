// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CallbackChain execution.
 * Translated from Python test_chain.py
 */
@DisplayName("CallbackChain Tests")
class CallbackChainTest {

    @Test
    @DisplayName("Initialization")
    void testInitialization() {
        CallbackChain chain = new CallbackChain("test_chain");
        assertEquals("test_chain", chain.getName());
        assertTrue(chain.getCallbacks().isEmpty());
    }

    @Test
    @DisplayName("Add callback")
    void testAddCallback() {
        CallbackChain chain = new CallbackChain("test");
        Function<Map<String, Object>, Object> callback = kwargs -> null;
        CallbackInfo info = CallbackInfo.builder()
                .callback(callback).priority(10).callbackName("cb1").build();
        chain.add(info, null, null);
        assertEquals(1, chain.getCallbacks().size());
        assertSame(info, chain.getCallbacks().get(0));
    }

    @Test
    @DisplayName("Multiple callbacks sorted by priority")
    void testAddMultipleCallbacksSortedByPriority() {
        CallbackChain chain = new CallbackChain("test");
        chain.add(CallbackInfo.builder().callback(kwargs -> null).priority(1).callbackName("low").build(), null, null);
        chain.add(CallbackInfo.builder().callback(kwargs -> null).priority(10).callbackName("high").build(), null, null);
        chain.add(CallbackInfo.builder().callback(kwargs -> null).priority(5).callbackName("med").build(), null, null);
        assertEquals(10, chain.getCallbacks().get(0).getPriority());
        assertEquals(5, chain.getCallbacks().get(1).getPriority());
        assertEquals(1, chain.getCallbacks().get(2).getPriority());
    }

    @Test
    @DisplayName("Remove callback")
    void testRemoveCallback() {
        CallbackChain chain = new CallbackChain("test");
        Function<Map<String, Object>, Object> cb1 = kwargs -> null;
        Function<Map<String, Object>, Object> cb2 = kwargs -> null;
        chain.add(CallbackInfo.builder().callback(cb1).priority(0).callbackName("cb1").build(), null, null);
        chain.add(CallbackInfo.builder().callback(cb2).priority(0).callbackName("cb2").build(), null, null);
        assertEquals(2, chain.getCallbacks().size());
        chain.remove(cb1);
        assertEquals(1, chain.getCallbacks().size());
        assertSame(cb2, chain.getCallbacks().get(0).getCallback());
    }

    @Test
    @DisplayName("Execute single callback")
    void testExecuteSingleCallback() {
        CallbackChain chain = new CallbackChain("test");
        chain.add(CallbackInfo.builder()
                .callback(kwargs -> "result")
                .priority(0).callbackName("cb").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("result", result.getResult());
        assertTrue(context.isCompleted());
    }

    @Test
    @DisplayName("Execute multiple callbacks passes results between them")
    void testExecuteMultipleCallbacks() {
        CallbackChain chain = new CallbackChain("test");
        List<String> executionOrder = new ArrayList<>();

        chain.add(CallbackInfo.builder()
                .callback(kwargs -> {
                    executionOrder.add("step1");
                    Map<String, Object> r = new HashMap<>();
                    r.put("step1", true);
                    return r;
                }).priority(20).callbackName("step1").build(), null, null);

        chain.add(CallbackInfo.builder()
                .callback(kwargs -> {
                    executionOrder.add("step2");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> prev = (Map<String, Object>) kwargs.get("_last_result");
                    if (prev != null) {
                        prev.put("step2", true);
                    }
                    return prev;
                }).priority(10).callbackName("step2").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(List.of("step1", "step2"), executionOrder);
        @SuppressWarnings("unchecked")
        Map<String, Object> finalResult = (Map<String, Object>) result.getResult();
        assertTrue((Boolean) finalResult.get("step1"));
        assertTrue((Boolean) finalResult.get("step2"));
    }

    @Test
    @DisplayName("Execute respects priority order")
    void testExecuteRespectsPriorityOrder() {
        CallbackChain chain = new CallbackChain("test");
        List<String> order = new ArrayList<>();

        chain.add(CallbackInfo.builder()
                .callback(kwargs -> { order.add("b"); return "b"; })
                .priority(5).callbackName("b").build(), null, null);
        chain.add(CallbackInfo.builder()
                .callback(kwargs -> { order.add("a"); return "a"; })
                .priority(10).callbackName("a").build(), null, null);
        chain.add(CallbackInfo.builder()
                .callback(kwargs -> { order.add("c"); return "c"; })
                .priority(1).callbackName("c").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        chain.execute(context);

        assertEquals(List.of("a", "b", "c"), order);
    }

    @Test
    @DisplayName("Execute skips disabled callbacks")
    void testExecuteSkipsDisabledCallbacks() {
        CallbackChain chain = new CallbackChain("test");
        List<String> order = new ArrayList<>();

        chain.add(CallbackInfo.builder()
                .callback(kwargs -> { order.add("enabled"); return null; })
                .priority(10).enabled(true).callbackName("enabled").build(), null, null);
        chain.add(CallbackInfo.builder()
                .callback(kwargs -> { order.add("disabled"); return null; })
                .priority(5).enabled(false).callbackName("disabled").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        chain.execute(context);

        assertEquals(List.of("enabled"), order);
    }

    @Test
    @DisplayName("BREAK action stops chain")
    void testBreakActionStopsChain() {
        CallbackChain chain = new CallbackChain("test");
        List<String> executed = new ArrayList<>();

        chain.add(CallbackInfo.builder()
                .callback(kwargs -> {
                    executed.add("step1");
                    return ChainResult.builder()
                            .action(ChainAction.BREAK)
                            .result("stopped_here")
                            .build();
                }).priority(10).callbackName("step1").build(), null, null);
        chain.add(CallbackInfo.builder()
                .callback(kwargs -> { executed.add("step2"); return "step2_result"; })
                .priority(5).callbackName("step2").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.BREAK, result.getAction());
        assertEquals("stopped_here", result.getResult());
        assertEquals(List.of("step1"), executed);
    }

    @Test
    @DisplayName("ROLLBACK action triggers rollback")
    void testRollbackActionTriggersRollback() {
        CallbackChain chain = new CallbackChain("test");
        List<String> rollbackOrder = new ArrayList<>();

        Function<Map<String, Object>, Object> step1Cb = kwargs ->
                ChainResult.builder().action(ChainAction.CONTINUE).result("step1").build();
        Function<Map<String, Object>, Object> step2Cb = kwargs ->
                ChainResult.builder().action(ChainAction.ROLLBACK)
                        .error(new RuntimeException("Failed")).build();

        chain.add(CallbackInfo.builder()
                        .callback(step1Cb).priority(10).callbackName("step1").build(),
                context -> rollbackOrder.add("rollback1"), null);
        chain.add(CallbackInfo.builder()
                        .callback(step2Cb).priority(5).callbackName("step2").build(),
                context -> rollbackOrder.add("rollback2"), null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertTrue(context.isRolledBack());
        assertTrue(rollbackOrder.contains("rollback1"));
    }

    @Test
    @DisplayName("RETRY action retries callback")
    void testRetryActionRetriesCallback() {
        CallbackChain chain = new CallbackChain("test");
        int[] callCount = {0};

        chain.add(CallbackInfo.builder()
                .callback(kwargs -> {
                    callCount[0]++;
                    if (callCount[0] < 3) {
                        return ChainResult.builder().action(ChainAction.RETRY).build();
                    }
                    return ChainResult.builder().action(ChainAction.CONTINUE).result("success").build();
                }).priority(0).maxRetries(5).callbackName("flaky").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("success", result.getResult());
        assertEquals(3, callCount[0]);
    }

    @Test
    @DisplayName("Exception triggers rollback")
    void testExceptionTriggersRollback() {
        CallbackChain chain = new CallbackChain("test");
        boolean[] rollbackCalled = {false};

        Function<Map<String, Object>, Object> step1 = kwargs -> "step1_result";
        Function<Map<String, Object>, Object> failStep = kwargs -> {
            throw new RuntimeException("Something went wrong");
        };

        chain.add(CallbackInfo.builder()
                        .callback(step1).priority(10).callbackName("step1").build(),
                ctx -> rollbackCalled[0] = true, null);
        chain.add(CallbackInfo.builder()
                        .callback(failStep).priority(5).callbackName("failStep").build(),
                null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertInstanceOf(RuntimeException.class, result.getError());
        assertTrue(rollbackCalled[0]);
    }

    @Test
    @DisplayName("Error handler can recover")
    void testErrorHandlerCanRecover() {
        CallbackChain chain = new CallbackChain("test");

        Function<Map<String, Object>, Object> failCb = kwargs -> {
            throw new RuntimeException("Expected error");
        };

        chain.add(CallbackInfo.builder()
                .callback(failCb).priority(0).callbackName("failCb").build(),
                null,
                exCtx -> "recovered_result");

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("recovered_result", result.getResult());
    }

    @Test
    @DisplayName("Retry on exception")
    void testRetryOnException() {
        CallbackChain chain = new CallbackChain("test");
        int[] attempts = {0};

        chain.add(CallbackInfo.builder()
                .callback(kwargs -> {
                    attempts[0]++;
                    if (attempts[0] < 3) {
                        throw new RuntimeException("Temporary failure");
                    }
                    return "success";
                }).priority(0).maxRetries(3).retryDelay(0.01).callbackName("flaky").build(), null, null);

        ChainContext context = new ChainContext("test", new Object[0], new HashMap<>());
        ChainResult result = chain.execute(context);

        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("success", result.getResult());
        assertEquals(3, attempts[0]);
    }
}
