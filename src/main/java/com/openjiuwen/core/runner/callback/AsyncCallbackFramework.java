/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production-ready callback framework.
 *
 * <p>Mirrors Python's {@code AsyncCallbackFramework} in
 * {@code openjiuwen/core/runner/callback/framework.py}.</p>
 */
public class AsyncCallbackFramework implements DecoratorFramework {

    public static final String CALLBACK_TYPE_TRANSFORM = "transform";

    public static final Object TRANSFORM_NOOP = CallbackDecorators.TRANSFORM_NOOP;

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(AsyncCallbackFramework.class);

    private static final int MAX_HISTORY_SIZE = 1000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, List<CallbackInfo>> callbacks = new ConcurrentHashMap<>();

    private final Map<String, CallbackChain> chains = new ConcurrentHashMap<>();

    private final Map<String, List<EventFilter>> filters = new ConcurrentHashMap<>();

    private final List<EventFilter> globalFilters = Collections.synchronizedList(new ArrayList<>());

    private final Map<Function<Map<String, Object>, Object>, List<EventFilter>> callbackFilters =
            new ConcurrentHashMap<>();

    private final Map<String, Map<HookType, List<Consumer<Map<String, Object>>>>> hooks =
            new ConcurrentHashMap<>();

    private final boolean enableMetrics;

    private final Map<String, CallbackMetrics> metrics = new ConcurrentHashMap<>();

    private final boolean enableLogging;

    private final Logger logger;

    private final Map<String, CircuitBreakerFilter> circuitBreakers = new ConcurrentHashMap<>();

    private final Deque<Map<String, Object>> eventHistory = new ArrayDeque<>(MAX_HISTORY_SIZE);

    private volatile boolean enableHistory;

    public AsyncCallbackFramework() {
        this(true, true);
    }

    public AsyncCallbackFramework(boolean enableMetrics, boolean enableLogging) {
        this(enableMetrics, enableLogging, DEFAULT_LOGGER);
    }

    AsyncCallbackFramework(boolean enableMetrics, boolean enableLogging, Logger logger) {
        this.enableMetrics = enableMetrics;
        this.enableLogging = enableLogging;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Map<String, List<CallbackInfo>> getCallbacks() {
        return callbacks;
    }

    public Map<String, CallbackChain> getChains() {
        return chains;
    }

    public Map<String, CircuitBreakerFilter> getCircuitBreakers() {
        return circuitBreakers;
    }

    public Map<Function<Map<String, Object>, Object>, List<EventFilter>> getCallbackFilters() {
        return callbackFilters;
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> on(String event) {
        return on(event, 0, false, "default", null, null, 0, 0.0, null, "");
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> on(
            String event,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            List<EventFilter> eventFilters,
            int maxRetries,
            double retryDelay,
            Double timeout,
            String callbackType
    ) {
        return CallbackDecorators.createOnDecorator(
                this,
                event,
                priority,
                once,
                namespace,
                tags,
                eventFilters,
                null,
                null,
                maxRetries,
                retryDelay,
                timeout,
                callbackType
        );
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> onChain(
            String event,
            int priority,
            boolean once,
            String namespace,
            Set<String> tags,
            Function<Map<String, Object>, Object> rollbackHandler,
            Function<Map<String, Object>, Object> errorHandler,
            int maxRetries,
            double retryDelay,
            Double timeout,
            String callbackType
    ) {
        return CallbackDecorators.createOnDecorator(
                this,
                event,
                priority,
                once,
                namespace,
                tags,
                null,
                rollbackHandler,
                errorHandler,
                maxRetries,
                retryDelay,
                timeout,
                callbackType
        );
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> emitBefore(
            String event,
            boolean passArgs,
            Map<String, Object> extraKwargs
    ) {
        return CallbackDecorators.createEmitBeforeDecorator(this, event, passArgs, safeKwargs(extraKwargs));
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
                safeKwargs(extraKwargs)
        );
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> emitAround(
            String beforeEvent,
            String afterEvent,
            boolean passArgs,
            boolean passResult,
            String onErrorEvent
    ) {
        return CallbackDecorators.createEmitAroundDecorator(
                this,
                beforeEvent,
                afterEvent,
                passArgs,
                passResult,
                onErrorEvent
        );
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> transformIo(
            Function<Map<String, Object>, Map<String, Object>> inputTransform,
            Function<Object, Object> outputTransform
    ) {
        return CallbackDecorators.createTransformIoDecorator(inputTransform, outputTransform);
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> transformIo(
            Function<Map<String, Object>, Map<String, Object>> inputTransform,
            Function<Object, Object> outputTransform,
            String outputMode
    ) {
        return CallbackDecorators.createTransformIoDecorator(inputTransform, outputTransform, outputMode);
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> transformIo(
            String inputEvent,
            String outputEvent,
            String resultKey,
            Function<Map<String, Object>, Map<String, Object>> inputTransform,
            Function<Object, Object> outputTransform,
            String outputMode
    ) {
        if (inputEvent != null || outputEvent != null) {
            return transformIoByEvents(inputEvent, outputEvent, resultKey, outputMode);
        }
        return transformIo(inputTransform, outputTransform, outputMode);
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> transformIoByEvents(
            String inputEvent,
            String outputEvent,
            String resultKey
    ) {
        return CallbackDecorators.createTransformIoByEventsDecorator(this, inputEvent, outputEvent, resultKey);
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> transformIoByEvents(
            String inputEvent,
            String outputEvent,
            String resultKey,
            String outputMode
    ) {
        return CallbackDecorators.createTransformIoByEventsDecorator(this, inputEvent, outputEvent, resultKey,
                outputMode);
    }

    public Function<WrapHandler, WrapHandler> onWrap(String event, int priority) {
        return CallbackDecorators.createOnWrapDecorator(this, event, priority);
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> wrap(String event) {
        return CallbackDecorators.createWrapByEventDecorator(this, event);
    }

    public Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> onTransform(
            String event,
            int priority
    ) {
        return on(event, priority, false, "default", null, null, 0, 0.0, null, CALLBACK_TYPE_TRANSFORM);
    }

    @Override
    public synchronized CallbackInfo registerSync(
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
            String callbackType
    ) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(callback, "callback");

        CallbackInfo callbackInfo = CallbackInfo.builder()
                .callback(callback)
                .priority(priority)
                .once(once)
                .namespace(namespace != null ? namespace : "default")
                .tags(tags != null ? new HashSet<>(tags) : new HashSet<>())
                .maxRetries(maxRetries)
                .retryDelay(retryDelay)
                .timeout(timeout)
                .callbackType(callbackType != null ? callbackType : "")
                .build();

        callbacks.computeIfAbsent(event, ignored -> Collections.synchronizedList(new ArrayList<>())).add(callbackInfo);
        sortCallbacks(event);

        if (eventFilters != null && !eventFilters.isEmpty()) {
            callbackFilters.put(callback, new ArrayList<>(eventFilters));
        }

        CallbackChain existingChain = chains.get(event);
        if (rollbackHandler != null || errorHandler != null) {
            CallbackChain chain = existingChain;
            if (chain == null) {
                chain = new CallbackChain(event);
                chains.put(event, chain);
                for (CallbackInfo existingCallbackInfo : snapshot(callbacks.get(event))) {
                    if (existingCallbackInfo != callbackInfo) {
                        chain.add(existingCallbackInfo);
                    }
                }
            }
            chain.add(callbackInfo, adaptRollbackHandler(rollbackHandler), adaptErrorHandler(errorHandler));
        } else if (existingChain != null) {
            existingChain.add(callbackInfo);
        }

        if (enableLogging) {
            logger.info("Registered callback: {} -> {}", event, callbackName(callback));
        }
        return callbackInfo;
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
            String callbackType
    ) {
        return registerSync(
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
    }

    public void unregisterSync(String event, Function<Map<String, Object>, Object> callback) {
        if (event == null || callback == null || !callbacks.containsKey(event)) {
            return;
        }

        Function<Map<String, Object>, Object> callbackToRemove = null;
        for (CallbackInfo callbackInfo : snapshot(callbacks.get(event))) {
            if (callbackInfo.getCallback() == callback || callbackInfo.getWrapper() == callback) {
                callbackToRemove = callbackInfo.getCallback();
                break;
            }
        }

        if (callbackToRemove == null) {
            return;
        }

        Function<Map<String, Object>, Object> removeTarget = callbackToRemove;
        callbacks.get(event).removeIf(info -> info.getCallback() == removeTarget);
        callbackFilters.remove(removeTarget);
        CallbackChain chain = chains.get(event);
        if (chain != null) {
            chain.remove(removeTarget);
        }
        circuitBreakers.remove(circuitBreakerKey(event, removeTarget));

        if (enableLogging) {
            logger.info("Unregistered callback: {} -> {}", event, callbackName(removeTarget));
        }
    }

    public void unregister(String event, Function<Map<String, Object>, Object> callback) {
        unregisterSync(event, callback);
    }

    public void unregisterNamespace(String namespace) {
        for (String event : new ArrayList<>(callbacks.keySet())) {
            List<CallbackInfo> removed = removeCallbacks(event, info -> Objects.equals(info.getNamespace(), namespace));
            cleanupRemoved(event, removed);
        }
    }

    public void unregisterByTags(Set<String> tags) {
        Set<String> safeTags = tags != null ? tags : Collections.emptySet();
        for (String event : new ArrayList<>(callbacks.keySet())) {
            List<CallbackInfo> removed = removeCallbacks(
                    event,
                    info -> !Collections.disjoint(info.getTags(), safeTags)
            );
            cleanupRemoved(event, removed);
        }
    }

    public void unregisterEvent(String event) {
        List<CallbackInfo> removed = callbacks.remove(event);
        if (removed != null) {
            cleanupRemoved(event, removed);
        }
        chains.remove(event);
        hooks.remove(event);
        filters.remove(event);
        if (enableLogging) {
            logger.info("Unregistered all callbacks for event: {}", event);
        }
    }

    @Override
    public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
        triggerResults(event, args, kwargs);
    }

    public List<Object> triggerResults(String event) {
        return triggerResults(event, new Object[0], Collections.emptyMap());
    }

    public List<Object> triggerResults(String event, Map<String, Object> kwargs) {
        return triggerResults(event, new Object[0], kwargs);
    }

    public List<Object> triggerResults(String event, Object[] args, Map<String, Object> kwargs) {
        Object[] safeArgs = safeArgs(args);
        Map<String, Object> safeKwargs = safeKwargs(kwargs);
        List<Object> results = new ArrayList<>();

        if (enableHistory) {
            recordHistory(event, safeArgs, safeKwargs);
        }

        executeHooks(event, HookType.BEFORE, safeArgs, safeKwargs);

        for (CallbackInfo callbackInfo : snapshot(callbacks.get(event))) {
            if (!callbackInfo.isEnabled() || isTransformCallback(callbackInfo)) {
                continue;
            }

            Function<Map<String, Object>, Object> callback = callbackInfo.getCallback();
            long startNanos = System.nanoTime();
            try {
                FilterResult filterResult = applyFilters(event, callback, safeArgs, safeKwargs);
                if (filterResult.getAction() == FilterAction.STOP) {
                    if (enableLogging) {
                        logger.info("Filter stopped event processing: {}", event);
                    }
                    break;
                }
                if (filterResult.getAction() == FilterAction.SKIP) {
                    if (enableLogging) {
                        logger.debug("Filter skipped callback {}: {}", callbackName(callback), filterResult.getReason());
                    }
                    continue;
                }

                Object[] finalArgs = filterResult.getModifiedArgs() != null
                        ? filterResult.getModifiedArgs() : safeArgs;
                Map<String, Object> finalKwargs = filterResult.getModifiedKwargs() != null
                        ? filterResult.getModifiedKwargs() : safeKwargs;
                Object result = invokeCallback(callback, finalArgs, finalKwargs, callbackInfo.getTimeout());

                updateMetrics(event, callback, startNanos, false);
                CircuitBreakerFilter breaker = circuitBreakers.get(circuitBreakerKey(event, callback));
                if (breaker != null) {
                    breaker.recordSuccess(event, callback);
                }

                results.add(result);
                if (callbackInfo.isOnce()) {
                    callbackInfo.setEnabled(false);
                }
            } catch (AbortError abortError) {
                updateMetrics(event, callback, startNanos, true);
                recordCircuitFailure(event, callback);
                executeHooks(event, HookType.ERROR, safeArgs, mergeError(safeKwargs, abortError));
                if (enableLogging) {
                    logger.error("Callback execution aborted: {} - {}", callbackName(callback), abortError.getReason());
                }
                Throwable cause = abortError.getCause();
                if (cause != null) {
                    throw runtime(cause);
                }
                throw abortError;
            } catch (Exception error) {
                updateMetrics(event, callback, startNanos, true);
                recordCircuitFailure(event, callback);
                executeHooks(event, HookType.ERROR, safeArgs, mergeError(safeKwargs, error));
                if (enableLogging) {
                    logger.error("Callback execution failed: {} - {}", callbackName(callback), error.getMessage(), error);
                }
            }
        }

        executeHooks(event, HookType.AFTER, prepend(results, safeArgs), safeKwargs);
        return results;
    }

    @Override
    public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
        Object result = TRANSFORM_NOOP;
        for (CallbackInfo callbackInfo : snapshot(callbacks.get(event))) {
            if (!callbackInfo.isEnabled() || !isTransformCallback(callbackInfo)) {
                continue;
            }
            result = invokeCallbackUnchecked(callbackInfo.getCallback(), safeArgs(args), safeKwargs(kwargs),
                    callbackInfo.getTimeout());
            if (callbackInfo.isOnce()) {
                callbackInfo.setEnabled(false);
            }
        }
        return result;
    }

    public CompletableFuture<CallbackInfo> registerAsync(
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
            String callbackType
    ) {
        return CompletableFuture.completedFuture(register(
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
        ));
    }

    public CompletableFuture<List<Object>> triggerAsync(String event, Object[] args, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> triggerResults(event, args, kwargs));
    }

    public CompletableFuture<Void> unregisterAsync(String event, Function<Map<String, Object>, Object> callback) {
        unregister(event, callback);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> unregisterNamespaceAsync(String namespace) {
        unregisterNamespace(namespace);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> unregisterByTagsAsync(Set<String> tags) {
        unregisterByTags(tags);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> unregisterEventAsync(String event) {
        unregisterEvent(event);
        return CompletableFuture.completedFuture(null);
    }

    public ScheduledFuture<List<Object>> triggerDelayed(
            String event,
            double delaySeconds,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        return scheduler.schedule(() -> {
            try {
                return triggerResults(event, args, kwargs);
            } finally {
                scheduler.shutdown();
            }
        }, Math.max(0L, Math.round(delaySeconds * 1000L)), TimeUnit.MILLISECONDS);
    }

    public ChainResult triggerChain(String event, Object[] args, Map<String, Object> kwargs) {
        CallbackChain chain = chains.get(event);
        if (chain == null) {
            chain = new CallbackChain(event);
            for (CallbackInfo callbackInfo : snapshot(callbacks.get(event))) {
                chain.add(callbackInfo);
            }
        }
        ChainContext context = new ChainContext(event, safeArgs(args), safeKwargs(kwargs));
        return chain.execute(context).join();
    }

    public List<Object> triggerParallel(String event, Object[] args, Map<String, Object> kwargs) {
        List<CallbackInfo> callbackInfos = snapshot(callbacks.get(event));
        if (callbackInfos.isEmpty()) {
            return Collections.emptyList();
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, callbackInfos.size()));
        List<Future<Object>> futures = new ArrayList<>();
        for (CallbackInfo callbackInfo : callbackInfos) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }
            futures.add(executor.submit(() -> executeParallelCallback(event, callbackInfo, args, kwargs)));
        }

        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                Object value = future.get();
                if (value != null) {
                    results.add(value);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException error) {
                if (enableLogging) {
                    logger.error("Parallel execution exception: {}", error.getMessage(), error);
                }
            }
        }
        executor.shutdown();
        return results;
    }

    public Object triggerUntil(
            String event,
            Predicate<Object> condition,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        Objects.requireNonNull(condition, "condition");
        Object[] safeArgs = safeArgs(args);
        Map<String, Object> safeKwargs = safeKwargs(kwargs);
        for (CallbackInfo callbackInfo : snapshot(callbacks.get(event))) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }

            Function<Map<String, Object>, Object> callback = callbackInfo.getCallback();
            try {
                FilterResult filterResult = applyFilters(event, callback, safeArgs, safeKwargs);
                if (filterResult.getAction() == FilterAction.STOP) {
                    break;
                }
                if (filterResult.getAction() == FilterAction.SKIP) {
                    continue;
                }

                Object[] finalArgs = filterResult.getModifiedArgs() != null
                        ? filterResult.getModifiedArgs() : safeArgs;
                Map<String, Object> finalKwargs = filterResult.getModifiedKwargs() != null
                        ? filterResult.getModifiedKwargs() : safeKwargs;
                Object result = invokeCallback(callback, finalArgs, finalKwargs, callbackInfo.getTimeout());
                if (condition.test(result)) {
                    if (enableLogging) {
                        logger.info("Condition satisfied by {}: {}", callbackName(callback), result);
                    }
                    if (callbackInfo.isOnce()) {
                        callbackInfo.setEnabled(false);
                    }
                    return result;
                }
                if (callbackInfo.isOnce()) {
                    callbackInfo.setEnabled(false);
                }
            } catch (Exception error) {
                if (enableLogging) {
                    logger.error("Callback {} failed in triggerUntil: {}", callbackName(callback),
                            error.getMessage(), error);
                }
            }
        }
        return null;
    }

    public List<Object> triggerWithTimeout(
            String event,
            double timeoutSeconds,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<Object>> future = executor.submit(() -> triggerResults(event, args, kwargs));
        try {
            return future.get(Math.max(0L, Math.round(timeoutSeconds * 1000L)), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        } catch (ExecutionException | TimeoutException error) {
            future.cancel(true);
            if (enableLogging) {
                logger.warn("Event '{}' execution timeout after {}s", event, timeoutSeconds);
            }
            return Collections.emptyList();
        } finally {
            executor.shutdownNow();
        }
    }

    public Iterator<Object> triggerStream(
            String event,
            Iterator<?> inputStream,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        List<Object> output = new ArrayList<>();
        if (inputStream == null) {
            return output.iterator();
        }
        try {
            while (inputStream.hasNext()) {
                Object item = inputStream.next();
                Object[] finalArgs = prepend(item, safeArgs(args));
                for (Object result : triggerResults(event, finalArgs, kwargs)) {
                    appendFlattened(output, result);
                }
            }
            return output.iterator();
        } catch (RuntimeException error) {
            if (enableLogging) {
                logger.error("Stream processing error: {}", error.getMessage(), error);
            }
            throw error;
        }
    }

    public Iterator<Object> triggerGenerator(String event, Object[] args, Map<String, Object> kwargs) {
        Object[] safeArgs = safeArgs(args);
        Map<String, Object> safeKwargs = safeKwargs(kwargs);
        List<Object> output = new ArrayList<>();
        executeHooks(event, HookType.BEFORE, safeArgs, safeKwargs);
        try {
            for (CallbackInfo callbackInfo : snapshot(callbacks.get(event))) {
                if (!callbackInfo.isEnabled()) {
                    continue;
                }
                Function<Map<String, Object>, Object> callback = callbackInfo.getCallback();
                long startNanos = System.nanoTime();
                try {
                    FilterResult filterResult = applyFilters(event, callback, safeArgs, safeKwargs);
                    if (filterResult.getAction() == FilterAction.STOP) {
                        if (enableLogging) {
                            logger.info("Filter stopped processing for {}", event);
                        }
                        break;
                    }
                    if (filterResult.getAction() == FilterAction.SKIP) {
                        if (enableLogging) {
                            logger.debug("Filter skipped callback {}: {}", callbackName(callback),
                                    filterResult.getReason());
                        }
                        continue;
                    }
                    Object[] finalArgs = filterResult.getModifiedArgs() != null
                            ? filterResult.getModifiedArgs() : safeArgs;
                    Map<String, Object> finalKwargs = filterResult.getModifiedKwargs() != null
                            ? filterResult.getModifiedKwargs() : safeKwargs;
                    Object result = invokeCallback(callback, finalArgs, finalKwargs, callbackInfo.getTimeout());
                    appendFlattened(output, result);
                    updateMetrics(event, callback, startNanos, false);
                    if (callbackInfo.isOnce()) {
                        callbackInfo.setEnabled(false);
                    }
                } catch (Exception error) {
                    updateMetrics(event, callback, startNanos, true);
                    if (enableLogging) {
                        logger.error("Callback {} failed in generator mode: {}", callbackName(callback),
                                error.getMessage(), error);
                    }
                    executeHooks(event, HookType.ERROR, safeArgs, mergeError(safeKwargs, error));
                }
            }
            executeHooks(event, HookType.AFTER, safeArgs, safeKwargs);
            return cleanupIterator(output.iterator(), event, safeArgs, safeKwargs);
        } catch (RuntimeException error) {
            executeHooks(event, HookType.CLEANUP, safeArgs, safeKwargs);
            throw error;
        }
    }

    public void addFilter(String event, EventFilter filter) {
        filters.computeIfAbsent(event, ignored -> Collections.synchronizedList(new ArrayList<>())).add(filter);
    }

    public void addGlobalFilter(EventFilter filter) {
        globalFilters.add(filter);
    }

    public void addCircuitBreaker(
            String event,
            Function<Map<String, Object>, Object> callback,
            int failureThreshold,
            double timeout
    ) {
        CircuitBreakerFilter breaker = new CircuitBreakerFilter(failureThreshold, timeout);
        circuitBreakers.put(circuitBreakerKey(event, callback), breaker);
        addFilter(event, breaker);
    }

    public void addHook(String event, HookType hookType, Consumer<Map<String, Object>> hook) {
        hooks.computeIfAbsent(event, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(hookType, ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(hook);
    }

    public Map<String, Map<String, Object>> getMetrics(String event, String callback) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, CallbackMetrics> entry : metrics.entrySet()) {
            String key = entry.getKey();
            int separator = key.indexOf(':');
            String eventName = separator >= 0 ? key.substring(0, separator) : key;
            String callbackName = separator >= 0 ? key.substring(separator + 1) : "";
            if (event != null && !event.equals(eventName)) {
                continue;
            }
            if (callback != null && !callback.equals(callbackName)) {
                continue;
            }
            result.put(key, entry.getValue().toMap());
        }
        return result;
    }

    public Map<String, Map<String, Object>> getMetrics() {
        return getMetrics(null, null);
    }

    public void resetMetrics() {
        metrics.clear();
    }

    public List<Map<String, Object>> getSlowCallbacks(double threshold) {
        List<Map<String, Object>> slowCallbacks = new ArrayList<>();
        for (Map.Entry<String, CallbackMetrics> entry : metrics.entrySet()) {
            CallbackMetrics callbackMetrics = entry.getValue();
            if (callbackMetrics.getAvgTime() <= threshold) {
                continue;
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("callback", entry.getKey());
            info.put("avg_time", callbackMetrics.getAvgTime());
            info.put("max_time", callbackMetrics.getMaxTime());
            info.put("call_count", callbackMetrics.getCallCount());
            slowCallbacks.add(info);
        }
        slowCallbacks.sort((left, right) -> Double.compare(
                ((Number) right.get("avg_time")).doubleValue(),
                ((Number) left.get("avg_time")).doubleValue()
        ));
        return slowCallbacks;
    }

    public void enableEventHistory(boolean enabled) {
        enableHistory = enabled;
    }

    public List<Map<String, Object>> getEventHistory(String event, Double since) {
        List<Map<String, Object>> history;
        synchronized (eventHistory) {
            history = new ArrayList<>(eventHistory);
        }
        if (event != null) {
            history.removeIf(record -> !event.equals(record.get("event")));
        }
        if (since != null) {
            history.removeIf(record -> ((Number) record.getOrDefault("timestamp", 0.0)).doubleValue() < since);
        }
        return history;
    }

    public void replayEvents(Double since) {
        for (Map<String, Object> record : getEventHistory(null, since)) {
            Object[] args = record.get("args") instanceof Object[] values ? values : new Object[0];
            @SuppressWarnings("unchecked")
            Map<String, Object> kwargs = record.get("kwargs") instanceof Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
            triggerResults(String.valueOf(record.get("event")), args, kwargs);
        }
    }

    public void saveState(String filepath) {
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> callbackState = new LinkedHashMap<>();
        for (Map.Entry<String, List<CallbackInfo>> entry : callbacks.entrySet()) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (CallbackInfo callbackInfo : snapshot(entry.getValue())) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", callbackName(callbackInfo.getCallback()));
                info.put("priority", callbackInfo.getPriority());
                info.put("namespace", callbackInfo.getNamespace());
                info.put("tags", new ArrayList<>(callbackInfo.getTags()));
                info.put("enabled", callbackInfo.isEnabled());
                entries.add(info);
            }
            callbackState.put(entry.getKey(), entries);
        }
        state.put("callbacks", callbackState);
        state.put("metrics", getMetrics());
        state.put("history", getEventHistory(null, null));

        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(filepath), state);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public List<String> listEvents(String namespace) {
        List<String> events = new ArrayList<>();
        for (Map.Entry<String, List<CallbackInfo>> entry : callbacks.entrySet()) {
            if (namespace == null || snapshot(entry.getValue()).stream()
                    .anyMatch(callbackInfo -> namespace.equals(callbackInfo.getNamespace()))) {
                events.add(entry.getKey());
            }
        }
        return events;
    }

    public List<Map<String, Object>> listCallbacks(String event) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CallbackInfo callbackInfo : snapshot(callbacks.get(event))) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", callbackName(callbackInfo.getCallback()));
            info.put("priority", callbackInfo.getPriority());
            info.put("enabled", callbackInfo.isEnabled());
            info.put("namespace", callbackInfo.getNamespace());
            info.put("tags", new ArrayList<>(callbackInfo.getTags()));
            info.put("once", callbackInfo.isOnce());
            info.put("max_retries", callbackInfo.getMaxRetries());
            info.put("timeout", callbackInfo.getTimeout());
            result.add(info);
        }
        return result;
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        int totalCallbacks = callbacks.values().stream().mapToInt(List::size).sum();
        Set<String> namespaces = new HashSet<>();
        for (List<CallbackInfo> callbackList : callbacks.values()) {
            for (CallbackInfo callbackInfo : snapshot(callbackList)) {
                namespaces.add(callbackInfo.getNamespace());
            }
        }
        int eventFilterCount = filters.values().stream().mapToInt(List::size).sum();
        result.put("total_events", callbacks.size());
        result.put("total_callbacks", totalCallbacks);
        result.put("namespaces", new ArrayList<>(namespaces));
        result.put("total_filters", globalFilters.size() + eventFilterCount);
        result.put("total_chains", chains.size());
        result.put("history_size", getEventHistory(null, null).size());
        result.put("metrics_collected", metrics.size());
        return result;
    }

    private Object executeParallelCallback(
            String event,
            CallbackInfo callbackInfo,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        Function<Map<String, Object>, Object> callback = callbackInfo.getCallback();
        try {
            FilterResult filterResult = applyFilters(event, callback, safeArgs(args), safeKwargs(kwargs));
            if (filterResult.getAction() == FilterAction.STOP) {
                if (enableLogging) {
                    logger.info("Filter stopped processing for {}", event);
                }
                return null;
            }
            if (filterResult.getAction() == FilterAction.SKIP) {
                if (enableLogging) {
                    logger.debug("Filter skipped {}: {}", callbackName(callback), filterResult.getReason());
                }
                return null;
            }
            Object[] finalArgs = filterResult.getModifiedArgs() != null
                    ? filterResult.getModifiedArgs() : safeArgs(args);
            Map<String, Object> finalKwargs = filterResult.getModifiedKwargs() != null
                    ? filterResult.getModifiedKwargs() : safeKwargs(kwargs);
            Object result = invokeCallback(callback, finalArgs, finalKwargs, callbackInfo.getTimeout());
            if (callbackInfo.isOnce()) {
                callbackInfo.setEnabled(false);
            }
            return result;
        } catch (Exception error) {
            if (enableLogging) {
                logger.error("Callback {} failed in parallel execution: {}", callbackName(callback),
                        error.getMessage(), error);
            }
            return null;
        }
    }

    private FilterResult applyFilters(
            String event,
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        Object[] currentArgs = safeArgs(args);
        Map<String, Object> currentKwargs = safeKwargs(kwargs);
        List<EventFilter> allFilters = new ArrayList<>();
        synchronized (globalFilters) {
            allFilters.addAll(globalFilters);
        }
        allFilters.addAll(snapshot(filters.get(event)));
        allFilters.addAll(snapshot(callbackFilters.get(callback)));

        for (EventFilter filter : allFilters) {
            FilterResult result = filter.filter(event, callback, currentArgs, currentKwargs);
            if (result.getAction() == FilterAction.STOP || result.getAction() == FilterAction.SKIP) {
                return result;
            }
            if (result.getAction() == FilterAction.MODIFY) {
                if (result.getModifiedArgs() != null) {
                    currentArgs = result.getModifiedArgs();
                }
                if (result.getModifiedKwargs() != null) {
                    currentKwargs = result.getModifiedKwargs();
                }
            }
        }
        return FilterResult.continueResult(currentArgs, currentKwargs);
    }

    private void executeHooks(
            String event,
            HookType hookType,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        Map<HookType, List<Consumer<Map<String, Object>>>> eventHooks = hooks.get(event);
        if (eventHooks == null) {
            return;
        }
        for (Consumer<Map<String, Object>> hook : snapshot(eventHooks.get(hookType))) {
            try {
                Map<String, Object> hookKwargs = safeKwargs(kwargs);
                hookKwargs.put("_event", event);
                hookKwargs.put("_hook_type", hookType);
                hookKwargs.put("_args", safeArgs(args));
                hook.accept(hookKwargs);
            } catch (Exception error) {
                logger.error("Hook execution failed: {}", error.getMessage(), error);
            }
        }
    }

    private Object invokeCallback(
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs,
            Double timeout
    ) throws Exception {
        Map<String, Object> callKwargs = safeKwargs(kwargs);
        callKwargs.putIfAbsent("session", null);
        callKwargs.put("_args", safeArgs(args));
        Object rawResult = callback.apply(callKwargs);
        if (!(rawResult instanceof CompletionStage<?> stage)) {
            return rawResult;
        }
        CompletableFuture<?> future = stage.toCompletableFuture();
        try {
            if (timeout != null && timeout > 0) {
                return future.get(Math.round(timeout * 1000L), TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Callback execution interrupted", interrupted);
        } catch (ExecutionException | CompletionException error) {
            throw normalize(error);
        }
    }

    private Object invokeCallbackUnchecked(
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs,
            Double timeout
    ) {
        try {
            return invokeCallback(callback, args, kwargs, timeout);
        } catch (Exception error) {
            throw runtime(error);
        }
    }

    private void updateMetrics(
            String event,
            Function<Map<String, Object>, Object> callback,
            long startNanos,
            boolean error
    ) {
        if (!enableMetrics) {
            return;
        }
        double executionTime = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        metrics.computeIfAbsent(event + ":" + callbackName(callback), ignored -> new CallbackMetrics())
                .update(executionTime, error);
    }

    private void recordCircuitFailure(String event, Function<Map<String, Object>, Object> callback) {
        CircuitBreakerFilter breaker = circuitBreakers.get(circuitBreakerKey(event, callback));
        if (breaker != null) {
            breaker.recordFailure(event, callback);
        }
    }

    private void recordHistory(String event, Object[] args, Map<String, Object> kwargs) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("event", event);
        record.put("args", safeArgs(args));
        record.put("kwargs", safeKwargs(kwargs));
        record.put("timestamp", System.currentTimeMillis() / 1000.0);
        synchronized (eventHistory) {
            while (eventHistory.size() >= MAX_HISTORY_SIZE) {
                eventHistory.removeFirst();
            }
            eventHistory.addLast(record);
        }
    }

    private void sortCallbacks(String event) {
        List<CallbackInfo> callbackList = callbacks.get(event);
        if (callbackList != null) {
            callbackList.sort((left, right) -> Integer.compare(right.getPriority(), left.getPriority()));
        }
    }

    private List<CallbackInfo> removeCallbacks(String event, Predicate<CallbackInfo> predicate) {
        List<CallbackInfo> callbackList = callbacks.get(event);
        if (callbackList == null) {
            return Collections.emptyList();
        }
        List<CallbackInfo> removed = new ArrayList<>();
        callbackList.removeIf(info -> {
            boolean matches = predicate.test(info);
            if (matches) {
                removed.add(info);
            }
            return matches;
        });
        return removed;
    }

    private void cleanupRemoved(String event, List<CallbackInfo> removed) {
        for (CallbackInfo callbackInfo : removed) {
            Function<Map<String, Object>, Object> callback = callbackInfo.getCallback();
            callbackFilters.remove(callback);
            circuitBreakers.remove(circuitBreakerKey(event, callback));
            CallbackChain chain = chains.get(event);
            if (chain != null) {
                chain.remove(callback);
            }
        }
    }

    private static Function<ChainContext, Object> adaptRollbackHandler(
            Function<Map<String, Object>, Object> rollbackHandler
    ) {
        if (rollbackHandler == null) {
            return null;
        }
        return context -> rollbackHandler.apply(chainKwargs(null, context));
    }

    private static BiFunction<Exception, ChainContext, Object> adaptErrorHandler(
            Function<Map<String, Object>, Object> errorHandler
    ) {
        if (errorHandler == null) {
            return null;
        }
        return (error, context) -> errorHandler.apply(chainKwargs(error, context));
    }

    private static Map<String, Object> chainKwargs(Exception error, ChainContext context) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("_chain_context", context);
        if (error != null) {
            kwargs.put("_error", error);
        }
        return kwargs;
    }

    private static boolean isTransformCallback(CallbackInfo callbackInfo) {
        return callbackInfo != null && CALLBACK_TYPE_TRANSFORM.equals(callbackInfo.getCallbackType());
    }

    private static String circuitBreakerKey(String event, Function<Map<String, Object>, Object> callback) {
        return event + ":" + callbackName(callback);
    }

    private static String callbackName(Function<Map<String, Object>, Object> callback) {
        return callback == null ? "<null-callback>" : callback.toString();
    }

    private static Object[] safeArgs(Object[] args) {
        return args == null ? new Object[0] : args.clone();
    }

    private static Map<String, Object> safeKwargs(Map<String, Object> kwargs) {
        return kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
    }

    private static Map<String, Object> mergeError(Map<String, Object> kwargs, Exception error) {
        Map<String, Object> merged = safeKwargs(kwargs);
        merged.putIfAbsent("session", null);
        merged.put("error", error);
        return merged;
    }

    private Iterator<Object> cleanupIterator(
            Iterator<Object> delegate,
            String event,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        return new Iterator<>() {
            private boolean cleanupDone;

            @Override
            public boolean hasNext() {
                boolean hasNext = delegate.hasNext();
                if (!hasNext) {
                    runCleanup();
                }
                return hasNext;
            }

            @Override
            public Object next() {
                return delegate.next();
            }

            private void runCleanup() {
                if (!cleanupDone) {
                    cleanupDone = true;
                    executeHooks(event, HookType.CLEANUP, args, kwargs);
                }
            }
        };
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] safeRest = safeArgs(rest);
        Object[] result = new Object[safeRest.length + 1];
        result[0] = first;
        System.arraycopy(safeRest, 0, result, 1, safeRest.length);
        return result;
    }

    private static void appendFlattened(List<Object> output, Object value) {
        if (value instanceof Iterator<?> iterator) {
            iterator.forEachRemaining(output::add);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(output::add);
            return;
        }
        output.add(value);
    }

    private static <T> List<T> snapshot(List<T> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        synchronized (values) {
            return new ArrayList<>(values);
        }
    }

    private static Exception normalize(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(current);
    }

    private static RuntimeException runtime(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(throwable);
    }
}
