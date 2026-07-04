/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Mirrors Python's {@code RateLimitFilter} in
 * {@code openjiuwen/core/runner/callback/filters.py}.
 */
public class RateLimitFilter extends EventFilter {

    private final int maxCalls;

    private final double timeWindow;

    private final Map<String, Deque<Double>> callTimes = new ConcurrentHashMap<>();

    public RateLimitFilter(int maxCalls, double timeWindow) {
        this(maxCalls, timeWindow, "RateLimit");
    }

    public RateLimitFilter(int maxCalls, double timeWindow, String name) {
        super(name);
        this.maxCalls = maxCalls;
        this.timeWindow = timeWindow;
    }

    public synchronized FilterResult filter(
            String event,
            CallbackInfo callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        return filterByName(event, callback == null ? "<null-callback>" : callback.getCallbackDisplayName());
    }

    @Override
    public synchronized FilterResult filter(
            String event,
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        return filterByName(event, callbackName(callback));
    }

    private FilterResult filterByName(String event, String callbackDisplayName) {
        double currentTime = System.currentTimeMillis() / 1000.0;
        String key = event + ":" + callbackDisplayName;
        Deque<Double> times = callTimes.computeIfAbsent(key, unused -> new ArrayDeque<>());

        while (!times.isEmpty() && currentTime - times.peekFirst() > timeWindow) {
            times.removeFirst();
        }

        if (times.size() >= maxCalls) {
            return FilterResult.skipResult(
                    "Rate limit exceeded: " + maxCalls + " calls per " + timeWindow + "s"
            );
        }

        times.addLast(currentTime);
        return FilterResult.continueResult();
    }
}
