/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Production-ready callback framework for Java.
 * <p>
 * A comprehensive event-driven framework with support for:
 * <ul>
 * <li>Priority-based callback execution</li>
 * <li>Filtering and validation</li>
 * <li>Callback chains with rollback</li>
 * <li>Performance metrics</li>
 * <li>Lifecycle hooks</li>
 * <li>Circuit breakers</li>
 * <li>Rate limiting</li>
 * </ul>
 * <p>
 * Callbacks are {@code Function<Map<String, Object>, Object>} where the map contains all
 * keyword arguments. Positional arguments are stored under key "_args" as Object[].
 * 
 * @since 0.1.7
 */
public class CallbackFramework {
    private static final Logger log = LoggerFactory.getLogger(CallbackFramework.class);

    // Core storage
    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, List<CallbackInfo>> callbacks = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, CallbackChain> chains = new ConcurrentHashMap<>();

    // Filters
    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, List<EventFilter>> filters = new ConcurrentHashMap<>();

    /**
     * Collections.synchronizedList.
     * 
     * @param ArrayList<>( ArrayList<>(
     * @since 0.1.7
     */
    private final List<EventFilter> globalFilters = Collections.synchronizedList(new ArrayList<>());

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<Function<Map<String, Object>, Object>, List<EventFilter>> callbackFilters =
        new ConcurrentHashMap<>();

    // Hooks
    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Map<HookType, List<Consumer<Map<String, Object>>>>> hooks = new ConcurrentHashMap<>();

    // Metrics
    private final boolean enableMetrics;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, CallbackMetrics> metrics = new ConcurrentHashMap<>();

    // Logging
    private final boolean enableLogging;

    // Circuit breakers
    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, CircuitBreakerFilter> circuitBreakers = new ConcurrentHashMap<>();

    // Event history
    /**
     * LinkedList<>.
     * 
     * @since 0.1.7
     */
    private final LinkedList<Map<String, Object>> eventHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 1000;
    private boolean enableHistory = false;

    /**
     * CallbackFramework.
     * 
     * @since 0.1.7
     */
    public CallbackFramework() {
        this(true, true);
    }

    /**
     * CallbackFramework.
     * 
     * @param enableMetrics enableMetrics
     * @param enableLogging enableLogging
     * @since 0.1.7
     */
    public CallbackFramework(boolean enableMetrics, boolean enableLogging) {
        this.enableMetrics = enableMetrics;
        this.enableLogging = enableLogging;
    }

    /**
     * getCallbacks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<CallbackInfo>> getCallbacks() {
        return callbacks;
    }

    /**
     * getChains.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, CallbackChain> getChains() {
        return chains;
    }

    /**
     * getCircuitBreakers.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, CircuitBreakerFilter> getCircuitBreakers() {
        return circuitBreakers;
    }

    /**
     * getCallbackFilters.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<Function<Map<String, Object>, Object>, List<EventFilter>> getCallbackFilters() {
        return callbackFilters;
    }

    /**
     * Register a callback for an event.
     * 
     * @param event Event name to listen for
     * @param callback Callback function
     * @param priority Execution priority (higher first)
     * @param once Execute only once then disable
     * @param namespace Namespace for grouping
     * @param tags Set of tags for filtering
     * @param eventFilters List of filters to apply
     * @param rollbackHandler Function to call on rollback
     * @param errorHandler Function to call on error
     * @param maxRetries Maximum retry attempts
     * @param retryDelay Delay between retries in seconds
     * @param timeout Execution timeout in seconds
     * @param callbackName Name for logging
     * @param callbackType callbackType
     * @return The registered CallbackInfo
     * @since 0.1.7
     */
    public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, int priority,
            boolean once, String namespace, Set<String> tags, List<EventFilter> eventFilters,
            Consumer<ChainContext> rollbackHandler, Function<CallbackChain.ExceptionContext, Object> errorHandler,
            int maxRetries, double retryDelay, Double timeout, String callbackName, String callbackType) {
        CallbackInfo callbackInfo = CallbackInfo.builder().callback(callback).priority(priority).once(once)
                .namespace(namespace != null ? namespace : "default").tags(tags != null ? tags : new HashSet<>())
                .maxRetries(maxRetries).retryDelay(retryDelay).timeout(timeout).callbackName(callbackName)
                .callbackType(callbackType != null ? callbackType : "").build();

        callbacks.computeIfAbsent(event, k -> Collections.synchronizedList(new ArrayList<>())).add(callbackInfo);
        sortCallbacks(event);

        if (eventFilters != null && !eventFilters.isEmpty()) {
            callbackFilters.put(callback, new ArrayList<>(eventFilters));
        }

        // Add to chain if rollback/error handlers provided or maxRetries > 0 (matches Python)
        if (rollbackHandler != null || errorHandler != null || maxRetries > 0) {
            chains.computeIfAbsent(event, CallbackChain::new).add(callbackInfo, rollbackHandler, errorHandler);
        }

        if (enableLogging) {
            log.info("Registered callback: {} -> {}", event, callbackInfo.getCallbackDisplayName());
        }

        return callbackInfo;
    }

    /**
     * Simplified register with fewer parameters.
     * 
     * @param event event
     * @param callback callback
     * @param priority priority
     * @param callbackName callbackName
     * @return the result
     * @since 0.1.7
     */
    public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, int priority,
            String callbackName) {
        return register(event, callback, priority, false, "default", null, null, null, null, 0, 0.0, null, callbackName,
                "");
    }

    /**
     * Register with default priority.
     * 
     * @param event event
     * @param callback callback
     * @param callbackName callbackName
     * @return the result
     * @since 0.1.7
     */
    public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, String callbackName) {
        return register(event, callback, 0, callbackName);
    }

    /**
     * Register with explicit parameters, defaulting callbackType to empty string.
     * 
     * @param event event
     * @param callback callback
     * @param priority priority
     * @param once once
     * @param namespace namespace
     * @param tags tags
     * @param eventFilters eventFilters
     * @param rollbackHandler rollbackHandler
     * @param errorHandler errorHandler
     * @param maxRetries maxRetries
     * @param retryDelay retryDelay
     * @param timeout timeout
     * @param callbackName callbackName
     * @return the result
     * @since 0.1.7
     */
    public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, int priority,
            boolean once, String namespace, Set<String> tags, List<EventFilter> eventFilters,
            Consumer<ChainContext> rollbackHandler, Function<CallbackChain.ExceptionContext, Object> errorHandler,
            int maxRetries, double retryDelay, Double timeout, String callbackName) {
        return register(event, callback, priority, once, namespace, tags, eventFilters, rollbackHandler, errorHandler,
                maxRetries, retryDelay, timeout, callbackName, "");
    }

    /**
     * Synchronous registration method for decorator / initialization use.
     * Identical to {@link #register} but explicitly named for use outside async contexts.
     * 
     * @param event event
     * @param callback callback
     * @param priority priority
     * @param once once
     * @param namespace namespace
     * @param tags tags
     * @param eventFilters eventFilters
     * @param rollbackHandler rollbackHandler
     * @param errorHandler errorHandler
     * @param maxRetries maxRetries
     * @param retryDelay retryDelay
     * @param timeout timeout
     * @param callbackName callbackName
     * @return the result
     * @since 0.1.7
     */
    public CallbackInfo registerSync(String event, Function<Map<String, Object>, Object> callback, int priority,
            boolean once, String namespace, Set<String> tags, List<EventFilter> eventFilters,
            Consumer<ChainContext> rollbackHandler, Function<CallbackChain.ExceptionContext, Object> errorHandler,
            int maxRetries, double retryDelay, Double timeout, String callbackName) {
        return register(event, callback, priority, once, namespace, tags, eventFilters, rollbackHandler, errorHandler,
                maxRetries, retryDelay, timeout, callbackName, "");
    }

    /**
     * Unregister a callback from an event.
     * 
     * @param event Event name
     * @param callback Callback to remove
     * @since 0.1.7
     */
    public void unregister(String event, Function<Map<String, Object>, Object> callback) {
        List<CallbackInfo> eventCallbacks = callbacks.get(event);
        if (eventCallbacks == null) {
            return;
        }

        CallbackInfo toRemove = null;
        for (CallbackInfo ci : eventCallbacks) {
            if (ci.getCallback() == callback) {
                toRemove = ci;
                break;
            }
        }

        if (toRemove != null) {
            eventCallbacks.remove(toRemove);
            callbackFilters.remove(callback);

            CallbackChain chain = chains.get(event);
            if (chain != null) {
                chain.remove(callback);
            }

            if (enableLogging) {
                log.info("Unregistered callback: {} -> {}", event, toRemove.getCallbackDisplayName());
            }
        }
    }

    /**
     * Unregister all callbacks in a namespace.
     * 
     * @param namespace namespace
     * @since 0.1.7
     */
    public void unregisterNamespace(String namespace) {
        for (Map.Entry<String, List<CallbackInfo>> entry : callbacks.entrySet()) {
            entry.getValue().removeIf(ci -> namespace.equals(ci.getNamespace()));
        }
    }

    /**
     * Unregister callbacks matching any of the given tags.
     * 
     * @param tags tags
     * @since 0.1.7
     */
    public void unregisterByTags(Set<String> tags) {
        for (Map.Entry<String, List<CallbackInfo>> entry : callbacks.entrySet()) {
            entry.getValue().removeIf(ci -> {
                Set<String> intersection = new HashSet<>(ci.getTags());
                intersection.retainAll(tags);
                return !intersection.isEmpty();
            });
        }
    }

    /**
     * Unregister all callbacks for a specific event.
     * 
     * @param event event
     * @since 0.1.7
     */
    public void unregisterEvent(String event) {
        List<CallbackInfo> eventCallbacks = callbacks.remove(event);
        if (eventCallbacks != null) {
            for (CallbackInfo ci : eventCallbacks) {
                callbackFilters.remove(ci.getCallback());
                String cbKey = event + ":" + ci.getCallbackDisplayName();
                circuitBreakers.remove(cbKey);
            }
        }

        chains.remove(event);
        hooks.remove(event);
        filters.remove(event);

        if (enableLogging) {
            log.info("Unregistered all callbacks for event: {}", event);
        }
    }

    /**
     * Trigger an event and execute all registered callbacks.
     * 
     * @param event Event name to trigger
     * @param args Positional arguments for callbacks
     * @param kwargs Keyword arguments for callbacks
     * @return List of results from all executed callbacks
     * @since 0.1.7
     */
    public List<Object> trigger(String event, Object[] args, Map<String, Object> kwargs) {
        List<Object> results = new ArrayList<>();
        Object[] resolvedArgs = args == null ? new Object[0] : args;
        Map<String, Object> resolvedKwargs = kwargs == null ? new HashMap<>() : kwargs;

        // Record history
        if (enableHistory) {
            recordHistory(event, resolvedArgs, resolvedKwargs);
        }

        // Execute BEFORE hooks
        executeHooks(event, HookType.BEFORE, resolvedArgs, resolvedKwargs);

        List<CallbackInfo> eventCallbacks = callbacks.getOrDefault(event, Collections.emptyList());

        for (CallbackInfo callbackInfo : new ArrayList<>(eventCallbacks)) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }
            // Skip transform-type callbacks in regular trigger (matches Python)
            if ("transform".equals(callbackInfo.getCallbackType())) {
                continue;
            }

            try {
                // Apply filters
                FilterResult filterResult = applyFilters(event, callbackInfo, resolvedArgs, resolvedKwargs);

                if (filterResult.getAction() == FilterAction.STOP) {
                    if (enableLogging) {
                        log.info("Filter stopped event processing: {}", event);
                    }
                    break;
                } else if (filterResult.getAction() == FilterAction.SKIP) {
                    if (enableLogging) {
                        log.debug("Filter skipped callback {}: {}", callbackInfo.getCallbackDisplayName(),
                                filterResult.getReason());
                    }
                    continue;
                }

                Object[] finalArgs =
                    filterResult.getModifiedArgs() != null ? filterResult.getModifiedArgs() : resolvedArgs;
                Map<String, Object> finalKwargs =
                    filterResult.getModifiedKwargs() != null ? filterResult.getModifiedKwargs() : resolvedKwargs;

                // Execute callback with metrics
                long startTime = System.nanoTime();

                // Build the kwargs map for the callback
                Map<String, Object> callbackKwargs = new HashMap<>(finalKwargs);
                callbackKwargs.put("_args", finalArgs);

                Object result = callbackInfo.getCallback().apply(callbackKwargs);

                double executionTime = (System.nanoTime() - startTime) / 1_000_000_000.0;

                // Update metrics
                if (enableMetrics) {
                    String key = event + ":" + callbackInfo.getCallbackDisplayName();
                    metrics.computeIfAbsent(key, k -> new CallbackMetrics()).update(executionTime, false);
                }

                // Record circuit breaker success
                String cbKey = event + ":" + callbackInfo.getCallbackDisplayName();
                CircuitBreakerFilter breaker = circuitBreakers.get(cbKey);
                if (breaker != null) {
                    breaker.recordSuccess(event, callbackInfo);
                }

                results.add(result);

                // Handle once-only callbacks
                if (callbackInfo.isOnce()) {
                    callbackInfo.setEnabled(false);
                }
            } catch (Exception e) {
                // Update metrics
                if (enableMetrics) {
                    String key = event + ":" + callbackInfo.getCallbackDisplayName();
                    metrics.computeIfAbsent(key, k -> new CallbackMetrics()).update(0.0, true);
                }

                // Record circuit breaker failure
                String cbKey = event + ":" + callbackInfo.getCallbackDisplayName();
                CircuitBreakerFilter breaker = circuitBreakers.get(cbKey);
                if (breaker != null) {
                    breaker.recordFailure(event, callbackInfo);
                }

                // Execute ERROR hooks
                Map<String, Object> errorKwargs = new HashMap<>(resolvedKwargs);
                errorKwargs.put("_error", e);
                executeHooks(event, HookType.ERROR, resolvedArgs, errorKwargs);
                RuntimeException requested = extractRequestedException(errorKwargs);
                if (requested != null) {
                    throw requested;
                }

                if (enableLogging) {
                    log.error("Callback execution failed: {} - {}", callbackInfo.getCallbackDisplayName(),
                            e.getMessage(), e);
                }
            }
        }

        // Execute AFTER hooks
        Map<String, Object> afterKwargs = new HashMap<>(resolvedKwargs);
        afterKwargs.put("_results", results);
        executeHooks(event, HookType.AFTER, args, afterKwargs);

        return results;
    }

    /**
     * Trigger with just event name and kwargs.
     * 
     * @param event event
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    public List<Object> trigger(String event, Map<String, Object> kwargs) {
        return trigger(event, new Object[0], kwargs);
    }

    /**
     * Trigger with just event name.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public List<Object> trigger(String event) {
        return trigger(event, new Object[0], new HashMap<>());
    }

    /**
     * Trigger callbacks as a chain with data flow.
     * 
     * @param event Event name
     * @param args Initial positional arguments
     * @param kwargs Initial keyword arguments
     * @return ChainResult with execution outcome
     * @since 0.1.7
     */
    public ChainResult triggerChain(String event, Object[] args, Map<String, Object> kwargs) {
        CallbackChain chain;
        if (!chains.containsKey(event)) {
            chain = new CallbackChain(event);
            for (CallbackInfo ci : callbacks.getOrDefault(event, Collections.emptyList())) {
                chain.add(ci, null, null);
            }
        } else {
            chain = chains.get(event);
        }

        ChainContext context =
            new ChainContext(event, args != null ? args : new Object[0], kwargs != null ? kwargs : new HashMap<>());

        return chain.execute(context);
    }

    /**
     * Trigger callbacks in parallel (concurrent execution).
     * 
     * @param event Event name to trigger
     * @param args Positional arguments for callbacks
     * @param kwargs Keyword arguments for callbacks
     * @return List of results from all callbacks (excluding failed ones)
     * @since 0.1.7
     */
    public List<Object> triggerParallel(String event, Object[] args, Map<String, Object> kwargs) {
        List<CallbackInfo> eventCallbacks = callbacks.getOrDefault(event, Collections.emptyList());
        if (eventCallbacks.isEmpty()) {
            return Collections.emptyList();
        }

        Object[] finalArgs = args != null ? args : new Object[0];
        Map<String, Object> finalKwargs = kwargs != null ? kwargs : new HashMap<>();

        ExecutorService executor = OpenJiuwenExecutors.newCachedThreadPool("callback-parallel", false);
        List<Future<Object>> futures = new ArrayList<>();

        for (CallbackInfo callbackInfo : new ArrayList<>(eventCallbacks)) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }

            futures.add(executor.submit(() -> {
                try {
                    FilterResult filterResult = applyFilters(event, callbackInfo, finalArgs, finalKwargs);

                    if (filterResult.getAction() == FilterAction.STOP
                            || filterResult.getAction() == FilterAction.SKIP) {
                        return null;
                    }

                    Object[] fa = filterResult.getModifiedArgs() != null ? filterResult.getModifiedArgs() : finalArgs;
                    Map<String, Object> fk =
                        filterResult.getModifiedKwargs() != null ? filterResult.getModifiedKwargs() : finalKwargs;

                    Map<String, Object> callbackKwargs = new HashMap<>(fk);
                    callbackKwargs.put("_args", fa);

                    Object result;
                    if (callbackInfo.getTimeout() != null && callbackInfo.getTimeout() > 0) {
                        ExecutorService inner = OpenJiuwenExecutors.newSingleThreadExecutor("callback-timeout", false);
                        try {
                            Future<Object> innerFuture =
                                inner.submit(() -> callbackInfo.getCallback().apply(callbackKwargs));
                            result = innerFuture.get((long) (callbackInfo.getTimeout() * 1000), TimeUnit.MILLISECONDS);
                        } finally {
                            inner.shutdownNow();
                        }
                    } else {
                        result = callbackInfo.getCallback().apply(callbackKwargs);
                    }

                    if (callbackInfo.isOnce()) {
                        callbackInfo.setEnabled(false);
                    }

                    return result;
                } catch (Exception e) {
                    if (enableLogging) {
                        log.error("Callback {} failed in parallel execution: {}", callbackInfo.getCallbackDisplayName(),
                                e.getMessage());
                    }
                    return null;
                }
            }));
        }

        executor.shutdown();

        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                Object result = future.get();
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                if (enableLogging) {
                    log.error("Parallel execution exception: {}", e.getMessage());
                }
            }
        }

        return results;
    }

    /**
     * Trigger callbacks until a condition is satisfied.
     * 
     * @param event Event name to trigger
     * @param condition Function that takes result and returns bool
     * @param args Positional arguments for callbacks
     * @param kwargs Keyword arguments for callbacks
     * @return First result that satisfies condition, or null
     * @since 0.1.7
     */
    public Object triggerUntil(String event, Predicate<Object> condition, Object[] args, Map<String, Object> kwargs) {
        List<CallbackInfo> eventCallbacks = callbacks.getOrDefault(event, Collections.emptyList());
        if (eventCallbacks.isEmpty()) {
            return null;
        }

        Object[] finalArgs = args != null ? args : new Object[0];
        Map<String, Object> finalKwargs = kwargs != null ? kwargs : new HashMap<>();

        for (CallbackInfo callbackInfo : new ArrayList<>(eventCallbacks)) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }

            try {
                FilterResult filterResult = applyFilters(event, callbackInfo, finalArgs, finalKwargs);

                if (filterResult.getAction() == FilterAction.STOP) {
                    break;
                } else if (filterResult.getAction() == FilterAction.SKIP) {
                    continue;
                }

                Object[] fa = filterResult.getModifiedArgs() != null ? filterResult.getModifiedArgs() : finalArgs;
                Map<String, Object> fk =
                    filterResult.getModifiedKwargs() != null ? filterResult.getModifiedKwargs() : finalKwargs;

                Map<String, Object> callbackKwargs = new HashMap<>(fk);
                callbackKwargs.put("_args", fa);

                Object result = callbackInfo.getCallback().apply(callbackKwargs);

                if (condition.test(result)) {
                    if (enableLogging) {
                        log.info("Condition satisfied by {}: {}", callbackInfo.getCallbackDisplayName(), result);
                    }
                    if (callbackInfo.isOnce()) {
                        callbackInfo.setEnabled(false);
                    }
                    return result;
                }

                if (callbackInfo.isOnce()) {
                    callbackInfo.setEnabled(false);
                }
            } catch (Exception e) {
                if (enableLogging) {
                    log.error("Callback {} failed in trigger_until: {}", callbackInfo.getCallbackDisplayName(),
                            e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Trigger event with timeout control.
     * 
     * @param event Event name to trigger
     * @param timeoutSeconds Maximum execution time in seconds
     * @param args Positional arguments for callbacks
     * @param kwargs Keyword arguments for callbacks
     * @return List of callback results (may be empty if timeout)
     * @since 0.1.7
     */
    public List<Object> triggerWithTimeout(String event, double timeoutSeconds, Object[] args,
            Map<String, Object> kwargs) {
        // Run all callbacks sequentially (no per-callback timeouts), then discard if total > timeout
        List<CallbackInfo> eventCallbacks = new ArrayList<>(callbacks.getOrDefault(event, Collections.emptyList()));
        List<Object> results = new ArrayList<>();
        long startNano = System.nanoTime();

        Object[] finalArgs = args != null ? args : new Object[0];
        Map<String, Object> finalKwargs = kwargs != null ? kwargs : new HashMap<>();

        for (CallbackInfo callbackInfo : eventCallbacks) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }
            try {
                FilterResult filterResult = applyFilters(event, callbackInfo, finalArgs, finalKwargs);
                if (filterResult.getAction() == FilterAction.STOP || filterResult.getAction() == FilterAction.SKIP) {
                    continue;
                }
                Object[] fa = filterResult.getModifiedArgs() != null ? filterResult.getModifiedArgs() : finalArgs;
                Map<String, Object> fk =
                    filterResult.getModifiedKwargs() != null ? filterResult.getModifiedKwargs() : finalKwargs;

                Map<String, Object> callbackKwargs = new HashMap<>(fk);
                callbackKwargs.put("_args", fa);

                Object result = callbackInfo.getCallback().apply(callbackKwargs);

                if (callbackInfo.isOnce()) {
                    callbackInfo.setEnabled(false);
                }
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                if (enableLogging) {
                    log.error("Callback {} failed in triggerWithTimeout: {}", callbackInfo.getCallbackDisplayName(),
                            e.getMessage());
                }
            }
        }

        long elapsedNano = System.nanoTime() - startNano;
        if (elapsedNano > (long) (timeoutSeconds * 1_000_000_000)) {
            if (enableLogging) {
                log.warn("Event '{}' exceeded timeout of {}s", event, timeoutSeconds);
            }
            return Collections.emptyList();
        }
        return results;
    }

    /**
     * Trigger event for each item in an input iterator (stream processing).
     * 
     * @param event Event name to trigger
     * @param inputStream Iterator providing input items
     * @param args Additional positional arguments for callbacks
     * @param kwargs Additional keyword arguments for callbacks
     * @return Iterator of results from callback executions
     * @since 0.1.7
     */
    public Iterator<Object> triggerStream(String event, Iterator<?> inputStream, Object[] args,
            Map<String, Object> kwargs) {
        List<Object> allResults = new ArrayList<>();
        while (inputStream.hasNext()) {
            Object item = inputStream.next();
            Object[] combinedArgs = new Object[]{item};
            if (args != null && args.length > 0) {
                Object[] newArgs = new Object[1 + args.length];
                newArgs[0] = item;
                System.arraycopy(args, 0, newArgs, 1, args.length);
                combinedArgs = newArgs;
            }
            List<Object> results = trigger(event, combinedArgs, kwargs);
            for (Object result : results) {
                flattenResult(result, allResults);
            }
        }
        return allResults.iterator();
    }

    /**
     * flattenResult.
     * 
     * @param result result
     * @param output output
     * @since 0.1.7
     */
    private void flattenResult(Object result, List<Object> output) {
        if (result instanceof Iterator<?> it) {
            while (it.hasNext()) {
                output.add(it.next());
            }
        } else if (result instanceof Iterable<?> iter && !(result instanceof Map) && !(result instanceof String)) {
            for (Object item : iter) {
                output.add(item);
            }
        } else {
            output.add(result);
        }
    }

    /**
     * Trigger an event after a delay.
     * 
     * @param event Event name to trigger
     * @param delaySeconds Delay in seconds before triggering
     * @param args Positional arguments for callbacks
     * @param kwargs Keyword arguments for callbacks
     * @return ScheduledFuture that can be cancelled; its result is the callback result list
     * @since 0.1.7
     */
    public ScheduledFuture<List<Object>> triggerDelayed(String event, double delaySeconds, Object[] args,
            Map<String, Object> kwargs) {
        ScheduledExecutorService scheduler = OpenJiuwenExecutors.newScheduledThreadPool("callback-delay", 1, false);
        long delayMillis = (long) (delaySeconds * 1000);
        return scheduler.schedule(() -> {
            try {
                return trigger(event, args, kwargs);
            } finally {
                scheduler.shutdown();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Trigger event and aggregate results from all callbacks as an iterator.
     * <p>
     * If a callback returns an {@link Iterable} or {@link Iterator}, its items are
     * individually yielded; otherwise the single result is yielded.
     * 
     * @param event Event name to trigger
     * @param args Positional arguments for callbacks
     * @param kwargs Keyword arguments for callbacks
     * @return Iterator of individual results
     * @since 0.1.7
     */
    public Iterator<Object> triggerGenerator(String event, Object[] args, Map<String, Object> kwargs) {
        List<Object> aggregated = new ArrayList<>();
        Object[] resolvedArgs = args == null ? new Object[0] : args;
        Map<String, Object> resolvedKwargs = kwargs == null ? new HashMap<>() : kwargs;

        executeHooks(event, HookType.BEFORE, resolvedArgs, resolvedKwargs);

        List<CallbackInfo> eventCallbacks = callbacks.getOrDefault(event, Collections.emptyList());

        for (CallbackInfo callbackInfo : new ArrayList<>(eventCallbacks)) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }

            try {
                FilterResult filterResult = applyFilters(event, callbackInfo, resolvedArgs, resolvedKwargs);

                if (filterResult.getAction() == FilterAction.STOP) {
                    break;
                } else if (filterResult.getAction() == FilterAction.SKIP) {
                    continue;
                }

                Object[] finalArgs =
                    filterResult.getModifiedArgs() != null ? filterResult.getModifiedArgs() : resolvedArgs;
                Map<String, Object> finalKwargs =
                    filterResult.getModifiedKwargs() != null ? filterResult.getModifiedKwargs() : resolvedKwargs;

                Map<String, Object> callbackKwargs = new HashMap<>(finalKwargs);
                callbackKwargs.put("_args", finalArgs);

                long startTime = System.nanoTime();
                Object result = callbackInfo.getCallback().apply(callbackKwargs);
                double executionTime = (System.nanoTime() - startTime) / 1_000_000_000.0;

                if (enableMetrics) {
                    String key = event + ":" + callbackInfo.getCallbackDisplayName();
                    metrics.computeIfAbsent(key, k -> new CallbackMetrics()).update(executionTime, false);
                }

                // Flatten Iterable/Iterator results
                if (result instanceof Iterable<?> iterable) {
                    for (Object item : iterable) {
                        aggregated.add(item);
                    }
                } else if (result instanceof Iterator<?> iter) {
                    while (iter.hasNext()) {
                        aggregated.add(iter.next());
                    }
                } else if (result != null) {
                    aggregated.add(result);
                }

                if (callbackInfo.isOnce()) {
                    callbackInfo.setEnabled(false);
                }
            } catch (Exception e) {
                if (enableMetrics) {
                    String key = event + ":" + callbackInfo.getCallbackDisplayName();
                    metrics.computeIfAbsent(key, k -> new CallbackMetrics()).update(0.0, true);
                }
                if (enableLogging) {
                    log.error("Callback {} failed in triggerGenerator: {}", callbackInfo.getCallbackDisplayName(),
                            e.getMessage(), e);
                }
            }
        }

        Map<String, Object> afterKwargs = new HashMap<>(resolvedKwargs);
        afterKwargs.put("_results", aggregated);
        executeHooks(event, HookType.AFTER, resolvedArgs, afterKwargs);

        return aggregated.iterator();
    }

    /**
     * TRANSFORM_NOOP.
     * 
     * @since 0.1.7
     */
    public static final Object TRANSFORM_NOOP = new Object();

    /**
     * triggerTransform.
     * 
     * @param event event
     * @param args args
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
        List<CallbackInfo> eventCallbacks = callbacks.getOrDefault(event, Collections.emptyList());
        List<CallbackInfo> transformCallbacks = new ArrayList<>();
        for (CallbackInfo ci : eventCallbacks) {
            if (ci.isEnabled() && "transform".equals(ci.getCallbackType())) {
                transformCallbacks.add(ci);
            }
        }
        if (transformCallbacks.isEmpty()) {
            return TRANSFORM_NOOP;
        }
        Object[] resolvedArgs = args != null ? args : new Object[0];
        Map<String, Object> resolvedKwargs = kwargs != null ? kwargs : new HashMap<>();
        Object result = TRANSFORM_NOOP;
        for (CallbackInfo callbackInfo : transformCallbacks) {
            Map<String, Object> callbackKwargs = new HashMap<>(resolvedKwargs);
            callbackKwargs.put("_args", resolvedArgs);
            result = callbackInfo.getCallback().apply(callbackKwargs);
        }
        return result;
    }

    /**
     * Add a filter to a specific event.
     * 
     * @param event event
     * @param filter filter
     * @since 0.1.7
     */
    public void addFilter(String event, EventFilter filter) {
        filters.computeIfAbsent(event, k -> Collections.synchronizedList(new ArrayList<>())).add(filter);
    }

    /**
     * Add a filter that applies to all events.
     * 
     * @param filter filter
     * @since 0.1.7
     */
    public void addGlobalFilter(EventFilter filter) {
        globalFilters.add(filter);
    }

    /**
     * Add circuit breaker protection to a callback.
     * 
     * @param event event
     * @param callback callback
     * @param failureThreshold failureThreshold
     * @param timeout timeout
     * @since 0.1.7
     */
    public void addCircuitBreaker(String event, CallbackInfo callback, int failureThreshold, double timeout) {
        String key = event + ":" + callback.getCallbackDisplayName();
        CircuitBreakerFilter breaker = new CircuitBreakerFilter(failureThreshold, timeout);
        circuitBreakers.put(key, breaker);
        addFilter(event, breaker);
    }

    /**
     * Apply all relevant filters to a callback.
     * Filters are applied in order: global -> event -> callback-specific.
     * 
     * @param event event
     * @param callbackInfo callbackInfo
     * @param args args
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    private FilterResult applyFilters(String event, CallbackInfo callbackInfo, Object[] args,
            Map<String, Object> kwargs) {
        Object[] currentArgs = args;
        Map<String, Object> currentKwargs = kwargs;

        List<EventFilter> allFilters = new ArrayList<>();
        allFilters.addAll(globalFilters);
        allFilters.addAll(filters.getOrDefault(event, Collections.emptyList()));
        List<EventFilter> cbFilters = callbackFilters.get(callbackInfo.getCallback());
        if (cbFilters != null) {
            allFilters.addAll(cbFilters);
        }

        for (EventFilter filter : allFilters) {
            FilterResult result = filter.filter(event, callbackInfo, currentArgs, currentKwargs);

            if (result.getAction() == FilterAction.STOP || result.getAction() == FilterAction.SKIP) {
                return result;
            } else if (result.getAction() == FilterAction.MODIFY) {
                if (result.getModifiedArgs() != null) {
                    currentArgs = result.getModifiedArgs();
                }
                if (result.getModifiedKwargs() != null) {
                    currentKwargs = result.getModifiedKwargs();
                }
            } else {
                // no-op
            }
        }

        return FilterResult.continueResult(currentArgs, currentKwargs);
    }

    /**
     * Add a lifecycle hook to an event.
     * 
     * @param event Event name
     * @param hookType Type of hook (BEFORE, AFTER, ERROR, CLEANUP)
     * @param hook Consumer that receives a map with "_args" and other kwargs
     * @since 0.1.7
     */
    public void addHook(String event, HookType hookType, Consumer<Map<String, Object>> hook) {
        hooks.computeIfAbsent(event, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(hookType, k -> Collections.synchronizedList(new ArrayList<>())).add(hook);
    }

    /**
     * Execute all hooks of a specific type for an event.
     * 
     * @param event event
     * @param hookType hookType
     * @param args args
     * @param kwargs kwargs
     * @since 0.1.7
     */
    private void executeHooks(String event, HookType hookType, Object[] args, Map<String, Object> kwargs) {
        Map<HookType, List<Consumer<Map<String, Object>>>> eventHooks = hooks.get(event);
        if (eventHooks == null) {
            return;
        }

        List<Consumer<Map<String, Object>>> hookList = eventHooks.get(hookType);
        if (hookList == null) {
            return;
        }

        Map<String, Object> hookKwargs = new HashMap<>(kwargs);
        hookKwargs.put("_args", args);

        for (Consumer<Map<String, Object>> hook : hookList) {
            try {
                hook.accept(hookKwargs);
            } catch (Exception e) {
                log.error("Hook execution failed: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Get performance metrics for callbacks.
     * 
     * @param event Optional event name filter
     * @param callback Optional callback name filter
     * @return Map of callback keys to metric maps
     * @since 0.1.7
     */
    public Map<String, Map<String, Object>> getMetrics(String event, String callback) {
        Map<String, Map<String, Object>> result = new HashMap<>();

        for (Map.Entry<String, CallbackMetrics> entry : metrics.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(":", 2);
            String eventName = parts[0];
            String callbackName = parts.length > 1 ? parts[1] : "";

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

    /**
     * Get all metrics.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Map<String, Object>> getMetrics() {
        return getMetrics(null, null);
    }

    /**
     * Reset all performance metrics.
     * 
     * @since 0.1.7
     */
    public void resetMetrics() {
        metrics.clear();
    }

    /**
     * Get callbacks with average execution time above threshold.
     * 
     * @param threshold Minimum average time in seconds
     * @return List of slow callback information
     * @since 0.1.7
     */
    public List<Map<String, Object>> getSlowCallbacks(double threshold) {
        List<Map<String, Object>> slowCallbacks = new ArrayList<>();

        for (Map.Entry<String, CallbackMetrics> entry : metrics.entrySet()) {
            CallbackMetrics m = entry.getValue();
            if (m.getAvgTime() > threshold) {
                Map<String, Object> info = new HashMap<>();
                info.put("callback", entry.getKey());
                info.put("avg_time", m.getAvgTime());
                info.put("max_time", m.getMaxTime());
                info.put("call_count", m.getCallCount());
                slowCallbacks.add(info);
            }
        }

        slowCallbacks.sort((a, b) -> Double.compare((double) b.get("avg_time"), (double) a.get("avg_time")));
        return slowCallbacks;
    }

    // ========== History & Replay ==========

    /**
     * Enable or disable event history recording.
     * 
     * @param enabled enabled
     * @since 0.1.7
     */
    public void enableEventHistory(boolean enabled) {
        this.enableHistory = enabled;
    }

    /**
     * Get recorded event history.
     * 
     * @param event Optional event name filter
     * @param since Optional epoch millis filter
     * @return List of event records
     * @since 0.1.7
     */
    public List<Map<String, Object>> getEventHistory(String event, Long since) {
        List<Map<String, Object>> result;
        synchronized (eventHistory) {
            result = new ArrayList<>(eventHistory);
        }

        if (event != null) {
            result.removeIf(h -> !event.equals(h.get("event")));
        }
        if (since != null) {
            double sinceTimestamp = since / 1000.0;
            result.removeIf(h -> (double) h.get("timestamp") < sinceTimestamp);
        }
        return result;
    }

    /**
     * Replay recorded events.
     * 
     * @param since since
     * @since 0.1.7
     */
    public void replayEvents(Long since) {
        List<Map<String, Object>> history = getEventHistory(null, since);

        for (Map<String, Object> record : history) {
            String event = (String) record.get("event");
            Object[] args = (Object[]) record.get("args");
            @SuppressWarnings("unchecked")
            Map<String, Object> kwargs = (Map<String, Object>) record.get("kwargs");
            trigger(event, args, kwargs);
        }
    }

    /**
     * recordHistory.
     * 
     * @param event event
     * @param args args
     * @param kwargs kwargs
     * @since 0.1.7
     */
    private void recordHistory(String event, Object[] args, Map<String, Object> kwargs) {
        Map<String, Object> record = new HashMap<>();
        record.put("event", event);
        record.put("args", args);
        record.put("kwargs", kwargs);
        record.put("timestamp", System.currentTimeMillis() / 1000.0);

        synchronized (eventHistory) {
            eventHistory.addLast(record);
            while (eventHistory.size() > MAX_HISTORY_SIZE) {
                eventHistory.removeFirst();
            }
        }
    }

    // ========== State Persistence ==========

    /**
     * Save framework state (metadata only, not callback functions) to a JSON file.
     * 
     * @param filepath Path to the output file
     * @since 0.1.7
     */
    public void saveState(String filepath) {
        Map<String, Object> state = new HashMap<>();

        // Callback metadata
        Map<String, List<Map<String, Object>>> callbackState = new HashMap<>();
        for (Map.Entry<String, List<CallbackInfo>> entry : callbacks.entrySet()) {
            List<Map<String, Object>> infos = new ArrayList<>();
            for (CallbackInfo ci : entry.getValue()) {
                Map<String, Object> info = new HashMap<>();
                info.put("name", ci.getCallbackDisplayName());
                info.put("priority", ci.getPriority());
                info.put("namespace", ci.getNamespace());
                info.put("tags", new ArrayList<>(ci.getTags()));
                info.put("enabled", ci.isEnabled());
                infos.add(info);
            }
            callbackState.put(entry.getKey(), infos);
        }
        state.put("callbacks", callbackState);

        // Metrics
        Map<String, Map<String, Object>> metricsState = new HashMap<>();
        for (Map.Entry<String, CallbackMetrics> entry : metrics.entrySet()) {
            metricsState.put(entry.getKey(), entry.getValue().toMap());
        }
        state.put("metrics", metricsState);

        // History
        synchronized (eventHistory) {
            state.put("history", new ArrayList<>(eventHistory));
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filepath), state);
        } catch (IOException e) {
            log.error("Failed to save state to {}: {}", filepath, e.getMessage(), e);
        }
    }

    /**
     * List all registered events.
     * 
     * @param namespace Optional namespace filter
     * @return List of event names
     * @since 0.1.7
     */
    public List<String> listEvents(String namespace) {
        List<String> events = new ArrayList<>(callbacks.keySet());

        if (namespace != null) {
            events.removeIf(event -> {
                List<CallbackInfo> cbs = callbacks.get(event);
                return cbs == null || cbs.stream().noneMatch(ci -> namespace.equals(ci.getNamespace()));
            });
        }
        return events;
    }

    /**
     * List all callbacks registered for an event.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> listCallbacks(String event) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (CallbackInfo ci : callbacks.getOrDefault(event, Collections.emptyList())) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", ci.getCallbackDisplayName());
            info.put("priority", ci.getPriority());
            info.put("enabled", ci.isEnabled());
            info.put("namespace", ci.getNamespace());
            info.put("tags", new ArrayList<>(ci.getTags()));
            info.put("once", ci.isOnce());
            info.put("max_retries", ci.getMaxRetries());
            info.put("timeout", ci.getTimeout());
            result.add(info);
        }
        return result;
    }

    /**
     * Get overall framework statistics.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getStatistics() {
        int totalCallbacks = callbacks.values().stream().mapToInt(List::size).sum();
        int totalEvents = callbacks.size();

        Set<String> namespaces = new HashSet<>();
        for (List<CallbackInfo> cbList : callbacks.values()) {
            for (CallbackInfo ci : cbList) {
                namespaces.add(ci.getNamespace());
            }
        }

        int totalFilters = globalFilters.size() + filters.values().stream().mapToInt(List::size).sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_events", totalEvents);
        stats.put("total_callbacks", totalCallbacks);
        stats.put("namespaces", new ArrayList<>(namespaces));
        stats.put("total_filters", totalFilters);
        stats.put("total_chains", chains.size());
        stats.put("history_size", eventHistory.size());
        stats.put("metrics_collected", metrics.size());
        return stats;
    }

    // ========== Decorator-style DSL ==========
    // These methods mirror Python's AsyncCallbackFramework decorator API:

    /**
     * Register a callback via the DSL style (mirrors Python {@code @framework.on(event)}).
     * <p>
     * Returns the registered {@link CallbackInfo} so callers can hold a reference.
     * 
     * @param event Event name to listen for
     * @param callback Callback function
     * @param callbackName callbackName
     * @return The registered CallbackInfo
     * @since 0.1.7
     */
    public CallbackInfo on(String event, Function<Map<String, Object>, Object> callback, String callbackName) {
        return register(event, callback, 0, callbackName);
    }

    /**
     * Full-parameter DSL registration (mirrors Python {@code @framework.on(event, priority=..., ...)}).
     * 
     * @param event event
     * @param callback callback
     * @param priority priority
     * @param once once
     * @param namespace namespace
     * @param tags tags
     * @param eventFilters eventFilters
     * @param rollbackHandler rollbackHandler
     * @param errorHandler errorHandler
     * @param maxRetries maxRetries
     * @param retryDelay retryDelay
     * @param timeout timeout
     * @param callbackName callbackName
     * @return the result
     * @since 0.1.7
     */
    public CallbackInfo on(String event, Function<Map<String, Object>, Object> callback, int priority, boolean once,
            String namespace, Set<String> tags, List<EventFilter> eventFilters, Consumer<ChainContext> rollbackHandler,
            Function<CallbackChain.ExceptionContext, Object> errorHandler, int maxRetries, double retryDelay,
            Double timeout, String callbackName) {
        return register(event, callback, priority, once, namespace, tags, eventFilters, rollbackHandler, errorHandler,
                maxRetries, retryDelay, timeout, callbackName, "");
    }

    /**
     * Wrap a function so that it triggers an event when called (mirrors Python
     * {@code @framework.trigger_on_call(event)}).
     * <p>
     * Returns a new function that:
     * <ol>
     * <li>Triggers the event (with args if {@code passArgs} is true)</li>
     * <li>Invokes the original function</li>
     * <li>Optionally triggers the event again with the result</li>
     * </ol>
     * 
     * @param event Event name to trigger
     * @param wrapped The function to wrap
     * @param passResult Whether to trigger event again with the result
     * @param passArgs Whether to pass function arguments to the event
     * @return Wrapped function
     * @since 0.1.7
     */
    public Function<Map<String, Object>, Object> triggerOnCall(String event,
            Function<Map<String, Object>, Object> wrapped, boolean passResult, boolean passArgs) {
        return kwargs -> {
            if (passArgs) {
                trigger(event, (Object[]) kwargs.get("_args"), kwargs);
            } else {
                trigger(event);
            }

            Object result = wrapped.apply(kwargs);

            if (passResult) {
                Map<String, Object> resultKwargs = new HashMap<>(kwargs);
                resultKwargs.put("result", result);
                trigger(event, (Object[]) kwargs.get("_args"), resultKwargs);
            }

            return result;
        };
    }

    /**
     * Wrap a function so that it triggers an event with the result after execution
     * (mirrors Python {@code @framework.emits(event)}).
     * 
     * @param event Event name to trigger after execution
     * @param wrapped The function to wrap
     * @param resultKey Keyword argument name for the result (default: "result")
     * @param includeArgs Whether to include original args in event
     * @return Wrapped function
     * @since 0.1.7
     */
    public Function<Map<String, Object>, Object> emits(String event, Function<Map<String, Object>, Object> wrapped,
            String resultKey, boolean includeArgs) {
        return kwargs -> {
            Object result = wrapped.apply(kwargs);

            Map<String, Object> eventKwargs = new HashMap<>();
            eventKwargs.put(resultKey, result);
            if (includeArgs) {
                eventKwargs.putAll(kwargs);
                eventKwargs.put(resultKey, result);
            }

            Object[] args = includeArgs ? (Object[]) kwargs.get("_args") : new Object[0];
            trigger(event, args, eventKwargs);

            return result;
        };
    }

    /**
     * emitsStream.
     * 
     * @param event event
     * @param wrapped wrapped
     * @param itemKey itemKey
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Function<Map<String, Object>, Object> emitsStream(String event,
            Function<Map<String, Object>, Object> wrapped, String itemKey) {
        String mode = "result".equals(itemKey) ? "once" : "per_item";
        return emitsStream(event, wrapped, itemKey, true, mode);
    }

    /**
     * Wrap an iterator-producing function with stream event triggers.
     * Supports passArgs (include original function arguments) and streamMode
     * (per_item triggers per item, once triggers once with all items).
     * 
     * @param event event
     * @param wrapped wrapped
     * @param itemKey itemKey
     * @param passArgs passArgs
     * @param streamMode streamMode
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Function<Map<String, Object>, Object> emitsStream(String event,
            Function<Map<String, Object>, Object> wrapped, String itemKey, boolean passArgs, String streamMode) {
        return kwargs -> {
            Object rawResult = wrapped.apply(kwargs);
            if (!(rawResult instanceof Iterator<?> || rawResult instanceof Iterable<?>)) {
                throw new IllegalStateException("emitsStream can only wrap functions returning Iterator/Iterable, got "
                        + (rawResult == null ? "null" : rawResult.getClass().getName()));
            }
            Iterator<?> source;
            if (rawResult instanceof Iterable<?> iterable) {
                source = iterable.iterator();
            } else {
                source = (Iterator<?>) rawResult;
            }

            List<Object> collected = new ArrayList<>();
            while (source.hasNext()) {
                Object item = source.next();
                collected.add(item);
            }

            Object[] originalArgs =
                kwargs.get("_args") instanceof Object[] ? (Object[]) kwargs.get("_args") : new Object[0];
            Object[] triggerArgs = passArgs ? originalArgs : new Object[0];

            if ("once".equals(streamMode)) {
                Map<String, Object> eventKwargs = new HashMap<>();
                if (passArgs) {
                    eventKwargs.putAll(kwargs);
                }
                eventKwargs.put(itemKey != null ? itemKey : "result", collected);
                trigger(event, triggerArgs, eventKwargs);
            } else {
                // per_item mode (default)
                for (Object item : collected) {
                    Map<String, Object> eventKwargs = new HashMap<>();
                    if (passArgs) {
                        eventKwargs.putAll(kwargs);
                    }
                    eventKwargs.put(itemKey != null ? itemKey : "item", item);
                    trigger(event, triggerArgs, eventKwargs);
                }
            }
            return collected.iterator();
        };
    }

    /**
     * Wrap a function with before/after/error events (mirrors Python {@code @framework.emit_around(before, after)}).
     * 
     * @param beforeEvent Event to trigger before execution
     * @param afterEvent Event to trigger after successful execution
     * @param wrapped The function to wrap
     * @param passArgs Whether to pass function arguments to events
     * @param passResult Whether to pass result to afterEvent
     * @param onErrorEvent Optional event to trigger on error (null to skip)
     * @return Wrapped function
     * @since 0.1.7
     */
    public Function<Map<String, Object>, Object> emitAround(String beforeEvent, String afterEvent,
            Function<Map<String, Object>, Object> wrapped, boolean passArgs, boolean passResult, String onErrorEvent) {
        return kwargs -> {
            Object[] args = kwargs.get("_args") instanceof Object[] ? (Object[]) kwargs.get("_args") : new Object[0];

            // Before event
            if (passArgs) {
                trigger(beforeEvent, args, kwargs);
            } else {
                trigger(beforeEvent);
            }

            try {
                Object result = wrapped.apply(kwargs);

                // After event
                if (passResult) {
                    Map<String, Object> afterKwargs = new HashMap<>(kwargs);
                    afterKwargs.put("result", result);
                    if (passArgs) {
                        trigger(afterEvent, args, afterKwargs);
                    } else {
                        Map<String, Object> resultOnly = new HashMap<>();
                        resultOnly.put("result", result);
                        trigger(afterEvent, new Object[0], resultOnly);
                    }
                } else {
                    if (passArgs) {
                        trigger(afterEvent, args, kwargs);
                    } else {
                        trigger(afterEvent);
                    }
                }

                return result;
            } catch (Exception e) {
                if (onErrorEvent != null) {
                    Map<String, Object> errorKwargs = new HashMap<>(kwargs);
                    errorKwargs.put("error", e);
                    if (passArgs) {
                        trigger(onErrorEvent, args, errorKwargs);
                    } else {
                        Map<String, Object> errOnly = new HashMap<>();
                        errOnly.put("error", e);
                        trigger(onErrorEvent, new Object[0], errOnly);
                    }
                }
                throw e;
            }
        };
    }

    /**
     * Wrap a function with input/output transformation via direct callables
     * (mirrors Python {@code @framework.transform_io(input_transform=..., output_transform=...)}).
     * 
     * @param wrapped The function to wrap
     * @param inputTransform Optional input transform: takes kwargs, returns modified kwargs (null to skip)
     * @param outputTransform Optional output transform: takes result, returns modified result (null to skip)
     * @return Wrapped function with I/O transformation
     * @since 0.1.7
     */
    public Function<Map<String, Object>, Object> transformIo(Function<Map<String, Object>, Object> wrapped,
            Function<Map<String, Object>, Map<String, Object>> inputTransform,
            Function<Object, Object> outputTransform) {
        return kwargs -> {
            Map<String, Object> finalKwargs = kwargs;
            if (inputTransform != null) {
                finalKwargs = inputTransform.apply(kwargs);
            }

            Object result = wrapped.apply(finalKwargs);

            if (outputTransform != null) {
                result = outputTransform.apply(result);
            }
            return result;
        };
    }

    /**
     * transformIoByEvents.
     * 
     * @param wrapped wrapped
     * @param inputEvent inputEvent
     * @param outputEvent outputEvent
     * @param resultKey resultKey
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Function<Map<String, Object>, Object> transformIoByEvents(Function<Map<String, Object>, Object> wrapped,
            String inputEvent, String outputEvent, String resultKey) {
        return kwargs -> {
            Map<String, Object> finalKwargs = kwargs;

            // Input transform: only run callbacks with callbackType="transform"
            if (inputEvent != null) {
                Object[] args =
                    kwargs.get("_args") instanceof Object[] ? (Object[]) kwargs.get("_args") : new Object[0];
                List<Object> results = triggerTransformOnly(inputEvent, args, kwargs);
                if (!results.isEmpty()) {
                    Object last = results.get(results.size() - 1);
                    if (last instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> lastMap = (Map<String, Object>) last;
                        finalKwargs = lastMap;
                    }
                }
            }

            Object result = wrapped.apply(finalKwargs);

            // Output transform: only run callbacks with callbackType="transform"
            if (outputEvent != null) {
                Map<String, Object> outKwargs = new HashMap<>();
                outKwargs.put(resultKey, result);
                List<Object> results = triggerTransformOnly(outputEvent, new Object[0], outKwargs);
                if (!results.isEmpty()) {
                    result = results.get(results.size() - 1);
                }
            }

            return result;
        };
    }

    /**
     * triggerTransformOnly.
     * 
     * @param event event
     * @param args args
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    private List<Object> triggerTransformOnly(String event, Object[] args, Map<String, Object> kwargs) {
        List<CallbackInfo> eventCallbacks = callbacks.getOrDefault(event, Collections.emptyList());
        List<Object> results = new ArrayList<>();
        Object[] finalArgs = args != null ? args : new Object[0];
        Map<String, Object> finalKwargs = kwargs != null ? kwargs : new HashMap<>();
        for (CallbackInfo callbackInfo : new ArrayList<>(eventCallbacks)) {
            if (!callbackInfo.isEnabled() || !"transform".equals(callbackInfo.getCallbackType())) {
                continue;
            }
            try {
                Map<String, Object> callbackKwargs = new HashMap<>(finalKwargs);
                callbackKwargs.put("_args", finalArgs);
                Object result = callbackInfo.getCallback().apply(callbackKwargs);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                if (enableLogging) {
                    log.error("Transform callback {} failed: {}", callbackInfo.getCallbackDisplayName(),
                            e.getMessage());
                }
            }
        }
        return results;
    }

    /**
     * sortCallbacks.
     * 
     * @param event event
     * @since 0.1.7
     */
    private void sortCallbacks(String event) {
        List<CallbackInfo> list = callbacks.get(event);
        if (list != null) {
            list.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        }
    }

    /**
     * extractRequestedException.
     * 
     * @param hookKwargs hookKwargs
     * @return the result
     * @since 0.1.7
     */
    private static RuntimeException extractRequestedException(Map<String, Object> hookKwargs) {
        Object raised = hookKwargs.get("_raise");
        if (raised instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (raised instanceof Throwable throwable) {
            return new RuntimeException(throwable);
        }
        return null;
    }
}
