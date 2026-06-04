/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.ConditionalFilter;
import com.openjiuwen.core.runner.callback.FilterAction;
import com.openjiuwen.core.runner.callback.RateLimitFilter;
import com.openjiuwen.core.runner.callback.ValidationFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Framework generators test cases.
 *
 * <p>Mirrors Python's {@code test_framework_generators.py} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_generators}.</p>
 */
@DisplayName("Framework Generators Tests")
class TestFrameworkGenerators {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(true, false);
    }

    private static Object[] argsFrom(Map<String, Object> kwargs) {
        Object raw = kwargs.get("_args");
        return raw instanceof Object[] args ? args : new Object[0];
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    @Test
    @DisplayName("test_trigger_stream_basic")
    void testTriggerStreamBasic() {
        framework.on("process", kwargs -> "processed: " + ((Map<?, ?>) argsFrom(kwargs)[0]).get("value"), "process_item");

        List<Object> results = collect(framework.triggerStream("process", List.of(
                Map.of("value", 0), Map.of("value", 1), Map.of("value", 2)).iterator(), null, new HashMap<>()));

        assertEquals(3, results.size());
        assertEquals("processed: 0", results.get(0));
    }

    @Test
    @DisplayName("test_trigger_stream_preserves_order")
    void testTriggerStreamPreservesOrder() {
        List<Object> processedValues = new ArrayList<>();
        framework.on("process", kwargs -> {
            Object value = ((Map<?, ?>) argsFrom(kwargs)[0]).get("value");
            processedValues.add(value);
            return value;
        }, "process_item");

        collect(framework.triggerStream("process", List.of(
                Map.of("value", 0), Map.of("value", 1), Map.of("value", 2), Map.of("value", 3), Map.of("value", 4))
                .iterator(), null, new HashMap<>()));

        assertEquals(List.of(0, 1, 2, 3, 4), processedValues);
    }

    @Test
    @DisplayName("test_trigger_stream_with_generator_callback")
    void testTriggerStreamWithGeneratorCallback() {
        framework.on("expand", kwargs -> {
            int value = (int) ((Map<?, ?>) argsFrom(kwargs)[0]).get("value");
            List<Map<String, Object>> expanded = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                expanded.add(Map.of("original", value, "expanded", i));
            }
            return expanded;
        }, "expand_item");

        List<Object> results = collect(framework.triggerStream("expand",
                List.of(Map.of("value", 0), Map.of("value", 1)).iterator(), null, new HashMap<>()));

        assertEquals(6, results.size());
    }

    @Test
    @DisplayName("test_trigger_stream_handles_errors")
    void testTriggerStreamHandlesErrors() {
        int[] successCount = {0};
        framework.on("process", kwargs -> {
            int value = (int) ((Map<?, ?>) argsFrom(kwargs)[0]).get("value");
            if (value == 1) {
                throw new IllegalArgumentException("Error on item 1");
            }
            successCount[0]++;
            return "ok: " + value;
        }, "process_item");

        List<Object> results = collect(framework.triggerStream("process",
                List.of(Map.of("value", 0), Map.of("value", 1), Map.of("value", 2)).iterator(), null, new HashMap<>()));

        assertEquals(2, successCount[0]);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("test_trigger_generator_basic")
    void testTriggerGeneratorBasic() {
        framework.on("stream", kwargs -> List.of("item_0", "item_1", "item_2"), "generator_callback");

        assertEquals(List.of("item_0", "item_1", "item_2"),
                collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>())));
    }

    @Test
    @DisplayName("test_trigger_generator_multiple_callbacks")
    void testTriggerGeneratorMultipleCallbacks() {
        framework.on("stream", kwargs -> List.of(Map.of("source", "gen1", "value", 0),
                Map.of("source", "gen1", "value", 1)), "generator1");
        framework.on("stream", kwargs -> List.of(Map.of("source", "gen2", "value", 0),
                Map.of("source", "gen2", "value", 1)), "generator2");

        List<Object> results = collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>()));

        assertEquals(4, results.size());
        assertEquals(2, results.stream().filter(item -> "gen1".equals(((Map<?, ?>) item).get("source"))).count());
        assertEquals(2, results.stream().filter(item -> "gen2".equals(((Map<?, ?>) item).get("source"))).count());
    }

    @Test
    @DisplayName("test_trigger_generator_mixed_callbacks")
    void testTriggerGeneratorMixedCallbacks() {
        framework.on("mixed", kwargs -> Map.of("type", "regular", "value", 100), "regular_callback");
        framework.on("mixed", kwargs -> List.of(Map.of("type", "generator", "value", 0),
                Map.of("type", "generator", "value", 1), Map.of("type", "generator", "value", 2)), "generator_callback");

        List<Object> results = collect(framework.triggerGenerator("mixed", new Object[0], new HashMap<>()));

        assertEquals(4, results.size());
        assertEquals(1, results.stream().filter(item -> "regular".equals(((Map<?, ?>) item).get("type"))).count());
        assertEquals(3, results.stream().filter(item -> "generator".equals(((Map<?, ?>) item).get("type"))).count());
    }

    @Test
    @DisplayName("test_trigger_generator_respects_priority")
    void testTriggerGeneratorRespectsPriority() {
        List<String> order = new ArrayList<>();
        framework.register("stream", kwargs -> {
            order.add("high_start");
            order.add("high_end");
            return List.of("high_item");
        }, 10, "high_priority");
        framework.register("stream", kwargs -> {
            order.add("low_start");
            order.add("low_end");
            return List.of("low_item");
        }, 1, "low_priority");

        collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>()));

        assertEquals("high_start", order.get(0));
    }

    @Test
    @DisplayName("test_trigger_generator_handles_errors")
    void testTriggerGeneratorHandlesErrors() {
        framework.register("stream", kwargs -> {
            throw new IllegalArgumentException("Error!");
        }, 10, "failing_callback");
        framework.register("stream", kwargs -> List.of("success"), 1, "success_callback");

        List<Object> results = collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>()));

        assertEquals(List.of("success"), results);
    }

    @Test
    @DisplayName("test_emit_after_stream_basic")
    void testEmitAfterStreamBasic() {
        List<Object> eventItems = new ArrayList<>();
        framework.on("chunk_ready", kwargs -> {
            eventItems.add(kwargs.get("item"));
            return null;
        }, "on_chunk");
        Function<Map<String, Object>, Object> process = kwargs -> List.of(Map.of("index", 0), Map.of("index", 1), Map.of("index", 2));

        List<Object> consumed = collect((Iterator<Object>) framework.emitsStream("chunk_ready", process, "item").apply(new HashMap<>()));

        assertEquals(3, eventItems.size());
        assertEquals(consumed, eventItems);
    }

    @Test
    @DisplayName("test_emit_after_stream_custom_item_key")
    void testEmitAfterStreamCustomItemKey() {
        List<Map<String, Object>> receivedKwargs = new ArrayList<>();
        framework.on("event", kwargs -> {
            receivedKwargs.add(new HashMap<>(kwargs));
            return null;
        }, "handler");

        collect((Iterator<Object>) framework.emitsStream("event",
                kwargs -> List.of(Map.of("value", 1), Map.of("value", 2)), "data").apply(new HashMap<>()));

        assertEquals(Map.of("value", 1), receivedKwargs.get(0).get("data"));
        assertEquals(Map.of("value", 2), receivedKwargs.get(1).get("data"));
    }

    @Test
    @DisplayName("test_emit_after_stream_multiple_handlers")
    void testEmitAfterStreamMultipleHandlers() {
        List<Object> handler1Items = new ArrayList<>();
        List<Object> handler2Items = new ArrayList<>();
        framework.on("event", kwargs -> {
            handler1Items.add(kwargs.get("item"));
            return null;
        }, "handler1");
        framework.on("event", kwargs -> {
            handler2Items.add(kwargs.get("item"));
            return null;
        }, "handler2");

        collect((Iterator<Object>) framework.emitsStream("event", kwargs -> List.of("item1", "item2"), "item")
                .apply(new HashMap<>()));

        assertEquals(2, handler1Items.size());
        assertEquals(2, handler2Items.size());
    }

    @Test
    @DisplayName("test_emit_after_stream_preserves_generator")
    void testEmitAfterStreamPreservesGenerator() {
        List<Map<String, Integer>> originalItems = List.of(Map.of("id", 1), Map.of("id", 2), Map.of("id", 3));
        framework.on("event", kwargs -> null, "handler");

        List<Object> consumed = collect((Iterator<Object>) framework.emitsStream("event", kwargs -> originalItems, "item")
                .apply(new HashMap<>()));

        assertEquals(originalItems, consumed);
    }

    @Test
    @DisplayName("test_emit_after_stream_with_exception")
    void testEmitAfterStreamWithException() {
        int[] eventCount = {0};
        framework.on("event", kwargs -> {
            eventCount[0]++;
            return null;
        }, "handler");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> framework.emitsStream("event", kwargs -> throwingIterator("Generator error!"), "item").apply(new HashMap<>()));

        assertEquals("Generator error!", thrown.getMessage());
        assertEquals(1, eventCount[0]);
    }

    @Test
    @DisplayName("test_trigger_stream_logs_and_raises_error")
    void testTriggerStreamLogsAndRaisesError() {
        framework.on("process", kwargs -> argsFrom(kwargs)[0], "callback");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> framework.triggerStream("process", throwingInputStream(), null, new HashMap<>()));

        assertEquals("Stream error!", thrown.getMessage());
    }

    @Test
    @DisplayName("test_emit_after_stream_reraises_error")
    void testEmitAfterStreamReraisesError() {
        framework.on("event", kwargs -> null, "handler");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> framework.emitsStream("event", kwargs -> throwingIterator("Generator failed!"), "item").apply(new HashMap<>()));

        assertEquals("Generator failed!", thrown.getMessage());
    }

    @Test
    @DisplayName("test_trigger_generator_disabled_callback")
    void testTriggerGeneratorDisabledCallback() {
        CallbackInfo info = framework.on("stream", kwargs -> List.of("item"), "callback");
        info.setEnabled(false);

        assertTrue(collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>())).isEmpty());
    }

    @Test
    @DisplayName("test_trigger_generator_stop_filter")
    void testTriggerGeneratorStopFilter() {
        framework.addFilter("stream", new ConditionalFilter((event, callback, args, kwargs) -> false,
                FilterAction.STOP, "StopFilter"));
        framework.on("stream", kwargs -> List.of("item"), "callback");

        assertTrue(collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>())).isEmpty());
    }

    @Test
    @DisplayName("test_trigger_generator_coroutine_returning_async_gen")
    void testTriggerGeneratorCoroutineReturningAsyncGen() {
        framework.on("stream", kwargs -> createGenerator(), "callback");

        List<Object> results = collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>()));

        assertTrue(results.contains("item1") || results.contains("item2"));
    }

    @Test
    @DisplayName("test_trigger_generator_sync_result")
    void testTriggerGeneratorSyncResult() {
        int[] callCount = {0};
        framework.on("stream", kwargs -> {
            callCount[0]++;
            return "sync_result";
        }, "sync_callback");

        List<Object> results = collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>()));

        assertEquals(1, callCount[0]);
        assertTrue(results.contains("sync_result"));
    }

    @Test
    @DisplayName("test_trigger_generator_metrics_collection")
    void testTriggerGeneratorMetricsCollection() {
        framework.on("stream", kwargs -> List.of("item"), "callback");

        collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>()));

        assertTrue(framework.getMetrics().containsKey("stream:callback"));
        assertEquals(1, framework.getMetrics().get("stream:callback").get("call_count"));
    }

    @Test
    @DisplayName("test_trigger_generator_once_callback")
    void testTriggerGeneratorOnceCallback() {
        framework.register("stream", kwargs -> List.of("item"), 0, true, "default", null,
                null, null, null, 0, 0.0, null, "once_callback");

        List<Object> results1 = collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>()));
        List<Object> results2 = collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>()));

        assertEquals(List.of("item"), results1);
        assertTrue(results2.isEmpty());
    }

    @Test
    @DisplayName("test_trigger_generator_error_with_metrics")
    void testTriggerGeneratorErrorWithMetrics() {
        framework.on("stream", kwargs -> {
            throw new IllegalArgumentException("Error!");
        }, "failing_callback");

        collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>()));

        assertTrue(framework.getMetrics().containsKey("stream:failing_callback"));
        assertEquals(1, framework.getMetrics().get("stream:failing_callback").get("error_count"));
    }

    @Test
    @DisplayName("test_trigger_generator_error_logging")
    void testTriggerGeneratorErrorLogging() {
        framework.on("stream", kwargs -> {
            throw new IllegalArgumentException("Test error!");
        }, "failing_callback");

        assertTrue(collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>())).isEmpty());
        assertEquals(1, framework.getMetrics().get("stream:failing_callback").get("error_count"));
    }

    @Test
    @DisplayName("test_generator_respects_rate_limit")
    void testGeneratorRespectsRateLimit() {
        framework.addFilter("stream", new RateLimitFilter(2, 1.0));
        int[] callCount = {0};
        framework.on("stream", kwargs -> {
            callCount[0]++;
            return List.of("item");
        }, "generator_callback");

        List<Object> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.addAll(collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>())));
        }

        assertEquals(2, callCount[0]);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("test_generator_skip_filter_logging")
    void testGeneratorSkipFilterLogging() {
        framework.addFilter("stream", new ValidationFilter(kwargs -> false));
        framework.on("stream", kwargs -> List.of("item"), "callback");

        assertTrue(collect(framework.triggerGenerator("stream", new Object[0], new HashMap<>())).isEmpty());
    }

    private static Iterator<Object> createGenerator() {
        return List.of((Object) "item1", "item2").iterator();
    }

    private static Iterator<Object> throwingIterator(String message) {
        return new Iterator<>() {
            private int state = 0;

            @Override
            public boolean hasNext() {
                return state < 2;
            }

            @Override
            public Object next() {
                if (state == 0) {
                    state++;
                    return Map.of("id", 1);
                }
                state++;
                throw new IllegalArgumentException(message);
            }
        };
    }

    private static Iterator<Map<String, Object>> throwingInputStream() {
        return new Iterator<>() {
            private int state = 0;

            @Override
            public boolean hasNext() {
                return state < 2;
            }

            @Override
            public Map<String, Object> next() {
                if (state == 0) {
                    state++;
                    return Map.of("value", 1);
                }
                state++;
                throw new RuntimeException("Stream error!");
            }
        };
    }
}
