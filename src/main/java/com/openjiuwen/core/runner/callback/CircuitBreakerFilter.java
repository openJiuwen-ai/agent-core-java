/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circuit breaker pattern implementation.
 * <p>
 * Prevents execution of failing callbacks after a threshold is reached.
 * Automatically attempts to reset after a timeout period.
 * 
 * @since 0.1.7
 */
public class CircuitBreakerFilter extends EventFilter {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerFilter.class);

    private final int failureThreshold;
    private final double timeout;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Double> lastFailureTime = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Boolean> isOpen = new ConcurrentHashMap<>();

    /**
     * CircuitBreakerFilter.
     * 
     * @since 0.1.7
     */
    public CircuitBreakerFilter() {
        this(5, 60.0, "CircuitBreaker");
    }

    /**
     * CircuitBreakerFilter.
     * 
     * @param failureThreshold failureThreshold
     * @param timeout timeout
     * @since 0.1.7
     */
    public CircuitBreakerFilter(int failureThreshold, double timeout) {
        this(failureThreshold, timeout, "CircuitBreaker");
    }

    /**
     * CircuitBreakerFilter.
     * 
     * @param failureThreshold failureThreshold
     * @param timeout timeout
     * @param name name
     * @since 0.1.7
     */
    public CircuitBreakerFilter(int failureThreshold, double timeout, String name) {
        super(name);
        this.failureThreshold = failureThreshold;
        this.timeout = timeout;
    }

    /**
     * getFailures.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Integer> getFailures() {
        return failures;
    }

    /**
     * filter.
     * 
     * @param event event
     * @param callback callback
     * @param args args
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public synchronized FilterResult filter(String event, CallbackInfo callback, Object[] args,
            Map<String, Object> kwargs) {
        String key = event + ":" + callback.getCallbackDisplayName();
        double currentTime = System.currentTimeMillis() / 1000.0;

        // Check if circuit is open
        if (Boolean.TRUE.equals(isOpen.getOrDefault(key, false))) {
            Double lastTime = lastFailureTime.get(key);
            if (lastTime != null && currentTime - lastTime > timeout) {
                // Try to close circuit if timeout isPassed
                isOpen.put(key, false);
                failures.put(key, 0);
            } else {
                return FilterResult.skipResult("Circuit breaker open, retry after " + timeout + "s");
            }
        }

        return FilterResult.continueResult();
    }

    /**
     * Record successful execution.
     * 
     * @param event Event name
     * @param callback Callback that succeeded
     * @since 0.1.7
     */
    public synchronized void recordSuccess(String event, CallbackInfo callback) {
        String key = event + ":" + callback.getCallbackDisplayName();
        failures.put(key, 0);
    }

    /**
     * Record failed execution and potentially open circuit.
     * 
     * @param event Event name
     * @param callback Callback that failed
     * @since 0.1.7
     */
    public synchronized void recordFailure(String event, CallbackInfo callback) {
        String key = event + ":" + callback.getCallbackDisplayName();
        double currentTime = System.currentTimeMillis() / 1000.0;

        int failCount = failures.getOrDefault(key, 0) + 1;
        failures.put(key, failCount);
        lastFailureTime.put(key, currentTime);

        if (failCount >= failureThreshold) {
            isOpen.put(key, true);
            logger.warn("Circuit breaker opened for {}", key);
        }
    }
}
