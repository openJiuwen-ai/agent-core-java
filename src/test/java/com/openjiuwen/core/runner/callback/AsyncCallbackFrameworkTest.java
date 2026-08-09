/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code AsyncCallbackFramework} in
 * {@code openjiuwen/core/runner/callback/framework.py}.
 */
class AsyncCallbackFrameworkTest {

    @Test
    void triggerRunsNonTransformCallbacksByPriority() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> order = new ArrayList<>();

        framework.registerSync("event", named("low", kwargs -> {
            order.add("low");
            return kwargs.get("value") + "-low";
        }), 1, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, "");
        framework.registerSync("event", named("transform", kwargs -> {
            order.add("transform");
            return "ignored";
        }), 20, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, "transform");
        framework.registerSync("event", named("high", kwargs -> {
            order.add("high");
            return kwargs.get("value") + "-high";
        }), 10, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, "");

        List<Object> results = framework.triggerResults("event", new Object[]{"arg"}, Map.of("value", "base"));

        assertEquals(List.of("high", "low"), order);
        assertEquals(List.of("base-high", "base-low"), results);
    }

    @Test
    void triggerAppliesFiltersAndDisablesOnceCallbacks() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        EventFilter modifier = new EventFilter("modifier") {
            @Override
            public FilterResult filter(
                    String event,
                    Function<Map<String, Object>, Object> callback,
                    Object[] args,
                    Map<String, Object> kwargs
            ) {
                return FilterResult.modifyResult(args, Map.of("value", 42));
            }
        };
        framework.addFilter("event", modifier);
        framework.registerSync("event", named("once", kwargs -> kwargs.get("value")), 0, true, "default",
                Set.of(), List.of(), null, null, 0, 0.0, null, "");

        assertEquals(List.of(42), framework.triggerResults("event", Map.of("value", 1)));
        assertEquals(List.of(), framework.triggerResults("event", Map.of("value", 1)));
    }

    @Test
    void triggerTransformReturnsLastTransformOrNoop() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();

        assertSame(AsyncCallbackFramework.TRANSFORM_NOOP,
                framework.triggerTransform("transform", new Object[0], Map.of()));

        framework.registerSync("transform", named("first", kwargs -> "first"), 1, false, "default",
                Set.of(), List.of(), null, null, 0, 0.0, null, "transform");
        framework.registerSync("transform", named("second", kwargs -> "second"), 0, false, "default",
                Set.of(), List.of(), null, null, 0, 0.0, null, "transform");

        assertEquals("second", framework.triggerTransform("transform", new Object[0], Map.of()));
    }

    @Test
    void triggerChainUsesRegisteredCallbacksAndHandlers() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> rollbackOrder = new ArrayList<>();

        framework.registerSync("chain", named("first", kwargs -> "first"), 10, false, "default",
                Set.of(), List.of(), kwargs -> {
                    rollbackOrder.add("first");
                    return null;
                }, null, 0, 0.0, null, "");
        framework.registerSync("chain", named("second", kwargs -> ChainResult.builder()
                        .action(ChainAction.ROLLBACK)
                        .error(new IllegalStateException("stop"))
                        .build()),
                1, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, "");

        ChainResult result = framework.triggerChain("chain", new Object[0], Map.of());

        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertEquals(List.of("first"), rollbackOrder);
    }

    @Test
    void historyHooksMetricsAndQueriesAreRecorded() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<HookType> hooks = new ArrayList<>();
        framework.enableEventHistory(true);
        framework.addHook("event", HookType.BEFORE, kwargs -> hooks.add(HookType.BEFORE));
        framework.addHook("event", HookType.AFTER, kwargs -> hooks.add(HookType.AFTER));
        framework.registerSync("event", named("callback", kwargs -> "ok"), 0, false, "ns",
                Set.of("tag"), List.of(), null, null, 0, 0.0, null, "");

        framework.triggerResults("event", Map.of("value", 1));

        assertEquals(List.of(HookType.BEFORE, HookType.AFTER), hooks);
        assertEquals(1, framework.getEventHistory("event", null).size());
        assertTrue(framework.getMetrics().containsKey("event:callback"));
        assertEquals(List.of("event"), framework.listEvents("ns"));
        assertEquals(1, framework.listCallbacks("event").size());
        assertEquals(1, framework.getStatistics().get("total_events"));
    }

    @Test
    void unregisterByTagsRemovesMatchingCallbacks() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.registerSync("event", named("tagged", kwargs -> "removed"), 0, false, "default",
                Set.of("remove"), List.of(), null, null, 0, 0.0, null, "");
        framework.registerSync("event", named("kept", kwargs -> "kept"), 0, false, "default",
                Set.of("keep"), List.of(), null, null, 0, 0.0, null, "");

        framework.unregisterByTags(Set.of("remove"));

        assertEquals(List.of("kept"), framework.triggerResults("event"));
    }

    @Test
    void timeoutParallelAndGeneratorModesReturnExpectedValues() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework(false, false);
        framework.registerSync("parallel", named("slow", kwargs -> CompletableFuture.supplyAsync(() -> "one")),
                0, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, "");
        framework.registerSync("generator", named("iter", kwargs -> List.of("a", "b").iterator()),
                0, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, "");
        framework.registerSync("timeout", named("too-slow", kwargs -> CompletableFuture.supplyAsync(() -> {
            sleepQuietly(200L);
            return "late";
        })), 0, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, "");

        assertEquals(List.of("one"), framework.triggerParallel("parallel", new Object[0], Map.of()));
        assertEquals(List.of("one"), framework.triggerParallel("parallel", new Object[0], Map.of()));
        Iterator<Object> generated = framework.triggerGenerator("generator", new Object[0], Map.of());
        assertEquals(List.of("a", "b"), toList(generated));
        assertTrue(framework.triggerWithTimeout("timeout", 0.05, new Object[0], Map.of()).isEmpty());
    }

    @Test
    void parallelCallbacksUseSharedBoundedModulePool() throws Exception {
        Field poolField = AsyncCallbackFramework.class.getDeclaredField("PARALLEL_EXECUTOR");
        poolField.setAccessible(true);
        ExecutorService executor = (ExecutorService) poolField.get(null);
        assertTrue(executor instanceof ThreadPoolExecutor);
        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
        assertTrue(pool.getQueue() instanceof ArrayBlockingQueue);
        assertEquals(pool.getMaximumPoolSize(), pool.getCorePoolSize());
        assertFalse(pool.isShutdown());
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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
