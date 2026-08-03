/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Mirrors Python's {@code CircuitBreakerFilter} in
 * {@code openjiuwen/core/runner/callback/filters.py}.
 */
public class CircuitBreakerFilter extends EventFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerFilter.class);

    private final int failureThreshold;

    private final double timeout;

    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    private final Map<String, Double> lastFailureTime = new ConcurrentHashMap<>();

    private final Map<String, Boolean> openCircuits = new ConcurrentHashMap<>();

    public CircuitBreakerFilter() {
        this(5, 60.0, "CircuitBreaker");
    }

    public CircuitBreakerFilter(int failureThreshold, double timeout) {
        this(failureThreshold, timeout, "CircuitBreaker");
    }

    public CircuitBreakerFilter(int failureThreshold, double timeout, String name) {
        super(name);
        this.failureThreshold = failureThreshold;
        this.timeout = timeout;
    }

    public Map<String, Integer> getFailures() {
        return failures;
    }

    @Override
    public synchronized FilterResult filter(
            String event,
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        String key = event + ":" + callbackName(callback);
        double currentTime = System.currentTimeMillis() / 1000.0;
        boolean isOpen = openCircuits.getOrDefault(key, false);
        if (!isOpen) {
            return FilterResult.continueResult();
        }

        double lastFailure = lastFailureTime.getOrDefault(key, 0.0);
        if (currentTime - lastFailure > timeout) {
            openCircuits.put(key, false);
            failures.put(key, 0);
            return FilterResult.continueResult();
        }

        return FilterResult.skipResult("Circuit breaker open, retry after " + timeout + "s");
    }

    public synchronized void recordSuccess(String event, Function<Map<String, Object>, Object> callback) {
        String key = event + ":" + callbackName(callback);
        failures.put(key, 0);
        openCircuits.put(key, false);
    }

    public synchronized void recordFailure(String event, Function<Map<String, Object>, Object> callback) {
        String key = event + ":" + callbackName(callback);
        int updatedFailures = failures.getOrDefault(key, 0) + 1;
        failures.put(key, updatedFailures);
        lastFailureTime.put(key, System.currentTimeMillis() / 1000.0);
        if (updatedFailures >= failureThreshold) {
            openCircuits.put(key, true);
            LOGGER.warn("Circuit breaker opened for {}", key);
        }
    }
}
