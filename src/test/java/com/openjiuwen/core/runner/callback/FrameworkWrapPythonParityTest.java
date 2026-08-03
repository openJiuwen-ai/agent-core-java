/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental parity tests for wrap decorators.
 *
 * <p>Mirrors Python's wrap tests in
 * {@code tests/unit_tests/core/runner/callback/test_framework_wrap.py}.</p>
 */
class FrameworkWrapPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_single_handler_executes_around_function",
            "test_multiple_handlers_outermost_first",
            "test_handler_modifies_kwargs",
            "test_handler_modifies_result",
            "test_handler_short_circuit",
            "test_no_handlers_is_identity",
            "test_error_in_wrapped_function_propagates",
            "test_error_in_handler_propagates",
            "test_stacked_result_mutation",
            "test_sync_function_promoted_to_async",
            "test_async_generator_single_handler",
            "test_async_generator_chain_order",
            "test_async_generator_handler_filter_items",
            "test_sync_generator_promoted_to_async_generator",
            "test_single_handler",
            "test_priority_determines_outermost",
            "test_no_handler_passthrough",
            "test_dynamic_lookup_after_decoration",
            "test_handler_stored_in_callbacks_registry",
            "test_handler_does_not_pollute_regular_event",
            "test_unregister_handler",
            "test_handler_modifies_args_framework",
            "test_handler_modifies_result_framework",
            "test_async_generator_handler",
            "test_sync_generator_handler",
            "test_three_handlers_stacked",
            "test_error_propagates_through_handler",
            "test_register_and_wrap_via_factories",
            "test_priority_via_factory",
            "test_disabled_handler_skipped"
    );

    @TestFactory
    Collection<DynamicTest> pythonFrameworkWrapCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonFrameworkWrapCase(name)))
                .toList();
    }

    private void runPythonFrameworkWrapCase(String name) {
        switch (name) {
            case "test_single_handler_executes_around_function" -> singleHandlerExecutesAroundFunction();
            case "test_multiple_handlers_outermost_first" -> multipleHandlersOutermostFirst();
            case "test_handler_modifies_kwargs" -> handlerModifiesKwargs();
            case "test_handler_modifies_result" -> handlerModifiesResult();
            case "test_handler_short_circuit" -> handlerShortCircuit();
            case "test_no_handlers_is_identity" -> noHandlersIsIdentity();
            case "test_error_in_wrapped_function_propagates" -> errorInWrappedFunctionPropagates();
            case "test_error_in_handler_propagates" -> errorInHandlerPropagates();
            case "test_stacked_result_mutation" -> stackedResultMutation();
            case "test_sync_function_promoted_to_async" -> syncFunctionPromotedToWrappedFunction();
            case "test_async_generator_single_handler" -> generatorSingleHandler();
            case "test_async_generator_chain_order" -> generatorChainOrder();
            case "test_async_generator_handler_filter_items" -> generatorHandlerFilterItems();
            case "test_sync_generator_promoted_to_async_generator" -> syncGeneratorWrappedAsIterator();
            case "test_single_handler" -> frameworkSingleHandler();
            case "test_priority_determines_outermost" -> priorityDeterminesOutermost();
            case "test_no_handler_passthrough" -> noHandlerPassthrough();
            case "test_dynamic_lookup_after_decoration" -> dynamicLookupAfterDecoration();
            case "test_handler_stored_in_callbacks_registry" -> handlerStoredInCallbacksRegistry();
            case "test_handler_does_not_pollute_regular_event" -> handlerDoesNotPolluteRegularEvent();
            case "test_unregister_handler" -> unregisterHandler();
            case "test_handler_modifies_args_framework" -> handlerModifiesArgsFramework();
            case "test_handler_modifies_result_framework" -> handlerModifiesResultFramework();
            case "test_async_generator_handler" -> frameworkGeneratorHandler();
            case "test_sync_generator_handler" -> frameworkSyncGeneratorHandler();
            case "test_three_handlers_stacked" -> threeHandlersStacked();
            case "test_error_propagates_through_handler" -> errorPropagatesThroughHandler();
            case "test_register_and_wrap_via_factories" -> registerAndWrapViaFactories();
            case "test_priority_via_factory" -> priorityViaFactory();
            case "test_disabled_handler_skipped" -> disabledHandlerSkipped();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private static void singleHandlerExecutesAroundFunction() {
        List<String> log = new ArrayList<>();
        WrapHandler handler = (callNext, kwargs) -> {
            log.add("before");
            Object result = callNext.apply(kwargs);
            log.add("after");
            return result;
        };
        Function<Map<String, Object>, Object> wrapped = CallbackDecorators.createWrapDecorator(handler)
                .apply(kwargs -> {
                    log.add("func");
                    return 42;
                });

        assertEquals(42, wrapped.apply(Map.of()));
        assertEquals(List.of("before", "func", "after"), log);
    }

    private static void multipleHandlersOutermostFirst() {
        List<String> log = new ArrayList<>();
        WrapHandler h1 = (callNext, kwargs) -> {
            log.add("h1_in");
            Object result = callNext.apply(kwargs);
            log.add("h1_out");
            return result;
        };
        WrapHandler h2 = (callNext, kwargs) -> {
            log.add("h2_in");
            Object result = callNext.apply(kwargs);
            log.add("h2_out");
            return result;
        };
        Function<Map<String, Object>, Object> wrapped = CallbackDecorators.createWrapDecorator(h1, h2)
                .apply(kwargs -> {
                    log.add("func");
                    return 0;
                });

        wrapped.apply(Map.of());

        assertEquals(List.of("h1_in", "h2_in", "func", "h2_out", "h1_out"), log);
    }

    private static void handlerModifiesKwargs() {
        WrapHandler addOne = (callNext, kwargs) -> {
            Map<String, Object> changed = mutable(kwargs);
            changed.put("n", ((Number) changed.getOrDefault("n", 0)).intValue() + 1);
            return callNext.apply(changed);
        };
        Function<Map<String, Object>, Object> compute = CallbackDecorators.createWrapDecorator(addOne)
                .apply(kwargs -> kwargs.get("n"));

        assertEquals(6, compute.apply(Map.of("n", 5)));
    }

    private static void handlerModifiesResult() {
        WrapHandler doubleResult = (callNext, kwargs) -> ((Number) callNext.apply(kwargs)).intValue() * 2;
        Function<Map<String, Object>, Object> get = CallbackDecorators.createWrapDecorator(doubleResult)
                .apply(kwargs -> 7);

        assertEquals(14, get.apply(Map.of()));
    }

    private static void handlerShortCircuit() {
        List<Boolean> reached = new ArrayList<>();
        WrapHandler blocker = (callNext, kwargs) -> "blocked";
        Function<Map<String, Object>, Object> func = CallbackDecorators.createWrapDecorator(blocker)
                .apply(kwargs -> {
                    reached.add(true);
                    return "original";
                });

        assertEquals("blocked", func.apply(Map.of()));
        assertEquals(List.of(), reached);
    }

    private static void noHandlersIsIdentity() {
        Function<Map<String, Object>, Object> original = kwargs -> ((Number) kwargs.get("x")).intValue() * 3;
        Function<Map<String, Object>, Object> wrapped = CallbackDecorators.createWrapDecorator().apply(original);

        assertSame(original, wrapped);
        assertEquals(12, wrapped.apply(Map.of("x", 4)));
    }

    private static void errorInWrappedFunctionPropagates() {
        WrapHandler passthrough = (callNext, kwargs) -> callNext.apply(kwargs);
        Function<Map<String, Object>, Object> boom = CallbackDecorators.createWrapDecorator(passthrough)
                .apply(kwargs -> {
                    throw new IllegalArgumentException("oops");
                });

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> boom.apply(Map.of()));
        assertEquals("oops", error.getMessage());
    }

    private static void errorInHandlerPropagates() {
        WrapHandler badHandler = (callNext, kwargs) -> {
            throw new IllegalStateException("handler failed");
        };
        Function<Map<String, Object>, Object> func = CallbackDecorators.createWrapDecorator(badHandler)
                .apply(kwargs -> 1);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> func.apply(Map.of()));
        assertEquals("handler failed", error.getMessage());
    }

    private static void stackedResultMutation() {
        WrapHandler add10 = (callNext, kwargs) -> ((Number) callNext.apply(kwargs)).intValue() + 10;
        WrapHandler add100 = (callNext, kwargs) -> ((Number) callNext.apply(kwargs)).intValue() + 100;
        Function<Map<String, Object>, Object> func = CallbackDecorators.createWrapDecorator(add10, add100)
                .apply(kwargs -> 1);

        assertEquals(111, func.apply(Map.of()));
    }

    private static void syncFunctionPromotedToWrappedFunction() {
        WrapHandler addOne = (callNext, kwargs) -> ((Number) callNext.apply(kwargs)).intValue() + 1;
        Function<Map<String, Object>, Object> syncFunc = CallbackDecorators.createWrapDecorator(addOne)
                .apply(kwargs -> ((Number) kwargs.get("n")).intValue() * 2);

        assertEquals(7, syncFunc.apply(Map.of("n", 3)));
    }

    private static void generatorSingleHandler() {
        WrapHandler doubleItems = (callNext, kwargs) -> toList(callNext.apply(kwargs)).stream()
                .map(value -> value * 2)
                .iterator();
        Function<Map<String, Object>, Object> stream = CallbackDecorators.createWrapDecorator(doubleItems)
                .apply(kwargs -> iteratorOf(1, 2, 3));

        assertEquals(List.of(2, 4, 6), toList(stream.apply(Map.of())));
    }

    private static void generatorChainOrder() {
        WrapHandler add10 = (callNext, kwargs) -> toList(callNext.apply(kwargs)).stream()
                .map(value -> value + 10)
                .iterator();
        WrapHandler mul2 = (callNext, kwargs) -> toList(callNext.apply(kwargs)).stream()
                .map(value -> value * 2)
                .iterator();
        Function<Map<String, Object>, Object> stream = CallbackDecorators.createWrapDecorator(add10, mul2)
                .apply(kwargs -> iteratorOf(1, 2));

        assertEquals(List.of(12, 14), toList(stream.apply(Map.of())));
    }

    private static void generatorHandlerFilterItems() {
        WrapHandler onlyEven = (callNext, kwargs) -> toList(callNext.apply(kwargs)).stream()
                .filter(value -> value % 2 == 0)
                .iterator();
        Function<Map<String, Object>, Object> stream = CallbackDecorators.createWrapDecorator(onlyEven)
                .apply(kwargs -> iteratorOf(0, 1, 2, 3, 4));

        assertEquals(List.of(0, 2, 4), toList(stream.apply(Map.of())));
    }

    private static void syncGeneratorWrappedAsIterator() {
        WrapHandler negate = (callNext, kwargs) -> toList(callNext.apply(kwargs)).stream()
                .map(value -> -value)
                .iterator();
        Function<Map<String, Object>, Object> syncGen = CallbackDecorators.createWrapDecorator(negate)
                .apply(kwargs -> iteratorOf(1, 2));

        assertEquals(List.of(-1, -2), toList(syncGen.apply(Map.of())));
    }

    private static void frameworkSingleHandler() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> log = new ArrayList<>();
        framework.onWrap("greet", 0).apply((callNext, kwargs) -> {
            log.add("before");
            Object result = callNext.apply(kwargs);
            log.add("after");
            return result;
        });
        Function<Map<String, Object>, Object> greet = framework.wrap("greet")
                .apply(kwargs -> {
                    log.add("greet");
                    return "hello " + kwargs.get("name");
                });

        assertEquals("hello world", greet.apply(Map.of("name", "world")));
        assertEquals(List.of("before", "greet", "after"), log);
    }

    private static void priorityDeterminesOutermost() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> log = new ArrayList<>();
        framework.onWrap("ev", 5).apply((callNext, kwargs) -> {
            log.add("low_in");
            Object result = callNext.apply(kwargs);
            log.add("low_out");
            return result;
        });
        framework.onWrap("ev", 20).apply((callNext, kwargs) -> {
            log.add("high_in");
            Object result = callNext.apply(kwargs);
            log.add("high_out");
            return result;
        });
        Function<Map<String, Object>, Object> func = framework.wrap("ev")
                .apply(kwargs -> {
                    log.add("func");
                    return 0;
                });

        func.apply(Map.of());

        assertEquals(List.of("high_in", "low_in", "func", "low_out", "high_out"), log);
    }

    private static void noHandlerPassthrough() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        Function<Map<String, Object>, Object> func = framework.wrap("empty_event")
                .apply(kwargs -> ((Number) kwargs.get("x")).intValue() * 2);

        assertEquals(10, func.apply(Map.of("x", 5)));
    }

    private static void dynamicLookupAfterDecoration() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        Function<Map<String, Object>, Object> func = framework.wrap("late").apply(kwargs -> 1);

        framework.onWrap("late", 0).apply((callNext, kwargs) -> ((Number) callNext.apply(kwargs)).intValue() + 100);

        assertEquals(101, func.apply(Map.of()));
    }

    private static void handlerStoredInCallbacksRegistry() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        WrapHandler handler = (callNext, kwargs) -> callNext.apply(kwargs);

        framework.onWrap("my_func", 0).apply(handler);

        String key = CallbackDecorators.WRAP_EVENT_PREFIX + "my_func";
        assertTrue(framework.getCallbacks().containsKey(key));
        assertEquals(1, framework.getCallbacks().get(key).size());
        assertSame(handler, framework.getCallbacks().get(key).get(0).getCallback().apply(Map.of()));
    }

    private static void handlerDoesNotPolluteRegularEvent() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.onWrap("isolated", 0).apply((callNext, kwargs) -> callNext.apply(kwargs));

        assertEquals(0, framework.getCallbacks().getOrDefault("isolated", List.of()).size());
    }

    private static void unregisterHandler() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> log = new ArrayList<>();
        framework.onWrap("unregister_ev", 0).apply((callNext, kwargs) -> {
            log.add("handler");
            return callNext.apply(kwargs);
        });
        Function<Map<String, Object>, Object> func = framework.wrap("unregister_ev").apply(kwargs -> 1);

        func.apply(Map.of());
        assertEquals(List.of("handler"), log);

        String key = CallbackDecorators.WRAP_EVENT_PREFIX + "unregister_ev";
        Function<Map<String, Object>, Object> registeredCallback =
                framework.getCallbacks().get(key).get(0).getCallback();
        framework.unregister(key, registeredCallback);
        log.clear();
        func.apply(Map.of());

        assertEquals(List.of(), log);
    }

    private static void handlerModifiesArgsFramework() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.onWrap("compute", 0).apply((callNext, kwargs) -> {
            Map<String, Object> changed = mutable(kwargs);
            changed.put("n", ((Number) changed.getOrDefault("n", 0)).intValue() * 2);
            return callNext.apply(changed);
        });
        Function<Map<String, Object>, Object> compute = framework.wrap("compute").apply(kwargs -> kwargs.get("n"));

        assertEquals(6, compute.apply(Map.of("n", 3)));
    }

    private static void handlerModifiesResultFramework() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.onWrap("process", 0).apply((callNext, kwargs) -> "value=" + callNext.apply(kwargs));
        Function<Map<String, Object>, Object> process = framework.wrap("process")
                .apply(kwargs -> ((Number) kwargs.get("x")).intValue() + 1);

        assertEquals("value=5", process.apply(Map.of("x", 4)));
    }

    private static void frameworkGeneratorHandler() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.onWrap("stream", 0).apply((callNext, kwargs) -> toList(callNext.apply(kwargs)).stream()
                .map(value -> -value)
                .iterator());
        Function<Map<String, Object>, Object> stream = framework.wrap("stream")
                .apply(kwargs -> iteratorOf(0, 1, 2));

        assertEquals(List.of(0, -1, -2), toList(stream.apply(Map.of())));
    }

    private static void frameworkSyncGeneratorHandler() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.onWrap("sync_stream", 0).apply((callNext, kwargs) -> toList(callNext.apply(kwargs)).stream()
                .map(value -> value + 1)
                .iterator());
        Function<Map<String, Object>, Object> syncGen = framework.wrap("sync_stream")
                .apply(kwargs -> iteratorOf(0, 10, 20));

        assertEquals(List.of(1, 11, 21), toList(syncGen.apply(Map.of())));
    }

    private static void threeHandlersStacked() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.onWrap("stack", 1).apply((callNext, kwargs) -> ((Number) callNext.apply(kwargs)).intValue() + 1);
        framework.onWrap("stack", 10).apply((callNext, kwargs) -> ((Number) callNext.apply(kwargs)).intValue() * 3);
        framework.onWrap("stack", 20).apply((callNext, kwargs) -> ((Number) callNext.apply(kwargs)).intValue() - 5);
        Function<Map<String, Object>, Object> func = framework.wrap("stack").apply(kwargs -> 10);

        assertEquals(28, func.apply(Map.of()));
    }

    private static void errorPropagatesThroughHandler() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.onWrap("err_ev", 0).apply((callNext, kwargs) -> callNext.apply(kwargs));
        Function<Map<String, Object>, Object> failing = framework.wrap("err_ev")
                .apply(kwargs -> {
                    throw new IllegalArgumentException("fail");
                });

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> failing.apply(Map.of()));
        assertEquals("fail", error.getMessage());
    }

    private static void registerAndWrapViaFactories() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> calls = new ArrayList<>();
        CallbackDecorators.createOnWrapDecorator(framework, "factory_ev", 0).apply((callNext, kwargs) -> {
            calls.add("handler");
            return callNext.apply(kwargs);
        });
        Function<Map<String, Object>, Object> func = CallbackDecorators
                .createWrapByEventDecorator(framework, "factory_ev")
                .apply(kwargs -> {
                    calls.add("func");
                    return 99;
                });

        assertEquals(99, func.apply(Map.of()));
        assertEquals(List.of("handler", "func"), calls);
    }

    private static void priorityViaFactory() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> order = new ArrayList<>();
        CallbackDecorators.createOnWrapDecorator(framework, "prio_ev", 1)
                .apply((callNext, kwargs) -> {
                    order.add("low");
                    return callNext.apply(kwargs);
                });
        CallbackDecorators.createOnWrapDecorator(framework, "prio_ev", 10)
                .apply((callNext, kwargs) -> {
                    order.add("high");
                    return callNext.apply(kwargs);
                });
        Function<Map<String, Object>, Object> func = CallbackDecorators
                .createWrapByEventDecorator(framework, "prio_ev")
                .apply(kwargs -> 0);

        func.apply(Map.of());

        assertEquals(List.of("high", "low"), order);
    }

    private static void disabledHandlerSkipped() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.onWrap("dis_ev", 0).apply((callNext, kwargs) -> "from_handler");
        Function<Map<String, Object>, Object> func = framework.wrap("dis_ev").apply(kwargs -> "from_func");

        String key = CallbackDecorators.WRAP_EVENT_PREFIX + "dis_ev";
        framework.getCallbacks().get(key).get(0).setEnabled(false);

        assertEquals("from_func", func.apply(Map.of()));
    }

    private static Map<String, Object> mutable(Map<String, Object> kwargs) {
        return new LinkedHashMap<>(kwargs);
    }

    private static Iterator<Integer> iteratorOf(Integer... values) {
        return List.of(values).iterator();
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> toList(Object iteratorValue) {
        Iterator<Integer> iterator = (Iterator<Integer>) iteratorValue;
        List<Integer> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }
}
