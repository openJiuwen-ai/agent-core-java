/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compatibility facade for the 0.1.12 synchronous callback framework class.
 *
 * <p>Mirrors Python's {@code AsyncCallbackFramework} in
 * {@code openjiuwen/core/runner/callback/framework.py}.</p>
 */
public class CallbackFramework extends AsyncCallbackFramework {

    public static final String CALLBACK_TYPE_TRANSFORM = AsyncCallbackFramework.CALLBACK_TYPE_TRANSFORM;

    public CallbackFramework() {
        super();
    }

    public CallbackFramework(boolean enableMetrics, boolean enableLogging) {
        super(enableMetrics, enableLogging);
    }

    public CallbackInfo register(
            String event,
            Function<Map<String, Object>, Object> callback,
            int priority,
            String callbackName
    ) {
        return register(event, callback, priority, false, "default", null, null,
                null, null, 0, 0.0, null, callbackName, "");
    }

    public CallbackInfo register(
            String event,
            Function<Map<String, Object>, Object> callback,
            String callbackName
    ) {
        return register(event, callback, 0, callbackName);
    }

    public CallbackInfo register(
            String event,
            Function<Map<String, Object>, Object> callback,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            List<EventFilter> eventFilters,
            Function<Map<String, Object>, Object> rollbackHandler,
            Function<Map<String, Object>, Object> errorHandler,
            int maxRetries,
            double retryDelay,
            Double timeout,
            String callbackName
    ) {
        return register(event, callback, priority, once, namespace, tags, eventFilters,
                rollbackHandler, errorHandler, maxRetries, retryDelay, timeout, callbackName, "");
    }

    public CallbackInfo register(
            String event,
            Function<Map<String, Object>, Object> callback,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            List<EventFilter> eventFilters,
            Function<Map<String, Object>, Object> rollbackHandler,
            Function<Map<String, Object>, Object> errorHandler,
            int maxRetries,
            double retryDelay,
            Double timeout,
            String callbackName,
            String callbackType
    ) {
        CallbackInfo info = super.registerSync(
                event,
                callback,
                priority,
                once,
                namespace,
                tags,
                eventFilters,
                rollbackHandler,
                errorHandler,
                maxRetries,
                retryDelay,
                timeout,
                callbackType
        );
        info.setCallbackName(callbackName);
        return info;
    }

    public CallbackInfo registerSync(
            String event,
            Function<Map<String, Object>, Object> callback,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            List<EventFilter> eventFilters,
            Function<Map<String, Object>, Object> rollbackHandler,
            Function<Map<String, Object>, Object> errorHandler,
            int maxRetries,
            double retryDelay,
            Double timeout,
            String callbackName
    ) {
        return register(event, callback, priority, once, namespace, tags, eventFilters,
                rollbackHandler, errorHandler, maxRetries, retryDelay, timeout, callbackName, "");
    }

    public CallbackInfo on(String event, Function<Map<String, Object>, Object> callback, String callbackName) {
        return register(event, callback, 0, callbackName);
    }

    public CallbackInfo on(
            String event,
            Function<Map<String, Object>, Object> callback,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            List<EventFilter> eventFilters,
            Consumer<ChainContext> rollbackHandler,
            Function<Object, Object> errorHandler,
            int maxRetries,
            double retryDelay,
            Double timeout,
            String callbackName
    ) {
        return register(event, callback, priority, once, namespace, tags, eventFilters,
                adaptRollback(rollbackHandler), adaptError(errorHandler), maxRetries, retryDelay, timeout,
                callbackName, "");
    }

    public List<Object> trigger(String event) {
        return triggerResults(event);
    }

    public List<Object> trigger(String event, Map<String, Object> kwargs) {
        return triggerResults(event, kwargs);
    }

    public ScheduledFuture<List<Object>> triggerDelayed(
            String event,
            double delaySeconds,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        return super.triggerDelayed(event, delaySeconds, args, kwargs);
    }

    public Iterator<Object> triggerStream(String event, Iterator<?> inputStream, Object[] args,
                                          Map<String, Object> kwargs) {
        return super.triggerStream(event, inputStream, args, kwargs);
    }

    public Function<Map<String, Object>, Object> emitAround(
            String beforeEvent,
            String afterEvent,
            Function<Map<String, Object>, Object> wrapped,
            boolean passArgs,
            boolean passResult,
            String onErrorEvent
    ) {
        return super.emitAround(beforeEvent, afterEvent, passArgs, passResult, onErrorEvent).apply(wrapped);
    }

    public Function<Map<String, Object>, Object> triggerOnCall(
            String event,
            Function<Map<String, Object>, Object> wrapped,
            boolean passArgs,
            boolean passResult
    ) {
        return emitBefore(event, passArgs, null).apply(wrapped);
    }

    public CallbackInfo onTransform(
            String event,
            Function<Map<String, Object>, Object> callback,
            int priority,
            String callbackName
    ) {
        return register(event, callback, priority, false, "default", null, null,
                null, null, 0, 0.0, null, callbackName, CALLBACK_TYPE_TRANSFORM);
    }

    public Function<Map<String, Object>, Object> transformIoByEvents(
            Function<Map<String, Object>, Object> wrapped,
            String inputEvent,
            String outputEvent,
            String resultKey
    ) {
        return transformIoByEvents(inputEvent, outputEvent, resultKey).apply(wrapped);
    }

    public Function<Map<String, Object>, Object> transformIo(
            Function<Map<String, Object>, Object> wrapped,
            Function<Map<String, Object>, Map<String, Object>> inputTransform,
            Function<Object, Object> outputTransform
    ) {
        return transformIo(inputTransform, outputTransform).apply(wrapped);
    }

    public Function<Map<String, Object>, Object> transform_io(
            Function<Map<String, Object>, Object> wrapped,
            Function<Map<String, Object>, Map<String, Object>> inputTransform,
            Function<Object, Object> outputTransform
    ) {
        return transformIo(inputTransform, outputTransform).apply(wrapped);
    }

    public Function<Map<String, Object>, Object> transform_io(
            Function<Map<String, Object>, Object> wrapped,
            Function<Map<String, Object>, Map<String, Object>> inputTransform,
            Function<Object, Object> outputTransform,
            String outputMode
    ) {
        return transformIo(inputTransform, outputTransform, outputMode).apply(wrapped);
    }

    public Function<Map<String, Object>, Object> transform_io(
            Function<Map<String, Object>, Object> wrapped,
            String inputEvent,
            String outputEvent,
            String resultKey,
            Function<Map<String, Object>, Map<String, Object>> inputTransform,
            Function<Object, Object> outputTransform,
            String outputMode
    ) {
        return transformIo(inputEvent, outputEvent, resultKey, inputTransform, outputTransform, outputMode)
                .apply(wrapped);
    }

    public Function<Map<String, Object>, Object> emit_after(
            String event,
            Function<Map<String, Object>, Object> wrapped,
            String resultKey,
            boolean passArgs
    ) {
        return emits(event, wrapped, resultKey, passArgs);
    }

    public Function<Map<String, Object>, Object> emits(
            String event,
            Function<Map<String, Object>, Object> wrapped,
            String resultKey,
            boolean passArgs
    ) {
        return emitAfter(event, resultKey, null, passArgs, "frame", null).apply(wrapped);
    }

    private static Function<Map<String, Object>, Object> adaptRollback(Consumer<ChainContext> rollbackHandler) {
        if (rollbackHandler == null) {
            return null;
        }
        return kwargs -> {
            Object context = kwargs == null ? null : kwargs.get("_chain_context");
            if (context instanceof ChainContext chainContext) {
                rollbackHandler.accept(chainContext);
            }
            return null;
        };
    }

    private static Function<Map<String, Object>, Object> adaptError(Function<Object, Object> errorHandler) {
        if (errorHandler == null) {
            return null;
        }
        return kwargs -> errorHandler.apply(kwargs);
    }
}
