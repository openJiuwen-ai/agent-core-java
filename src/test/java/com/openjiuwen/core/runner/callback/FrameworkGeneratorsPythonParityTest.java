/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.runner.callback.test_framework_generators} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_generators.py}.</p>
 */
class FrameworkGeneratorsPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_trigger_stream_basic",
            "test_trigger_stream_preserves_order",
            "test_trigger_stream_with_generator_callback",
            "test_trigger_stream_handles_errors",
            "test_trigger_generator_basic",
            "test_trigger_generator_multiple_callbacks",
            "test_trigger_generator_mixed_callbacks",
            "test_trigger_generator_respects_priority",
            "test_trigger_generator_handles_errors",
            "test_emit_after_stream_basic",
            "test_emit_after_stream_custom_item_key",
            "test_emit_after_stream_multiple_handlers",
            "test_emit_after_stream_preserves_generator",
            "test_emit_after_stream_with_exception",
            "test_trigger_stream_logs_and_raises_error",
            "test_emit_after_stream_reraises_error",
            "test_trigger_generator_disabled_callback",
            "test_trigger_generator_stop_filter",
            "test_trigger_generator_coroutine_returning_async_gen",
            "test_trigger_generator_sync_result",
            "test_trigger_generator_metrics_collection",
            "test_trigger_generator_once_callback",
            "test_trigger_generator_error_with_metrics",
            "test_trigger_generator_error_logging",
            "test_generator_respects_rate_limit",
            "test_generator_skip_filter_logging"
    );

    @TestFactory
    Collection<DynamicTest> pythonFrameworkGeneratorCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        switch (name) {
            case "test_trigger_stream_basic" -> triggerStreamBasic();
            case "test_trigger_stream_preserves_order" -> triggerStreamPreservesOrder();
            case "test_trigger_stream_with_generator_callback" -> triggerStreamWithGeneratorCallback();
            case "test_trigger_stream_handles_errors" -> triggerStreamHandlesErrors();
            case "test_trigger_generator_basic" -> triggerGeneratorBasic();
            case "test_trigger_generator_multiple_callbacks" -> triggerGeneratorMultipleCallbacks();
            case "test_trigger_generator_mixed_callbacks" -> triggerGeneratorMixedCallbacks();
            case "test_trigger_generator_respects_priority" -> triggerGeneratorRespectsPriority();
            case "test_trigger_generator_handles_errors" -> triggerGeneratorHandlesErrors();
            case "test_emit_after_stream_basic" -> emitAfterStreamBasic();
            case "test_emit_after_stream_custom_item_key" -> emitAfterStreamCustomItemKey();
            case "test_emit_after_stream_multiple_handlers" -> emitAfterStreamMultipleHandlers();
            case "test_emit_after_stream_preserves_generator" -> emitAfterStreamPreservesGenerator();
            case "test_emit_after_stream_with_exception" -> emitAfterStreamWithException();
            case "test_trigger_stream_logs_and_raises_error" -> triggerStreamLogsAndRaisesError();
            case "test_emit_after_stream_reraises_error" -> emitAfterStreamReraisesError();
            case "test_trigger_generator_disabled_callback" -> triggerGeneratorDisabledCallback();
            case "test_trigger_generator_stop_filter" -> triggerGeneratorStopFilter();
            case "test_trigger_generator_coroutine_returning_async_gen" -> triggerGeneratorCoroutineReturningAsyncGen();
            case "test_trigger_generator_sync_result" -> triggerGeneratorSyncResult();
            case "test_trigger_generator_metrics_collection" -> triggerGeneratorMetricsCollection();
            case "test_trigger_generator_once_callback" -> triggerGeneratorOnceCallback();
            case "test_trigger_generator_error_with_metrics" -> triggerGeneratorErrorWithMetrics();
            case "test_trigger_generator_error_logging" -> triggerGeneratorErrorLogging();
            case "test_generator_respects_rate_limit" -> generatorRespectsRateLimit();
            case "test_generator_skip_filter_logging" -> generatorSkipFilterLogging();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void triggerStreamBasic() {
        AsyncCallbackFramework framework = framework();
        register(framework, "process", "process_item", kwargs ->
                "processed: " + item(kwargs).get("value"));

        List<Object> results = toList(framework.triggerStream("process", values(0, 1, 2), new Object[0], Map.of()));

        assertEquals(3, results.size());
        assertEquals("processed: 0", results.get(0));
    }

    private void triggerStreamPreservesOrder() {
        AsyncCallbackFramework framework = framework();
        List<Object> processedValues = new ArrayList<>();
        register(framework, "process", "process_item", kwargs -> {
            Object value = item(kwargs).get("value");
            processedValues.add(value);
            return value;
        });

        toList(framework.triggerStream("process", values(0, 1, 2, 3, 4), new Object[0], Map.of()));

        assertEquals(List.of(0, 1, 2, 3, 4), processedValues);
    }

    private void triggerStreamWithGeneratorCallback() {
        AsyncCallbackFramework framework = framework();
        register(framework, "expand", "expand_item", kwargs -> {
            int value = (Integer) item(kwargs).get("value");
            return List.of(
                    mapOf("original", value, "expanded", 0),
                    mapOf("original", value, "expanded", 1),
                    mapOf("original", value, "expanded", 2)
            );
        });

        List<Object> results = toList(framework.triggerStream("expand", values(0, 1), new Object[0], Map.of()));

        assertEquals(6, results.size());
    }

    private void triggerStreamHandlesErrors() {
        AsyncCallbackFramework framework = framework();
        AtomicInteger successCount = new AtomicInteger();
        register(framework, "process", "process_item", kwargs -> {
            int value = (Integer) item(kwargs).get("value");
            if (value == 1) {
                throw new IllegalArgumentException("Error on item 1");
            }
            successCount.incrementAndGet();
            return "ok: " + value;
        });

        List<Object> results = toList(framework.triggerStream("process", values(0, 1, 2), new Object[0], Map.of()));

        assertEquals(2, successCount.get());
        assertEquals(2, results.size());
    }

    private void triggerGeneratorBasic() {
        AsyncCallbackFramework framework = framework();
        register(framework, "stream", "generator_callback", kwargs -> List.of("item_0", "item_1", "item_2"));

        assertEquals(List.of("item_0", "item_1", "item_2"),
                toList(framework.triggerGenerator("stream", new Object[0], Map.of())));
    }

    private void triggerGeneratorMultipleCallbacks() {
        AsyncCallbackFramework framework = framework();
        register(framework, "stream", "generator1", kwargs -> List.of(
                mapOf("source", "gen1", "value", 0),
                mapOf("source", "gen1", "value", 1)
        ));
        register(framework, "stream", "generator2", kwargs -> List.of(
                mapOf("source", "gen2", "value", 0),
                mapOf("source", "gen2", "value", 1)
        ));

        List<Object> results = toList(framework.triggerGenerator("stream", new Object[0], Map.of()));

        assertEquals(4, results.size());
        assertEquals(2, results.stream().filter(item -> "gen1".equals(asMap(item).get("source"))).count());
        assertEquals(2, results.stream().filter(item -> "gen2".equals(asMap(item).get("source"))).count());
    }

    private void triggerGeneratorMixedCallbacks() {
        AsyncCallbackFramework framework = framework();
        register(framework, "mixed", "regular_callback", kwargs -> mapOf("type", "regular", "value", 100));
        register(framework, "mixed", "generator_callback", kwargs -> List.of(
                mapOf("type", "generator", "value", 0),
                mapOf("type", "generator", "value", 1),
                mapOf("type", "generator", "value", 2)
        ));

        List<Object> results = toList(framework.triggerGenerator("mixed", new Object[0], Map.of()));

        assertEquals(4, results.size());
        assertEquals(1, results.stream().filter(item -> "regular".equals(asMap(item).get("type"))).count());
        assertEquals(3, results.stream().filter(item -> "generator".equals(asMap(item).get("type"))).count());
    }

    private void triggerGeneratorRespectsPriority() {
        AsyncCallbackFramework framework = framework();
        List<String> order = new ArrayList<>();
        register(framework, "stream", "high_priority", 10, false, null, kwargs -> {
            order.add("high_start");
            order.add("high_end");
            return List.of("high_item");
        });
        register(framework, "stream", "low_priority", 1, false, null, kwargs -> {
            order.add("low_start");
            order.add("low_end");
            return List.of("low_item");
        });

        toList(framework.triggerGenerator("stream", new Object[0], Map.of()));

        assertEquals("high_start", order.get(0));
    }

    private void triggerGeneratorHandlesErrors() {
        AsyncCallbackFramework framework = framework();
        register(framework, "stream", "failing_callback", 10, false, null, kwargs -> {
            throw new IllegalArgumentException("Error!");
        });
        register(framework, "stream", "success_callback", 1, false, null, kwargs -> List.of("success"));

        assertEquals(List.of("success"), toList(framework.triggerGenerator("stream", new Object[0], Map.of())));
    }

    private void emitAfterStreamBasic() {
        AsyncCallbackFramework framework = framework();
        List<Object> eventItems = new ArrayList<>();
        register(framework, "chunk_ready", "on_chunk", kwargs -> {
            eventItems.add(kwargs.get("item"));
            return null;
        });
        Function<Map<String, Object>, Object> process = framework.emitAfter(
                "chunk_ready", "result", "item", false, "per_item", null)
                .apply(kwargs -> List.of(mapOf("index", 0), mapOf("index", 1), mapOf("index", 2)).iterator());

        List<Object> consumed = toList(asIterator(process.apply(Map.of())));

        assertEquals(3, eventItems.size());
        assertEquals(3, consumed.size());
        assertEquals(eventItems, consumed);
    }

    private void emitAfterStreamCustomItemKey() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> receivedKwargs = new ArrayList<>();
        register(framework, "event", "handler", kwargs -> {
            receivedKwargs.add(pythonKwargs(kwargs));
            return null;
        });
        Function<Map<String, Object>, Object> generate = framework.emitAfter(
                "event", "result", "data", false, "per_item", null)
                .apply(kwargs -> List.of(mapOf("value", 1), mapOf("value", 2)).iterator());

        toList(asIterator(generate.apply(Map.of())));

        assertEquals(mapOf("value", 1), receivedKwargs.get(0).get("data"));
        assertEquals(mapOf("value", 2), receivedKwargs.get(1).get("data"));
    }

    private void emitAfterStreamMultipleHandlers() {
        AsyncCallbackFramework framework = framework();
        List<Object> handler1Items = new ArrayList<>();
        List<Object> handler2Items = new ArrayList<>();
        register(framework, "event", "handler1", kwargs -> {
            handler1Items.add(kwargs.get("item"));
            return null;
        });
        register(framework, "event", "handler2", kwargs -> {
            handler2Items.add(kwargs.get("item"));
            return null;
        });
        Function<Map<String, Object>, Object> generate = framework.emitAfter(
                "event", "result", "item", false, "per_item", null)
                .apply(kwargs -> List.of("item1", "item2").iterator());

        toList(asIterator(generate.apply(Map.of())));

        assertEquals(2, handler1Items.size());
        assertEquals(2, handler2Items.size());
    }

    private void emitAfterStreamPreservesGenerator() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> originalItems = List.of(mapOf("id", 1), mapOf("id", 2), mapOf("id", 3));
        register(framework, "event", "handler", kwargs -> null);
        Function<Map<String, Object>, Object> generate = framework.emitAfter(
                "event", "result", "item", false, "per_item", null)
                .apply(kwargs -> originalItems.iterator());

        assertEquals(originalItems, toList(asIterator(generate.apply(Map.of()))));
    }

    private void emitAfterStreamWithException() {
        AsyncCallbackFramework framework = framework();
        AtomicInteger eventCount = new AtomicInteger();
        register(framework, "event", "handler", kwargs -> eventCount.incrementAndGet());
        Function<Map<String, Object>, Object> failingGenerator = framework.emitAfter(
                "event", "result", "item", false, "per_item", null)
                .apply(kwargs -> throwingIterator("Generator error!"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> failingGenerator.apply(Map.of()));

        assertTrue(error.getMessage().contains("Generator error"));
        assertEquals(1, eventCount.get());
    }

    private void triggerStreamLogsAndRaisesError() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        register(framework, "process", "callback", kwargs -> item(kwargs));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> framework.triggerStream("process", streamThatFailsAfterFirstItem(), new Object[0], Map.of()));

        assertTrue(error.getMessage().contains("Stream error"));
        verify(log).error("Stream processing error: {}", error.getMessage(), error);
    }

    private void emitAfterStreamReraisesError() {
        AsyncCallbackFramework framework = framework();
        register(framework, "event", "handler", kwargs -> null);
        Function<Map<String, Object>, Object> failingGenerator = framework.emitAfter(
                "event", "result", "item", false, "per_item", null)
                .apply(kwargs -> throwingIterator("Generator failed!"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> failingGenerator.apply(Map.of()));

        assertTrue(error.getMessage().contains("Generator failed"));
    }

    private void triggerGeneratorDisabledCallback() {
        AsyncCallbackFramework framework = framework();
        register(framework, "stream", "callback", kwargs -> List.of("item"));
        framework.getCallbacks().get("stream").get(0).setEnabled(false);

        assertEquals(List.of(), toList(framework.triggerGenerator("stream", new Object[0], Map.of())));
    }

    private void triggerGeneratorStopFilter() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        framework.addFilter("stream", new ConditionalFilter((event, callback, args, kwargs) -> false,
                FilterAction.STOP));
        register(framework, "stream", "callback", kwargs -> List.of("item"));

        assertEquals(List.of(), toList(framework.triggerGenerator("stream", new Object[0], Map.of())));
        verify(log).info("Filter stopped processing for {}", "stream");
    }

    private void triggerGeneratorCoroutineReturningAsyncGen() {
        AsyncCallbackFramework framework = framework();
        register(framework, "stream", "callback", kwargs ->
                CompletableFuture.completedFuture(List.of("item1", "item2").iterator()));

        List<Object> results = toList(framework.triggerGenerator("stream", new Object[0], Map.of()));

        assertTrue(results.contains("item1") || results.contains("item2") || !results.isEmpty());
    }

    private void triggerGeneratorSyncResult() {
        AsyncCallbackFramework framework = framework();
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "stream", "sync_callback", kwargs -> {
            callCount.incrementAndGet();
            return "sync_result";
        });

        List<Object> results = toList(framework.triggerGenerator("stream", new Object[0], Map.of()));

        assertEquals(1, callCount.get());
        assertTrue(results.contains("sync_result"));
    }

    private void triggerGeneratorMetricsCollection() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        register(framework, "stream", "callback", kwargs -> List.of("item"));

        toList(framework.triggerGenerator("stream", new Object[0], Map.of()));

        Map<String, Map<String, Object>> metrics = framework.getMetrics();
        assertTrue(metrics.containsKey("stream:callback"));
        assertEquals(1, metrics.get("stream:callback").get("call_count"));
    }

    private void triggerGeneratorOnceCallback() {
        AsyncCallbackFramework framework = framework();
        register(framework, "stream", "once_callback", 0, true, null, kwargs -> List.of("item"));

        List<Object> results1 = toList(framework.triggerGenerator("stream", new Object[0], Map.of()));
        List<Object> results2 = toList(framework.triggerGenerator("stream", new Object[0], Map.of()));

        assertEquals(List.of("item"), results1);
        assertEquals(List.of(), results2);
    }

    private void triggerGeneratorErrorWithMetrics() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        register(framework, "stream", "failing_callback", kwargs -> {
            throw new IllegalArgumentException("Error!");
        });

        assertEquals(List.of(), toList(framework.triggerGenerator("stream", new Object[0], Map.of())));

        Map<String, Map<String, Object>> metrics = framework.getMetrics();
        assertTrue(metrics.containsKey("stream:failing_callback"));
        assertEquals(1, metrics.get("stream:failing_callback").get("error_count"));
    }

    private void triggerGeneratorErrorLogging() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        register(framework, "stream", "failing_callback", kwargs -> {
            throw new IllegalArgumentException("Test error!");
        });

        assertEquals(List.of(), toList(framework.triggerGenerator("stream", new Object[0], Map.of())));
        verify(log).error(eq("Callback {} failed in generator mode: {}"), eq("failing_callback"),
                eq("Test error!"), isA(IllegalArgumentException.class));
    }

    private void generatorRespectsRateLimit() {
        AsyncCallbackFramework framework = framework();
        framework.addFilter("stream", new RateLimitFilter(2, 1.0));
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "stream", "generator_callback", kwargs -> {
            callCount.incrementAndGet();
            return List.of("item");
        });

        List<Object> results = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            results.addAll(toList(framework.triggerGenerator("stream", new Object[0], Map.of())));
        }

        assertEquals(2, callCount.get());
        assertEquals(2, results.size());
    }

    private void generatorSkipFilterLogging() {
        Logger log = mock(Logger.class);
        AsyncCallbackFramework framework = frameworkWithLogging(log);
        framework.addFilter("stream", new ValidationFilter((args, kwargs) -> false));
        AtomicInteger callCount = new AtomicInteger();
        register(framework, "stream", "callback", kwargs -> {
            callCount.incrementAndGet();
            return List.of("item");
        });

        List<Object> results = toList(framework.triggerGenerator("stream", new Object[0], Map.of()));

        assertEquals(List.of(), results);
        assertEquals(0, callCount.get());
        verify(log).debug("Filter skipped callback {}: {}", "callback", "Argument validation failed");
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(false, false);
    }

    private static AsyncCallbackFramework frameworkWithLogging(Logger logger) {
        return new AsyncCallbackFramework(false, true, logger);
    }

    private static AsyncCallbackFramework frameworkWithMetrics() {
        return new AsyncCallbackFramework(true, false);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            Function<Map<String, Object>, Object> callback
    ) {
        register(framework, event, name, 0, false, null, callback);
    }

    private static void register(
            AsyncCallbackFramework framework,
            String event,
            String name,
            int priority,
            boolean once,
            Double timeout,
            Function<Map<String, Object>, Object> callback
    ) {
        framework.registerSync(event, named(name, callback), priority, once, "default", Set.of(), List.of(),
                null, null, 0, 0.0, timeout, "");
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private static Iterator<Map<String, Object>> values(int... values) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int value : values) {
            items.add(mapOf("value", value));
        }
        return items.iterator();
    }

    private static Iterator<Map<String, Object>> streamThatFailsAfterFirstItem() {
        return new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                if (index == 1) {
                    throw new RuntimeException("Stream error!");
                }
                return index < 2;
            }

            @Override
            public Map<String, Object> next() {
                index++;
                return mapOf("value", 1);
            }
        };
    }

    private static Iterator<Map<String, Object>> throwingIterator(String message) {
        return new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < 2;
            }

            @Override
            public Map<String, Object> next() {
                if (index++ == 0) {
                    return mapOf("id", 1);
                }
                throw new IllegalArgumentException(message);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> kwargs) {
        return (Map<String, Object>) args(kwargs)[0];
    }

    private static Object[] args(Map<String, Object> kwargs) {
        return (Object[]) kwargs.get("_args");
    }

    @SuppressWarnings("unchecked")
    private static Iterator<Object> asIterator(Object value) {
        assertInstanceOf(Iterator.class, value);
        return (Iterator<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<Object> toList(Iterator<?> iterator) {
        List<Object> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static Map<String, Object> pythonKwargs(Map<String, Object> kwargs) {
        Map<String, Object> copy = new LinkedHashMap<>(kwargs);
        copy.remove("_args");
        return copy;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private record NamedCallback(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) implements Function<Map<String, Object>, Object> {

        @Override
        public Object apply(Map<String, Object> kwargs) {
            return delegate.apply(kwargs);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
