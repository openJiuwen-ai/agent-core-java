/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen/core/runner/callback/decorator.py}.
 */
class CallbackDecoratorsTest {

    @Test
    void onDecoratorRegistersCallbackAndWrapper() {
        FakeDecoratorFramework framework = new FakeDecoratorFramework();
        Function<Map<String, Object>, Object> wrapped = CallbackDecorators.createOnDecorator(
                framework, "event", 1, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, ""
        ).apply(kwargs -> kwargs.get("value"));

        Object result = wrapped.apply(Map.of("value", "ok"));

        assertEquals("ok", result);
        assertEquals(1, framework.registered.size());
        assertSame(wrapped, framework.registered.get(0).getWrapper());
    }

    @Test
    void emitAroundTriggersBeforeAndAfterEvents() {
        FakeDecoratorFramework framework = new FakeDecoratorFramework();
        Function<Map<String, Object>, Object> wrapped = CallbackDecorators.createEmitAroundDecorator(
                framework, "before", "after", true, true, "error"
        ).apply(kwargs -> "done");

        Object result = wrapped.apply(Map.of("_args", new Object[]{"a"}, "name", "demo"));

        assertEquals("done", result);
        assertEquals(List.of("before", "after"), framework.triggeredEvents);
        assertEquals("done", framework.triggerPayloads.get(1).get("result"));
    }

    @Test
    void transformIoByEventsAppliesInputAndOutputTransforms() {
        FakeDecoratorFramework framework = new FakeDecoratorFramework();
        framework.transformResults.put("input", Map.of("value", "mutated"));
        framework.transformResults.put("output", "final");

        Function<Map<String, Object>, Object> wrapped = CallbackDecorators.createTransformIoByEventsDecorator(
                framework, "input", "output", "result"
        ).apply(kwargs -> kwargs.get("value"));

        Object result = wrapped.apply(Map.of("value", "original"));

        assertEquals("final", result);
    }

    @Test
    void wrapDecoratorAppliesOutermostFirstOrder() {
        List<String> order = new ArrayList<>();
        WrapHandler outer = (next, kwargs) -> {
            order.add("outer-before");
            Object result = next.apply(kwargs);
            order.add("outer-after");
            return result;
        };
        WrapHandler inner = (next, kwargs) -> {
            order.add("inner-before");
            Object result = next.apply(kwargs);
            order.add("inner-after");
            return result;
        };

        Function<Map<String, Object>, Object> wrapped = CallbackDecorators.createWrapDecorator(outer, inner)
                .apply(kwargs -> {
                    order.add("base");
                    return "ok";
                });

        Object result = wrapped.apply(Map.of());

        assertEquals("ok", result);
        assertEquals(List.of("outer-before", "inner-before", "base", "inner-after", "outer-after"), order);
    }

    @Test
    void bindArgsNoDuplicatePrefersKeywordValue() {
        CallbackDecorators.BoundArgs bound = CallbackDecorators.bindArgsNoDuplicate(
                new Object[]{"a", "b"},
                Map.of("first", "kw"),
                List.of("first", "second")
        );

        assertEquals(1, bound.getArgs().length);
        assertEquals("b", bound.getArgs()[0]);
        assertTrue(bound.getKwargs().containsKey("first"));
    }

    private static final class FakeDecoratorFramework implements DecoratorFramework {

        private final List<CallbackInfo> registered = new ArrayList<>();
        private final List<String> triggeredEvents = new ArrayList<>();
        private final List<Map<String, Object>> triggerPayloads = new ArrayList<>();
        private final Map<String, Object> transformResults = new HashMap<>();
        private final Map<String, List<CallbackInfo>> callbacks = new LinkedHashMap<>();

        @Override
        public CallbackInfo registerSync(String event,
                                         Function<Map<String, Object>, Object> callback,
                                         int priority,
                                         boolean once,
                                         String namespace,
                                         Set<String> tags,
                                         List<EventFilter> filters,
                                         Function<Map<String, Object>, Object> rollbackHandler,
                                         Function<Map<String, Object>, Object> errorHandler,
                                         int maxRetries,
                                         double retryDelay,
                                         Double timeout,
                                         String callbackType) {
            CallbackInfo info = CallbackInfo.builder()
                    .callback(callback)
                    .priority(priority)
                    .once(once)
                    .namespace(namespace)
                    .tags(tags)
                    .maxRetries(maxRetries)
                    .retryDelay(retryDelay)
                    .timeout(timeout)
                    .callbackType(callbackType)
                    .build();
            registered.add(info);
            callbacks.computeIfAbsent(event, ignored -> new ArrayList<>()).add(info);
            return info;
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
            triggeredEvents.add(event);
            triggerPayloads.add(new HashMap<>(kwargs));
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            return transformResults.getOrDefault(event, CallbackDecorators.TRANSFORM_NOOP);
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return callbacks;
        }
    }
}
