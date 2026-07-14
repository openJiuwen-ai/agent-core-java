/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Mirrors Python's callback utility module in
 * {@code openjiuwen/core/runner/callback/utils.py}.
 */
public final class CallbackUtils {

    private static final Supplier<DecoratorFramework> DEFAULT_FRAMEWORK_SUPPLIER = () -> null;

    private static volatile Supplier<DecoratorFramework> frameworkSupplier = DEFAULT_FRAMEWORK_SUPPLIER;

    /**
     * Mirrors Python's {@code lazy_callback_framework} in
     * {@code openjiuwen/core/runner/callback/utils.py}.
     */
    public static final LazyFrameworkProxy lazyCallbackFramework = new LazyFrameworkProxy();

    private CallbackUtils() {
    }

    public static DecoratorFramework getCallbackFramework() {
        DecoratorFramework framework = frameworkSupplier.get();
        if (framework == null) {
            throw new IllegalStateException("Runner or callback_framework is not initialized.");
        }
        return framework;
    }

    public static void setFrameworkSupplier(Supplier<DecoratorFramework> supplier) {
        frameworkSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public static void setCallbackFramework(DecoratorFramework framework) {
        frameworkSupplier = () -> Objects.requireNonNull(framework, "framework");
    }

    public static void resetFrameworkSupplier() {
        frameworkSupplier = DEFAULT_FRAMEWORK_SUPPLIER;
    }

    public static void trigger(String event) {
        trigger(event, Collections.emptyMap());
    }

    public static void trigger(String event, Map<String, Object> kwargs) {
        DecoratorFramework framework = frameworkSupplier.get();
        if (framework != null) {
            framework.trigger(event, new Object[0], orderedKwargs(kwargs));
        }
    }

    private static Map<String, Object> orderedKwargs(Map<String, Object> kwargs) {
        return kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
    }

    /**
     * Mirrors Python's {@code _LazyFrameworkProxy} in
     * {@code openjiuwen/core/runner/callback/utils.py}.
     */
    public static final class LazyFrameworkProxy implements DecoratorFramework {

        private LazyFrameworkProxy() {
        }

        public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> emitBefore(
                String event
        ) {
            return emitBefore(event, true, null);
        }

        public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> emitBefore(
                String event,
                boolean passArgs,
                Map<String, Object> extraKwargs
        ) {
            return CallbackDecorators.createEmitBeforeDecorator(this, event, passArgs, orderedKwargs(extraKwargs));
        }

        public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> emitAfter(
                String event
        ) {
            return emitAfter(event, "result", "item", false, "per_item", null);
        }

        public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> emitAfter(
                String event,
                String resultKey,
                String itemKey,
                boolean passArgs,
                String streamMode,
                Map<String, Object> extraKwargs
        ) {
            return CallbackDecorators.createEmitAfterDecorator(
                    this,
                    event,
                    resultKey,
                    itemKey,
                    passArgs,
                    streamMode,
                    orderedKwargs(extraKwargs)
            );
        }

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
            return getCallbackFramework().registerSync(
                    event,
                    callback,
                    priority,
                    once,
                    namespace,
                    tags == null ? new HashSet<>() : tags,
                    filters == null ? Collections.emptyList() : filters,
                    rollbackHandler,
                    errorHandler,
                    maxRetries,
                    retryDelay,
                    timeout,
                    callbackType
            );
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
            getCallbackFramework().trigger(event, args == null ? new Object[0] : args.clone(), orderedKwargs(kwargs));
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            return getCallbackFramework().triggerTransform(
                    event,
                    args == null ? new Object[0] : args.clone(),
                    orderedKwargs(kwargs)
            );
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return getCallbackFramework().getCallbacks();
        }
    }
}
