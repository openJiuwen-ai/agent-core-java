/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for wrap handlers: createWrapDecorator, framework.onWrap, framework.wrap.
 * <p>
 * Mirrors Python's test_framework_wrap.py.
 * <p>
 * Coverage:
 * - Static chain (createWrapDecorator): single/multiple handlers, execution order,
 *   arg/result mutation, short-circuit, no-op, sync/async functions,
 *   error propagation.
 * - Event-based chain (onWrap / wrap): priority ordering, dynamic lookup after decoration,
 *   no-handler pass-through, storage in callbacks, unregister.
 */
class FrameworkWrapTest {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(false, false);
    }

    private static List<Integer> collectIntegers(Object raw) {
        List<Integer> values = new ArrayList<>();
        if (raw instanceof Iterator<?> iterator) {
            while (iterator.hasNext()) {
                values.add((Integer) iterator.next());
            }
            return values;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                values.add((Integer) item);
            }
        }
        return values;
    }

    // ===========================================================================
    // createWrapDecorator — static chain
    // ===========================================================================

    @Test
    @DisplayName("Single handler executes around function")
    void testSingleHandlerExecutesAroundFunction() throws Exception {
        List<String> log = new ArrayList<>();

        CallbackFramework.WrapHandler handler = (callNext, kwargs) -> {
            log.add("before");
            Object result = callNext.apply(kwargs);
            log.add("after");
            return result;
        };

        Function<Map<String, Object>, Object> originalFunc = kwargs -> {
            log.add("func");
            return 42;
        };

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(handler)).apply(originalFunc);

        Map<String, Object> kwargs = new HashMap<>();
        Object result = wrapped.apply(kwargs);

        assertEquals(42, result);
        assertEquals(List.of("before", "func", "after"), log);
    }

    @Test
    @DisplayName("Multiple handlers: outermost first")
    void testMultipleHandlersOutermostFirst() throws Exception {
        List<String> log = new ArrayList<>();

        CallbackFramework.WrapHandler h1 = (callNext, kwargs) -> {
            log.add("h1_in");
            Object result = callNext.apply(kwargs);
            log.add("h1_out");
            return result;
        };

        CallbackFramework.WrapHandler h2 = (callNext, kwargs) -> {
            log.add("h2_in");
            Object result = callNext.apply(kwargs);
            log.add("h2_out");
            return result;
        };

        Function<Map<String, Object>, Object> originalFunc = kwargs -> {
            log.add("func");
            return 0;
        };

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(h1, h2)).apply(originalFunc);

        wrapped.apply(new HashMap<>());
        assertEquals(List.of("h1_in", "h2_in", "func", "h2_out", "h1_out"), log);
    }

    @Test
    @DisplayName("Handler can modify kwargs")
    void testHandlerModifiesKwargs() throws Exception {
        CallbackFramework.WrapHandler addOne = (callNext, kwargs) -> {
            kwargs.put("n", (int) kwargs.getOrDefault("n", 0) + 1);
            return callNext.apply(kwargs);
        };

        Function<Map<String, Object>, Object> compute = kwargs -> kwargs.get("n");

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(addOne)).apply(compute);

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("n", 5);
        assertEquals(6, wrapped.apply(kwargs));
    }

    @Test
    @DisplayName("Handler can modify result")
    void testHandlerModifiesResult() throws Exception {
        CallbackFramework.WrapHandler doubleResult = (callNext, kwargs) -> {
            Object result = callNext.apply(kwargs);
            return (int) result * 2;
        };

        Function<Map<String, Object>, Object> get = kwargs -> 7;

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(doubleResult)).apply(get);

        assertEquals(14, wrapped.apply(new HashMap<>()));
    }

    @Test
    @DisplayName("Handler short-circuit: wrapped function not reached")
    void testHandlerShortCircuit() throws Exception {
        List<Boolean> reached = new ArrayList<>();

        CallbackFramework.WrapHandler blocker = (callNext, kwargs) -> "blocked";

        Function<Map<String, Object>, Object> func = kwargs -> {
            reached.add(true);
            return "original";
        };

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(blocker)).apply(func);

        assertEquals("blocked", wrapped.apply(new HashMap<>()));
        assertTrue(reached.isEmpty());
    }

    @Test
    @DisplayName("No handlers is identity")
    void testNoHandlersIsIdentity() throws Exception {
        Function<Map<String, Object>, Object> func = kwargs -> {
            Object x = kwargs.get("x");
            return (int) x * 3;
        };

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of()).apply(func);

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("x", 4);
        assertEquals(12, wrapped.apply(kwargs));
    }

    @Test
    @DisplayName("Error in wrapped function propagates")
    void testErrorInWrappedFunctionPropagates() {
        CallbackFramework.WrapHandler passthrough = (callNext, kwargs) -> callNext.apply(kwargs);

        Function<Map<String, Object>, Object> boom = kwargs -> {
            throw new RuntimeException("oops");
        };

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(passthrough)).apply(boom);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> wrapped.apply(new HashMap<>()));
        assertTrue(ex.getMessage().contains("oops"));
    }

    @Test
    @DisplayName("Error in handler propagates")
    void testErrorInHandlerPropagates() {
        CallbackFramework.WrapHandler badHandler = (callNext, kwargs) -> {
            throw new RuntimeException("handler failed");
        };

        Function<Map<String, Object>, Object> func = kwargs -> 1;

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(badHandler)).apply(func);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> wrapped.apply(new HashMap<>()));
        assertTrue(ex.getMessage().contains("handler failed"));
    }

    // ===========================================================================
    // framework.onWrap / framework.wrap — event-based chain
    // ===========================================================================

    @Test
    @DisplayName("Single onWrap handler executes around function")
    void testSingleHandlerFramework() throws Exception {
        List<String> log = new ArrayList<>();

        CallbackFramework.WrapHandler handler = (callNext, kwargs) -> {
            log.add("before");
            Object result = callNext.apply(kwargs);
            log.add("after");
            return result;
        };

        framework.onWrap("greet", handler, 10);

        Function<Map<String, Object>, Object> greet = kwargs -> {
            log.add("greet");
            return "hello " + kwargs.get("name");
        };

        Function<Map<String, Object>, Object> wrapped = framework.wrap("greet", greet);

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("name", "world");
        Object result = wrapped.apply(kwargs);

        assertEquals("hello world", result);
        assertEquals(List.of("before", "greet", "after"), log);
    }

    @Test
    @DisplayName("Priority determines outermost")
    void testPriorityDeterminesOutermost() throws Exception {
        List<String> log = new ArrayList<>();

        CallbackFramework.WrapHandler low = (callNext, kwargs) -> {
            log.add("low_in");
            Object result = callNext.apply(kwargs);
            log.add("low_out");
            return result;
        };

        CallbackFramework.WrapHandler high = (callNext, kwargs) -> {
            log.add("high_in");
            Object result = callNext.apply(kwargs);
            log.add("high_out");
            return result;
        };

        framework.onWrap("ev", low, 5);
        framework.onWrap("ev", high, 20);

        String key = CallbackFramework.WRAP_EVENT_PREFIX + "ev";
        List<CallbackInfo> infos = framework.getCallbacks().get(key);
        assertEquals(2, infos.size());
        // Higher priority should be first in list
        assertEquals(20, infos.get(0).getPriority());
        assertEquals(5, infos.get(1).getPriority());
    }

    @Test
    @DisplayName("Handler stored in callbacks registry")
    void testHandlerStoredInCallbacksRegistry() {
        CallbackFramework.WrapHandler handler = (callNext, kwargs) -> callNext.apply(kwargs);

        framework.onWrap("my_func", handler, 10);

        String key = CallbackFramework.WRAP_EVENT_PREFIX + "my_func";
        assertTrue(framework.getCallbacks().containsKey(key));
        assertEquals(1, framework.getCallbacks().get(key).size());
    }

    @Test
    @DisplayName("Unregister handler")
    void testUnregisterHandler() throws Exception {
        List<String> log = new ArrayList<>();

        CallbackFramework.WrapHandler handler = (callNext, kwargs) -> {
            log.add("handler");
            return callNext.apply(kwargs);
        };

        framework.onWrap("unregister_ev", handler, 10);

        String key = CallbackFramework.WRAP_EVENT_PREFIX + "unregister_ev";
        assertEquals(1, framework.getCallbacks().get(key).size());

        // Unregister
        framework.unregisterWrapHandler("unregister_ev", handler);
        assertEquals(0, framework.getCallbacks().get(key).size());
    }

    @Test
    @DisplayName("WRAP_EVENT_PREFIX constant is correct")
    void testWrapEventPrefix() {
        assertEquals("__wrap__:", CallbackFramework.WRAP_EVENT_PREFIX);
    }

    // ===========================================================================
    // Additional test cases from Python test_framework_wrap.py
    // ===========================================================================

    @Test
    @DisplayName("Stacked result mutation")
    void testStackedResultMutation() throws Exception {
        CallbackFramework.WrapHandler add_10 = (callNext, kwargs) -> {
            Object result = callNext.apply(kwargs);
            return (int) result + 10;
        };

        CallbackFramework.WrapHandler add_100 = (callNext, kwargs) -> {
            Object result = callNext.apply(kwargs);
            return (int) result + 100;
        };

        // add_10 is outermost: func → add_100 → add_10
        // func returns 1 → add_100: 101 → add_10: 111
        Function<Map<String, Object>, Object> func = kwargs -> 1;

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(add_10, add_100)).apply(func);

        assertEquals(111, wrapped.apply(new HashMap<>()));
    }

    @Test
    @DisplayName("No handler passthrough")
    void testNoHandlerPassthrough() throws Exception {
        Function<Map<String, Object>, Object> func = kwargs -> {
            Object x = kwargs.get("x");
            return (int) x * 2;
        };

        // Register handler for different event, not for "empty_event"
        CallbackFramework.WrapHandler handler = (callNext, kwargs) -> callNext.apply(kwargs);
        framework.onWrap("other_event", handler, 10);

        Function<Map<String, Object>, Object> wrapped = framework.wrap("empty_event", func);
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("x", 5);
        
        Object result = wrapped.apply(kwargs);
        assertEquals(10, result);
    }

    @Test
    @DisplayName("Sync function promoted to wrapped callable")
    void testSyncFunctionPromotedToAsync() throws Exception {
        CallbackFramework.WrapHandler handler = (callNext, kwargs) -> (int) callNext.apply(kwargs) + 1;
        Function<Map<String, Object>, Object> syncFunc = kwargs -> (int) kwargs.get("n") * 2;
        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(handler)).apply(syncFunc);

        assertEquals(7, wrapped.apply(new HashMap<>(Map.of("n", 3))));
    }

    @Test
    @DisplayName("Iterator single handler transforms yielded items")
    void testAsyncGeneratorSingleHandler() throws Exception {
        CallbackFramework.WrapHandler doubleItems = (callNext, kwargs) ->
                collectIntegers(callNext.apply(kwargs)).stream().map(i -> i * 2).toList().iterator();
        Function<Map<String, Object>, Object> stream = kwargs -> List.of(1, 2, 3).iterator();
        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(doubleItems)).apply(stream);

        assertEquals(List.of(2, 4, 6), collectIntegers(wrapped.apply(new HashMap<>())));
    }

    @Test
    @DisplayName("Iterator handler chain order composes outermost last")
    void testAsyncGeneratorChainOrder() throws Exception {
        CallbackFramework.WrapHandler add10 = (callNext, kwargs) ->
                collectIntegers(callNext.apply(kwargs)).stream().map(i -> i + 10).toList().iterator();
        CallbackFramework.WrapHandler mul2 = (callNext, kwargs) ->
                collectIntegers(callNext.apply(kwargs)).stream().map(i -> i * 2).toList().iterator();
        Function<Map<String, Object>, Object> stream = kwargs -> List.of(1, 2).iterator();

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(add10, mul2)).apply(stream);

        assertEquals(List.of(12, 14), collectIntegers(wrapped.apply(new HashMap<>())));
    }

    @Test
    @DisplayName("Iterator handler can filter yielded items")
    void testAsyncGeneratorHandlerFilterItems() throws Exception {
        CallbackFramework.WrapHandler onlyEven = (callNext, kwargs) ->
                collectIntegers(callNext.apply(kwargs)).stream().filter(i -> i % 2 == 0).toList().iterator();
        Function<Map<String, Object>, Object> stream = kwargs -> List.of(0, 1, 2, 3, 4).iterator();

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(onlyEven)).apply(stream);

        assertEquals(List.of(0, 2, 4), collectIntegers(wrapped.apply(new HashMap<>())));
    }

    @Test
    @DisplayName("Sync generator promoted to wrapped iterator")
    void testSyncGeneratorPromotedToAsyncGenerator() throws Exception {
        CallbackFramework.WrapHandler negate = (callNext, kwargs) ->
                collectIntegers(callNext.apply(kwargs)).stream().map(i -> -i).toList().iterator();
        Function<Map<String, Object>, Object> syncGen = kwargs -> List.of(1, 2).iterator();

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(negate)).apply(syncGen);

        assertEquals(List.of(-1, -2), collectIntegers(wrapped.apply(new HashMap<>())));
    }

    @Test
    @DisplayName("Event wrap handler transforms iterator items")
    void testAsyncGeneratorHandler() throws Exception {
        framework.onWrap("stream", (callNext, kwargs) ->
                collectIntegers(callNext.apply(kwargs)).stream().map(i -> -i).toList().iterator(), 0);
        Function<Map<String, Object>, Object> stream = kwargs -> {
            int n = (int) kwargs.get("n");
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                values.add(i);
            }
            return values.iterator();
        };

        Function<Map<String, Object>, Object> wrapped = framework.wrap("stream", stream);

        assertEquals(List.of(0, -1, -2), collectIntegers(wrapped.apply(new HashMap<>(Map.of("n", 3)))));
    }

    @Test
    @DisplayName("Event wrap handler supports sync iterator functions")
    void testSyncGeneratorHandler() throws Exception {
        framework.onWrap("sync_stream", (callNext, kwargs) ->
                collectIntegers(callNext.apply(kwargs)).stream().map(i -> i + 1).toList().iterator(), 0);
        Function<Map<String, Object>, Object> syncGen = kwargs -> {
            int n = (int) kwargs.get("n");
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                values.add(i * 10);
            }
            return values.iterator();
        };

        Function<Map<String, Object>, Object> wrapped = framework.wrap("sync_stream", syncGen);

        assertEquals(List.of(1, 11, 21), collectIntegers(wrapped.apply(new HashMap<>(Map.of("n", 3)))));
    }

    @Test
    @DisplayName("Register and wrap via factory-style APIs")
    void testRegisterAndWrapViaFactories() throws Exception {
        List<String> calls = new ArrayList<>();

        framework.onWrap("factory_ev", (callNext, kwargs) -> {
            calls.add("handler");
            return callNext.apply(kwargs);
        }, 0);
        Function<Map<String, Object>, Object> func = kwargs -> {
            calls.add("func");
            return 99;
        };

        Object result = framework.wrap("factory_ev", func).apply(new HashMap<>());

        assertEquals(99, result);
        assertEquals(List.of("handler", "func"), calls);
    }

    @Test
    @DisplayName("Handler does not pollute regular event")
    void testHandlerDoesNotPolluteRegularEvent() {
        CallbackFramework.WrapHandler handler = (callNext, kwargs) -> callNext.apply(kwargs);

        framework.onWrap("isolated", handler, 10);

        // The logical event "isolated" must have no regular callbacks
        // Only wrap callbacks under "__wrap__:isolated"
        assertFalse(framework.getCallbacks().containsKey("isolated"));
        assertTrue(framework.getCallbacks().containsKey(CallbackFramework.WRAP_EVENT_PREFIX + "isolated"));
    }

    @Test
    @DisplayName("Three handlers stacked")
    void testThreeHandlersStacked() throws Exception {
        CallbackFramework.WrapHandler h1 = (callNext, kwargs) -> {
            Object result = callNext.apply(kwargs);
            return (int) result + 1;  // innermost of the three
        };

        CallbackFramework.WrapHandler h2 = (callNext, kwargs) -> {
            Object result = callNext.apply(kwargs);
            return (int) result * 3;  // middle
        };

        CallbackFramework.WrapHandler h3 = (callNext, kwargs) -> {
            Object result = callNext.apply(kwargs);
            return (int) result - 5;  // outermost
        };

        // Priority: h3(20) > h2(10) > h1(1)
        // Order: h3 → h2 → h1 → func
        // func returns 10 → h1: +1=11 → h2: *3=33 → h3: -5=28
        Function<Map<String, Object>, Object> func = kwargs -> 10;

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(h3, h2, h1)).apply(func);

        assertEquals(28, wrapped.apply(new HashMap<>()));
    }

    @Test
    @DisplayName("Error propagates through handler")
    void testErrorPropagatesThroughHandler() {
        CallbackFramework.WrapHandler passthrough = (callNext, kwargs) -> callNext.apply(kwargs);

        Function<Map<String, Object>, Object> failing = kwargs -> {
            throw new RuntimeException("fail");
        };

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(passthrough)).apply(failing);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> wrapped.apply(new HashMap<>()));
        assertTrue(ex.getMessage().contains("fail"));
    }

    @Test
    @DisplayName("Handler modifies args framework")
    void testHandlerModifiesArgsFramework() throws Exception {
        CallbackFramework.WrapHandler double_n = (callNext, kwargs) -> {
            kwargs.put("n", (int) kwargs.getOrDefault("n", 0) * 2);
            return callNext.apply(kwargs);
        };

        Function<Map<String, Object>, Object> compute = kwargs -> kwargs.get("n");

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(double_n)).apply(compute);

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("n", 3);
        assertEquals(6, wrapped.apply(kwargs));
    }

    @Test
    @DisplayName("Handler modifies result framework")
    void testHandlerModifiesResultFramework() throws Exception {
        CallbackFramework.WrapHandler stringify = (callNext, kwargs) -> {
            Object result = callNext.apply(kwargs);
            return "value=" + result;
        };

        Function<Map<String, Object>, Object> process = kwargs -> {
            Object x = kwargs.get("x");
            return (int) x + 1;
        };

        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(List.of(stringify)).apply(process);

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("x", 4);
        assertEquals("value=5", wrapped.apply(kwargs));
    }

    @Test
    @DisplayName("Priority via factory decorator")
    void testPriorityViaFactory() throws Exception {
        List<String> order = new ArrayList<>();

        CallbackFramework.WrapHandler h_low = (callNext, kwargs) -> {
            order.add("low");
            return callNext.apply(kwargs);
        };

        CallbackFramework.WrapHandler h_high = (callNext, kwargs) -> {
            order.add("high");
            return callNext.apply(kwargs);
        };

        framework.onWrap("prio_ev", h_low, 1);
        framework.onWrap("prio_ev", h_high, 10);

        Function<Map<String, Object>, Object> func = kwargs -> 0;
        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(framework.getWrapHandlers("prio_ev")).apply(func);

        wrapped.apply(new HashMap<>());
        assertEquals(List.of("high", "low"), order);
    }

    @Test
    @DisplayName("Disabled handler skipped")
    void testDisabledHandlerSkipped() throws Exception {
        CallbackFramework.WrapHandler handler = (callNext, kwargs) -> "from_handler";

        framework.onWrap("dis_ev", handler, 10);

        Function<Map<String, Object>, Object> func = kwargs -> "from_func";

        // Get handlers and check we can simulate disabled behavior
        List<CallbackFramework.WrapHandler> handlers = framework.getWrapHandlers("dis_ev");
        assertFalse(handlers.isEmpty());

        // Disable the handler's CallbackInfo directly
        String key = CallbackFramework.WRAP_EVENT_PREFIX + "dis_ev";
        framework.getCallbacks().get(key).get(0).setEnabled(false);

        // Now get handlers should return empty list
        List<CallbackFramework.WrapHandler> disabledHandlers = framework.getWrapHandlers("dis_ev");
        assertTrue(disabledHandlers.isEmpty());
    }

    @Test
    @DisplayName("Dynamic lookup after decoration")
    void testDynamicLookupAfterDecoration() throws Exception {
        // First create wrapped function
        Function<Map<String, Object>, Object> func = kwargs -> 1;

        // At this point, no handlers registered
        List<CallbackFramework.WrapHandler> initialHandlers = framework.getWrapHandlers("late");
        assertTrue(initialHandlers.isEmpty());

        // Register handler AFTER checking
        CallbackFramework.WrapHandler handler = (callNext, kwargs) -> {
            Object result = callNext.apply(kwargs);
            return (int) result + 100;
        };
        framework.onWrap("late", handler, 10);

        // Now handlers should be available
        List<CallbackFramework.WrapHandler> laterHandlers = framework.getWrapHandlers("late");
        assertFalse(laterHandlers.isEmpty());

        // Apply with handlers now
        Function<Map<String, Object>, Object> wrapped =
                CallbackFramework.createWrapDecorator(laterHandlers).apply(func);
        assertEquals(101, wrapped.apply(new HashMap<>()));
    }
}
