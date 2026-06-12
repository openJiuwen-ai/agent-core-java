/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Mirrors Python's {@code decorator.py} in
 * {@code openjiuwen/core/runner/callback/decorator.py}.
 */
public final class CallbackDecorators {

    public static final Object TRANSFORM_NOOP = new Object();
    public static final String WRAP_EVENT_PREFIX = "__wrap__:";

    private CallbackDecorators() {
    }

    public static Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> createOnDecorator(
            DecoratorFramework framework,
            String event,
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
            String callbackType
    ) {
        return func -> {
            CallbackInfo info = framework.registerSync(
                    event,
                    func,
                    priority,
                    once,
                    namespace != null ? namespace : "default",
                    tags != null ? tags : new HashSet<>(),
                    filters != null ? filters : Collections.emptyList(),
                    rollbackHandler,
                    errorHandler,
                    maxRetries,
                    retryDelay,
                    timeout,
                    callbackType != null ? callbackType : ""
            );
            Function<Map<String, Object>, Object> wrapper = kwargs -> func.apply(kwargs);
            info.setWrapper(wrapper);
            return wrapper;
        };
    }

    public static Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> createEmitBeforeDecorator(
            DecoratorFramework framework,
            String event,
            boolean passArgs,
            Map<String, Object> extraKwargs
    ) {
        return wrapped -> kwargs -> {
            Map<String, Object> merged = mergedKwargs(kwargs, extraKwargs);
            if (passArgs) {
                framework.trigger(event, internalArgs(kwargs), merged);
            } else {
                framework.trigger(event, new Object[0], merged);
            }
            return wrapped.apply(kwargs);
        };
    }

    public static Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> createEmitAfterDecorator(
            DecoratorFramework framework,
            String event,
            String resultKey,
            String itemKey,
            boolean passArgs,
            String streamMode,
            Map<String, Object> extraKwargs
    ) {
        return wrapped -> kwargs -> {
            Object result = wrapped.apply(kwargs);
            if (result instanceof Iterator<?> iterator) {
                List<Object> collected = new ArrayList<>();
                while (iterator.hasNext()) {
                    Object item = iterator.next();
                    collected.add(item);
                    if (!"once".equals(streamMode)) {
                        Map<String, Object> perItem = mergedKwargs(kwargs, extraKwargs);
                        perItem.put(itemKey != null ? itemKey : "item", item);
                        framework.trigger(event, passArgs ? internalArgs(kwargs) : new Object[0], perItem);
                    }
                }
                if ("once".equals(streamMode)) {
                    Map<String, Object> onceKwargs = mergedKwargs(kwargs, extraKwargs);
                    onceKwargs.put(resultKey != null ? resultKey : "result", collected);
                    framework.trigger(event, passArgs ? internalArgs(kwargs) : new Object[0], onceKwargs);
                }
                return collected.iterator();
            }

            Map<String, Object> afterKwargs = mergedKwargs(kwargs, extraKwargs);
            afterKwargs.put(resultKey != null ? resultKey : "result", result);
            framework.trigger(event, passArgs ? internalArgs(kwargs) : new Object[0], afterKwargs);
            return result;
        };
    }

    public static Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> createEmitAroundDecorator(
            DecoratorFramework framework,
            String beforeEvent,
            String afterEvent,
            boolean passArgs,
            boolean passResult,
            String onErrorEvent
    ) {
        return wrapped -> kwargs -> {
            framework.trigger(beforeEvent, passArgs ? internalArgs(kwargs) : new Object[0], passArgs ? new HashMap<>(kwargs) : new HashMap<>());
            try {
                Object result = wrapped.apply(kwargs);
                Map<String, Object> afterKwargs = passArgs ? new HashMap<>(kwargs) : new HashMap<>();
                if (passResult) {
                    afterKwargs.put("result", result);
                }
                framework.trigger(afterEvent, passArgs ? internalArgs(kwargs) : new Object[0], afterKwargs);
                return result;
            } catch (RuntimeException exception) {
                if (onErrorEvent != null) {
                    Map<String, Object> errorKwargs = passArgs ? new HashMap<>(kwargs) : new HashMap<>();
                    errorKwargs.put("error", exception);
                    framework.trigger(onErrorEvent, passArgs ? internalArgs(kwargs) : new Object[0], errorKwargs);
                }
                throw exception;
            }
        };
    }

    public static Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> createTransformIoDecorator(
            Function<Map<String, Object>, Map<String, Object>> inputTransform,
            Function<Object, Object> outputTransform
    ) {
        return wrapped -> kwargs -> {
            Map<String, Object> finalKwargs = inputTransform != null ? inputTransform.apply(kwargs) : kwargs;
            Object result = wrapped.apply(finalKwargs);
            return outputTransform != null ? outputTransform.apply(result) : result;
        };
    }

    @SuppressWarnings("unchecked")
    public static Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> createTransformIoByEventsDecorator(
            DecoratorFramework framework,
            String inputEvent,
            String outputEvent,
            String resultKey
    ) {
        return wrapped -> kwargs -> {
            Map<String, Object> finalKwargs = kwargs;
            if (inputEvent != null) {
                Object transformed = framework.triggerTransform(inputEvent, internalArgs(kwargs), kwargs);
                if (transformed instanceof BoundArgs boundArgs) {
                    finalKwargs = new HashMap<>(boundArgs.getKwargs());
                    finalKwargs.put("_args", boundArgs.getArgs());
                } else if (transformed instanceof Map<?, ?> map) {
                    finalKwargs = (Map<String, Object>) map;
                }
            }

            Object result = wrapped.apply(finalKwargs);
            if (outputEvent == null) {
                return result;
            }

            Map<String, Object> outKwargs = new HashMap<>();
            outKwargs.put(resultKey != null ? resultKey : "result", result);
            Object transformed = framework.triggerTransform(outputEvent, new Object[0], outKwargs);
            return transformed == TRANSFORM_NOOP ? result : transformed;
        };
    }

    public static Function<WrapHandler, WrapHandler> createOnWrapDecorator(
            DecoratorFramework framework,
            String event,
            int priority
    ) {
        return handler -> {
            framework.registerSync(
                    WRAP_EVENT_PREFIX + event,
                    ignored -> handler,
                    priority,
                    false,
                    "default",
                    new HashSet<>(),
                    Collections.emptyList(),
                    null,
                    null,
                    0,
                    0.0,
                    null,
                    null
            );
            return handler;
        };
    }

    public static Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> createWrapByEventDecorator(
            DecoratorFramework framework,
            String event
    ) {
        return wrapped -> createWrapDecorator(getFrameworkWrapHandlers(framework, event)).apply(wrapped);
    }

    public static Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> createWrapDecorator(
            List<WrapHandler> handlers
    ) {
        return wrapped -> {
            if (handlers == null || handlers.isEmpty()) {
                return wrapped;
            }
            Function<Map<String, Object>, Object> chain = wrapped;
            for (int index = handlers.size() - 1; index >= 0; index--) {
                WrapHandler handler = handlers.get(index);
                Function<Map<String, Object>, Object> next = chain;
                chain = kwargs -> handler.execute(next, kwargs);
            }
            return chain;
        };
    }

    public static Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> createWrapDecorator(
            WrapHandler... handlers
    ) {
        return createWrapDecorator(Arrays.asList(handlers));
    }

    public static BoundArgs bindArgsNoDuplicate(Object[] args, Map<String, Object> kwargs, List<String> paramNames) {
        Object[] safeArgs = args != null ? args : new Object[0];
        Map<String, Object> safeKwargs = kwargs != null ? new HashMap<>(kwargs) : new HashMap<>();
        List<String> names = paramNames != null ? paramNames : Collections.emptyList();
        int positionalCount = Math.min(safeArgs.length, names.size());

        List<Object> keptArgs = new ArrayList<>();
        for (int index = 0; index < positionalCount; index++) {
            if (!safeKwargs.containsKey(names.get(index))) {
                keptArgs.add(safeArgs[index]);
            }
        }
        for (int index = positionalCount; index < safeArgs.length; index++) {
            keptArgs.add(safeArgs[index]);
        }
        return new BoundArgs(keptArgs.toArray(), safeKwargs);
    }

    @SuppressWarnings("unchecked")
    private static List<WrapHandler> getFrameworkWrapHandlers(DecoratorFramework framework, String event) {
        List<CallbackInfo> infos = framework.getCallbacks().getOrDefault(WRAP_EVENT_PREFIX + event, Collections.emptyList());
        List<WrapHandler> handlers = new ArrayList<>();
        for (CallbackInfo info : infos) {
            if (!info.isEnabled() || info.getCallback() == null) {
                continue;
            }
            Object value = info.getCallback().apply(Collections.emptyMap());
            if (value instanceof WrapHandler handler) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    private static Map<String, Object> mergedKwargs(Map<String, Object> kwargs, Map<String, Object> extraKwargs) {
        Map<String, Object> merged = new HashMap<>();
        if (kwargs != null) {
            merged.putAll(kwargs);
        }
        if (extraKwargs != null) {
            merged.putAll(extraKwargs);
        }
        return merged;
    }

    private static Object[] internalArgs(Map<String, Object> kwargs) {
        Object args = kwargs != null ? kwargs.get("_args") : null;
        return args instanceof Object[] values ? values : new Object[0];
    }

    /**
     * Mirrors Python's {@code _bind_args_no_duplicate()} result in
     * {@code openjiuwen/core/runner/callback/decorator.py}.
     */
    public static final class BoundArgs {

        private final Object[] args;
        private final Map<String, Object> kwargs;

        public BoundArgs(Object[] args, Map<String, Object> kwargs) {
            this.args = args != null ? args.clone() : new Object[0];
            this.kwargs = kwargs != null ? new HashMap<>(kwargs) : new HashMap<>();
        }

        public Object[] getArgs() {
            return args.clone();
        }

        public Map<String, Object> getKwargs() {
            return new HashMap<>(kwargs);
        }
    }
}
