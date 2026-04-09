/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.runner.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Production-ready callback framework for Java.
 * <p>
 * A comprehensive event-driven framework with support for:
 * <ul>
 *   <li>Priority-based callback execution</li>
 *   <li>Filtering and validation</li>
 *   <li>Callback chains with rollback</li>
 *   <li>Performance metrics</li>
 *   <li>Lifecycle hooks</li>
 *   <li>Circuit breakers</li>
 *   <li>Rate limiting</li>
 * </ul>
 * <p>
 * Callbacks are {@code Function<Map<String, Object>, Object>} where the map contains all
 * keyword arguments. Positional arguments are stored under key "_args" as Object[].
 */
public class CallbackFramework {

    private static final Logger log = LoggerFactory.getLogger(CallbackFramework.class);

    // Core storage
    private final Map<String, List<CallbackInfo>> callbacks = new ConcurrentHashMap<>();
    private final Map<String, CallbackChain> chains = new ConcurrentHashMap<>();

    // Filters
    private final Map<String, List<EventFilter>> filters = new ConcurrentHashMap<>();
    private final List<EventFilter> globalFilters = Collections.synchronizedList(new ArrayList<>());
    private final Map<Function<Map<String, Object>, Object>, List<EventFilter>> callbackFilters = new ConcurrentHashMap<>();

    // Hooks
    private final Map<String, Map<HookType, List<Consumer<Map<String, Object>>>>> hooks = new ConcurrentHashMap<>();

    // Metrics
    private final boolean enableMetrics;
    private final Map<String, CallbackMetrics> metrics = new ConcurrentHashMap<>();

    // Logging
    private final boolean enableLogging;

    // Circuit breakers
    private final Map<String, CircuitBreakerFilter> circuitBreakers = new ConcurrentHashMap<>();

    // Event history
    private final LinkedList<Map<String, Object>> eventHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 1000;
    private boolean enableHistory = false;

    public CallbackFramework() {
        this(true, true);
    }

    public CallbackFramework(boolean enableMetrics, boolean enableLogging) {
        this.enableMetrics = enableMetrics;
        this.enableLogging = enableLogging;
    }

    // ========== Properties ==========

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

    // ========== Registration ==========

    /**
     * Register a callback for an event.
     *
     * @param event          Event name to listen for
     * @param callback       Callback function
     * @param priority       Execution priority (higher first)
     * @param once           Execute only once then disable
     * @param namespace      Namespace for grouping
     * @param tags           Set of tags for filtering
     * @param eventFilters   List of filters to apply
     * @param rollbackHandler Function to call on rollback
     * @param errorHandler   Function to call on error
     * @param maxRetries     Maximum retry attempts
     * @param retryDelay     Delay between retries in seconds
     * @param timeout        Execution timeout in seconds
     * @param callbackName   Name for logging
     * @return The registered CallbackInfo
     */
    public CallbackInfo register(String event,
                                  Function<Map<String, Object>, Object> callback,
                                  int priority,
                                  boolean once,
                                  String namespace,
                                  Set<String> tags,
                                  List<EventFilter> eventFilters,
                                  Consumer<ChainContext> rollbackHandler,
                                  Function<CallbackChain.ExceptionContext, Object> errorHandler,
                                  int maxRetries,
                                  double retryDelay,
                                  Double timeout,
                                  String callbackName) {

        CallbackInfo callbackInfo = CallbackInfo.builder()
                .callback(callback)
                .priority(priority)
                .once(once)
                .namespace(namespace != null ? namespace : "default")
                .tags(tags != null ? tags : new HashSet<>())
                .maxRetries(maxRetries)
                .retryDelay(retryDelay)
                .timeout(timeout)
                .callbackName(callbackName)
                .build();

        callbacks.computeIfAbsent(event, k -> Collections.synchronizedList(new ArrayList<>())).add(callbackInfo);
        sortCallbacks(event);

        if (eventFilters != null && !eventFilters.isEmpty()) {
            callbackFilters.put(callback, new ArrayList<>(eventFilters));
        }

        // Add to chain if rollback/error handlers provided
        if (rollbackHandler != null || errorHandler != null) {
            chains.computeIfAbsent(event, CallbackChain::new)
                    .add(callbackInfo, rollbackHandler, errorHandler);
        }

        if (enableLogging) {
            log.info("Registered callback: {} -> {}", event, callbackInfo.getCallbackDisplayName());
        }

        return callbackInfo;
    }

    /**
     * Simplified register with fewer parameters.
     */
    public CallbackInfo register(String event,
                                  Function<Map<String, Object>, Object> callback,
                                  int priority,
                                  String callbackName) {
        return register(event, callback, priority, false, "default", null, null,
                null, null, 0, 0.0, null, callbackName);
    }

    /**
     * Register with default priority.
     */
    public CallbackInfo register(String event,
                                  Function<Map<String, Object>, Object> callback,
                                  String callbackName) {
        return register(event, callback, 0, callbackName);
    }

    /**
     * Synchronous registration method for decorator / initialization use.
     * Identical to {@link #register} but explicitly named for use outside async contexts.
     */
    public CallbackInfo registerSync(String event,
                                      Function<Map<String, Object>, Object> callback,
                                      int priority,
                                      boolean once,
                                      String namespace,
                                      Set<String> tags,
                                      List<EventFilter> eventFilters,
                                      Consumer<ChainContext> rollbackHandler,
                                      Function<CallbackChain.ExceptionContext, Object> errorHandler,
                                      int maxRetries,
                                      double retryDelay,
                                      Double timeout,
                                      String callbackName) {
        return register(event, callback, priority, once, namespace, tags, eventFilters,
                rollbackHandler, errorHandler, maxRetries, retryDelay, timeout, callbackName);
    }

    /**
     * Unregister a callback from an event.
     *
     * @param event    Event name
     * @param callback Callback to remove
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
     */
    public void unregisterNamespace(String namespace) {
        for (Map.Entry<String, List<CallbackInfo>> entry : callbacks.entrySet()) {
            entry.getValue().removeIf(ci -> namespace.equals(ci.getNamespace()));
        }
    }

    /**
     * Unregister callbacks matching any of the given tags.
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

    // ========== Triggering ==========

    /**
     * Trigger an event and execute all registered callbacks.
     *
     * @param event  Event name to trigger
     * @param args   Positional arguments for callbacks
     * @param kwargs Keyword arguments for callbacks
     * @return List of results from all executed callbacks
     */
    public List<Object> trigger(String event, Object[] args, Map<String, Object> kwargs) {
        List<Object> results = new ArrayList<>();

        if (args == null) args = new Object[0];
        if (kwargs == null) kwargs = new HashMap<>();

        // Record history
        if (enableHistory) {
            recordHistory(event, args, kwargs);
        }

        // Execute BEFORE hooks
        executeHooks(event, HookType.BEFORE, args, kwargs);

        List<CallbackInfo> eventCallbacks = callbacks.getOrDefault(event, Collections.emptyList());

        for (CallbackInfo callbackInfo : new ArrayList<>(eventCallbacks)) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }

            try {
                // Apply filters
                FilterResult filterResult = applyFilters(event, callbackInfo, args, kwargs);

                if (filterResult.getAction() == FilterAction.STOP) {
                    if (enableLogging) {
                        log.info("Filter stopped event processing: {}", event);
                    }
                    break;
                } else if (filterResult.getAction() == FilterAction.SKIP) {
                    if (enableLogging) {
                        log.debug("Filter skipped callback {}: {}",
                                callbackInfo.getCallbackDisplayName(), filterResult.getReason());
                    }
                    continue;
                }

                Object[] finalArgs = filterResult.getModifiedArgs() != null ? filterResult.getModifiedArgs() : args;
                Map<String, Object> finalKwargs = filterResult.getModifiedKwargs() != null
                        ? filterResult.getModifiedKwargs() : kwargs;

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
                    metrics.computeIfAbsent(key, k -> new CallbackMetrics())
                            .update(executionTime, false);
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
                    metrics.computeIfAbsent(key, k -> new CallbackMetrics())
                            .update(0.0, true);
                }

                // Record circuit breaker failure
                String cbKey = event + ":" + callbackInfo.getCallbackDisplayName();
                CircuitBreakerFilter breaker = circuitBreakers.get(cbKey);
                if (breaker != null) {
                    breaker.recordFailure(event, callbackInfo);
                }

                // Execute ERROR hooks
                Map<String, Object> errorKwargs = new HashMap<>(kwargs);
                errorKwargs.put("_error", e);
                executeHooks(event, HookType.ERROR, args, errorKwargs);
                RuntimeException requested = extractRequestedException(errorKwargs);
                if (requested != null) {
                    throw requested;
                }

                if (enableLogging) {
                    log.error("Callback execution failed: {} - {}",
                            callbackInfo.getCallbackDisplayName(), e.getMessage(), e);
                }
            }
        }

        // Execute AFTER hooks
        Map<String, Object> afterKwargs = new HashMap<>(kwargs);
        afterKwargs.put("_results", results);
        executeHooks(event, HookType.AFTER, args, afterKwargs);

        return results;
    }

    /**
     * Trigger with just event name and kwargs.
     */
    public List<Object> trigger(String event, Map<String, Object> kwargs) {
        return trigger(event, new Object[0], kwargs);
    }

    /**
     * Trigger with just event name.
     */
    public List<Object> trigger(String event) {
        return trigger(event, new Object[0], new HashMap<>());
    }

    /**
     * Trigger callbacks as a chain with data flow.
     *
     * @param event  Event name
     * @param args   Initial positional arguments
     * @param kwargs Initial keyword arguments
     * @return ChainResult with execution outcome
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

        ChainContext context = new ChainContext(event,
                args != null ? args : new Object[0],
                kwargs != null ? kwargs : new HashMap<>());

        return chain.execute(context);
    }

    /**
     * Trigger callbacks in parallel (concurrent execution).
     *
     * @param event  Event name to trigger
     * @param args   Positional arguments for callbacks
     * @param kwargs Keyword arguments for callbacks
     * @return List of results from all callbacks (excluding failed ones)
     */
    public List<Object> triggerParallel(String event, Object[] args, Map<String, Object> kwargs) {
        List<CallbackInfo> eventCallbacks = callbacks.getOrDefault(event, Collections.emptyList());
        if (eventCallbacks.isEmpty()) {
            return Collections.emptyList();
        }

        Object[] finalArgs = args != null ? args : new Object[0];
        Map<String, Object> finalKwargs = kwargs != null ? kwargs : new HashMap<>();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<Object>> futures = new ArrayList<>();

        for (CallbackInfo callbackInfo : new ArrayList<>(eventCallbacks)) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }

            futures.add(executor.submit(() -> {
                try {
                    FilterResult filterResult = applyFilters(event, callbackInfo, finalArgs, finalKwargs);

                    if (filterResult.getAction() == FilterAction.STOP ||
                        filterResult.getAction() == FilterAction.SKIP) {
                        return null;
                    }

                    Object[] fa = filterResult.getModifiedArgs() != null ? filterResult.getModifiedArgs() : finalArgs;
                    Map<String, Object> fk = filterResult.getModifiedKwargs() != null
                            ? filterResult.getModifiedKwargs() : finalKwargs;

                    Map<String, Object> callbackKwargs = new HashMap<>(fk);
                    callbackKwargs.put("_args", fa);

                    Object result;
                    if (callbackInfo.getTimeout() != null && callbackInfo.getTimeout() > 0) {
                        ExecutorService inner = Executors.newSingleThreadExecutor();
                        try {
                            Future<Object> innerFuture = inner.submit(() -> callbackInfo.getCallback().apply(callbackKwargs));
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
                        log.error("Callback {} failed in parallel execution: {}",
                                callbackInfo.getCallbackDisplayName(), e.getMessage());
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
     * @param event     Event name to trigger
     * @param condition Function that takes result and returns bool
     * @param args      Positional arguments for callbacks
     * @param kwargs    Keyword arguments for callbacks
     * @return First result that satisfies condition, or null
     */
    public Object triggerUntil(String event, Predicate<Object> condition,
                                Object[] args, Map<String, Object> kwargs) {
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
                Map<String, Object> fk = filterResult.getModifiedKwargs() != null
                        ? filterResult.getModifiedKwargs() : finalKwargs;

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
                    log.error("Callback {} failed in trigger_until: {}",
                            callbackInfo.getCallbackDisplayName(), e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Trigger event with timeout control.
     *
     * @param event          Event name to trigger
     * @param timeoutSeconds Maximum execution time in seconds
     * @param args           Positional arguments for callbacks
     * @param kwargs         Keyword arguments for callbacks
     * @return List of callback results (may be empty if timeout)
     */
    public List<Object> triggerWithTimeout(String event, double timeoutSeconds,
                                           Object[] args, Map<String, Object> kwargs) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<Object>> future = executor.submit(() -> trigger(event, args, kwargs));
            return future.get((long) (timeoutSeconds * 1000), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (enableLogging) {
                log.warn("Event '{}' execution timeout after {}s", event, timeoutSeconds);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            if (enableLogging) {
                log.error("Error during timed trigger of event '{}': {}", event, e.getMessage());
            }
            return Collections.emptyList();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Trigger event for each item in an input iterator (stream processing).
     *
     * @param event       Event name to trigger
     * @param inputStream Iterator providing input items
     * @param args        Additional positional arguments for callbacks
     * @param kwargs      Additional keyword arguments for callbacks
     * @return Iterator of results from callback executions
     */
    public Iterator<Object> triggerStream(String event, Iterator<?> inputStream,
                                           Object[] args, Map<String, Object> kwargs) {
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
            allResults.addAll(results);
        }
        return allResults.iterator();
    }

    /**
     * Trigger an event after a delay.
     *
     * @param event         Event name to trigger
     * @param delaySeconds  Delay in seconds before triggering
     * @param args          Positional arguments for callbacks
     * @param kwargs        Keyword arguments for callbacks
     * @return ScheduledFuture that can be cancelled; its result is the callback result list
     */
    public ScheduledFuture<List<Object>> triggerDelayed(String event, double delaySeconds,
                                                         Object[] args, Map<String, Object> kwargs) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
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
     * @param event  Event name to trigger
     * @param args   Positional arguments for callbacks
     * @param kwargs Keyword arguments for callbacks
     * @return Iterator of individual results
     */
    public Iterator<Object> triggerGenerator(String event, Object[] args, Map<String, Object> kwargs) {
        List<Object> aggregated = new ArrayList<>();

        if (args == null) args = new Object[0];
        if (kwargs == null) kwargs = new HashMap<>();

        executeHooks(event, HookType.BEFORE, args, kwargs);

        List<CallbackInfo> eventCallbacks = callbacks.getOrDefault(event, Collections.emptyList());

        for (CallbackInfo callbackInfo : new ArrayList<>(eventCallbacks)) {
            if (!callbackInfo.isEnabled()) {
                continue;
            }

            try {
                FilterResult filterResult = applyFilters(event, callbackInfo, args, kwargs);

                if (filterResult.getAction() == FilterAction.STOP) {
                    break;
                } else if (filterResult.getAction() == FilterAction.SKIP) {
                    continue;
                }

                Object[] finalArgs = filterResult.getModifiedArgs() != null ? filterResult.getModifiedArgs() : args;
                Map<String, Object> finalKwargs = filterResult.getModifiedKwargs() != null
                        ? filterResult.getModifiedKwargs() : kwargs;

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
                    log.error("Callback {} failed in triggerGenerator: {}",
                            callbackInfo.getCallbackDisplayName(), e.getMessage(), e);
                }
            }
        }

        Map<String, Object> afterKwargs = new HashMap<>(kwargs);
        afterKwargs.put("_results", aggregated);
        executeHooks(event, HookType.AFTER, args, afterKwargs);

        return aggregated.iterator();
    }

    // ========== Filters ==========

    /**
     * Add a filter to a specific event.
     */
    public void addFilter(String event, EventFilter filter) {
        filters.computeIfAbsent(event, k -> Collections.synchronizedList(new ArrayList<>())).add(filter);
    }

    /**
     * Add a filter that applies to all events.
     */
    public void addGlobalFilter(EventFilter filter) {
        globalFilters.add(filter);
    }

    /**
     * Add circuit breaker protection to a callback.
     */
    public void addCircuitBreaker(String event, CallbackInfo callback,
                                   int failureThreshold, double timeout) {
        String key = event + ":" + callback.getCallbackDisplayName();
        CircuitBreakerFilter breaker = new CircuitBreakerFilter(failureThreshold, timeout);
        circuitBreakers.put(key, breaker);
        addFilter(event, breaker);
    }

    /**
     * Apply all relevant filters to a callback.
     * Filters are applied in order: global -> event -> callback-specific.
     */
    private FilterResult applyFilters(String event, CallbackInfo callbackInfo,
                                       Object[] args, Map<String, Object> kwargs) {
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
            }
        }

        return FilterResult.continueResult(currentArgs, currentKwargs);
    }

    // ========== Hooks ==========

    /**
     * Add a lifecycle hook to an event.
     *
     * @param event    Event name
     * @param hookType Type of hook (BEFORE, AFTER, ERROR, CLEANUP)
     * @param hook     Consumer that receives a map with "_args" and other kwargs
     */
    public void addHook(String event, HookType hookType, Consumer<Map<String, Object>> hook) {
        hooks.computeIfAbsent(event, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(hookType, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(hook);
    }

    /**
     * Execute all hooks of a specific type for an event.
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

    // ========== Metrics ==========

    /**
     * Get performance metrics for callbacks.
     *
     * @param event    Optional event name filter
     * @param callback Optional callback name filter
     * @return Map of callback keys to metric maps
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
     */
    public Map<String, Map<String, Object>> getMetrics() {
        return getMetrics(null, null);
    }

    /**
     * Reset all performance metrics.
     */
    public void resetMetrics() {
        metrics.clear();
    }

    /**
     * Get callbacks with average execution time above threshold.
     *
     * @param threshold Minimum average time in seconds
     * @return List of slow callback information
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

    // ========== Queries ==========

    /**
     * List all registered events.
     *
     * @param namespace Optional namespace filter
     * @return List of event names
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
    // on(), trigger_on_call(), emits(), emits_stream(), emit_around(), transform_io()

    /**
     * Register a callback via the DSL style (mirrors Python {@code @framework.on(event)}).
     * <p>
     * Returns the registered {@link CallbackInfo} so callers can hold a reference.
     *
     * @param event    Event name to listen for
     * @param callback Callback function
     * @return The registered CallbackInfo
     */
    public CallbackInfo on(String event, Function<Map<String, Object>, Object> callback, String callbackName) {
        return register(event, callback, 0, callbackName);
    }

    /**
     * Full-parameter DSL registration (mirrors Python {@code @framework.on(event, priority=..., ...)}).
     */
    public CallbackInfo on(String event,
                           Function<Map<String, Object>, Object> callback,
                           int priority,
                           boolean once,
                           String namespace,
                           Set<String> tags,
                           List<EventFilter> eventFilters,
                           Consumer<ChainContext> rollbackHandler,
                           Function<CallbackChain.ExceptionContext, Object> errorHandler,
                           int maxRetries,
                           double retryDelay,
                           Double timeout,
                           String callbackName) {
        return register(event, callback, priority, once, namespace, tags, eventFilters,
                rollbackHandler, errorHandler, maxRetries, retryDelay, timeout, callbackName);
    }

    /**
     * Wrap a function so that it triggers an event when called (mirrors Python {@code @framework.trigger_on_call(event)}).
     * <p>
     * Returns a new function that:
     * <ol>
     *   <li>Triggers the event (with args if {@code passArgs} is true)</li>
     *   <li>Invokes the original function</li>
     *   <li>Optionally triggers the event again with the result</li>
     * </ol>
     *
     * @param event      Event name to trigger
     * @param wrapped    The function to wrap
     * @param passResult Whether to trigger event again with the result
     * @param passArgs   Whether to pass function arguments to the event
     * @return Wrapped function
     */
    public Function<Map<String, Object>, Object> triggerOnCall(
            String event,
            Function<Map<String, Object>, Object> wrapped,
            boolean passResult,
            boolean passArgs) {
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
     * @param event      Event name to trigger after execution
     * @param wrapped    The function to wrap
     * @param resultKey  Keyword argument name for the result (default: "result")
     * @param includeArgs Whether to include original args in event
     * @return Wrapped function
     */
    public Function<Map<String, Object>, Object> emits(
            String event,
            Function<Map<String, Object>, Object> wrapped,
            String resultKey,
            boolean includeArgs) {
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
     * Wrap an iterator-producing function so that each yielded item triggers an event
     * (mirrors Python {@code @framework.emits_stream(event)}).
     *
     * @param event   Event name to trigger for each item
     * @param wrapped Function that returns an Iterator
     * @param itemKey Keyword argument name for the yielded item (default: "item")
     * @return Wrapped function that returns an Iterator with event triggers
     */
    @SuppressWarnings("unchecked")
    public Function<Map<String, Object>, Object> emitsStream(
            String event,
            Function<Map<String, Object>, Object> wrapped,
            String itemKey) {
        return kwargs -> {
            Object rawResult = wrapped.apply(kwargs);
            if (!(rawResult instanceof Iterator<?> || rawResult instanceof Iterable<?>)) {
                throw new IllegalStateException(
                        "emitsStream can only wrap functions returning Iterator/Iterable, got "
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
                Map<String, Object> eventKwargs = new HashMap<>();
                eventKwargs.put(itemKey, item);
                trigger(event, new Object[0], eventKwargs);
                collected.add(item);
            }
            return collected.iterator();
        };
    }

    /**
     * Wrap a function with before/after/error events (mirrors Python {@code @framework.emit_around(before, after)}).
     *
     * @param beforeEvent  Event to trigger before execution
     * @param afterEvent   Event to trigger after successful execution
     * @param wrapped      The function to wrap
     * @param passArgs     Whether to pass function arguments to events
     * @param passResult   Whether to pass result to afterEvent
     * @param onErrorEvent Optional event to trigger on error (null to skip)
     * @return Wrapped function
     */
    public Function<Map<String, Object>, Object> emitAround(
            String beforeEvent,
            String afterEvent,
            Function<Map<String, Object>, Object> wrapped,
            boolean passArgs,
            boolean passResult,
            String onErrorEvent) {
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
     * @param wrapped         The function to wrap
     * @param inputTransform  Optional input transform: takes kwargs, returns modified kwargs (null to skip)
     * @param outputTransform Optional output transform: takes result, returns modified result (null to skip)
     * @return Wrapped function with I/O transformation
     */
    public Function<Map<String, Object>, Object> transformIo(
            Function<Map<String, Object>, Object> wrapped,
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
     * Wrap a function with input/output transformation via event callbacks
     * (mirrors Python {@code @framework.transform_io(input_event=..., output_event=...)}).
     * <p>
     * The last callback result from inputEvent is used as the new kwargs.
     * The last callback result from outputEvent is used as the transformed output.
     *
     * @param wrapped     The function to wrap
     * @param inputEvent  Event name for input transform (null to skip)
     * @param outputEvent Event name for output transform (null to skip)
     * @param resultKey   Keyword for output event payload (default: "result")
     * @return Wrapped function with event-driven I/O transformation
     */
    @SuppressWarnings("unchecked")
    public Function<Map<String, Object>, Object> transformIoByEvents(
            Function<Map<String, Object>, Object> wrapped,
            String inputEvent,
            String outputEvent,
            String resultKey) {
        return kwargs -> {
            Map<String, Object> finalKwargs = kwargs;

            // Input transform via event
            if (inputEvent != null) {
                Object[] args = kwargs.get("_args") instanceof Object[] ? (Object[]) kwargs.get("_args") : new Object[0];
                List<Object> results = trigger(inputEvent, args, kwargs);
                if (!results.isEmpty()) {
                    Object last = results.get(results.size() - 1);
                    if (last instanceof Map<?, ?>) {
                        finalKwargs = (Map<String, Object>) last;
                    }
                }
            }

            Object result = wrapped.apply(finalKwargs);

            // Output transform via event
            if (outputEvent != null) {
                Map<String, Object> outKwargs = new HashMap<>();
                outKwargs.put(resultKey, result);
                List<Object> results = trigger(outputEvent, new Object[0], outKwargs);
                if (!results.isEmpty()) {
                    result = results.get(results.size() - 1);
                }
            }

            return result;
        };
    }

    // ========== Internal ==========

    private void sortCallbacks(String event) {
        List<CallbackInfo> list = callbacks.get(event);
        if (list != null) {
            list.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        }
    }

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
