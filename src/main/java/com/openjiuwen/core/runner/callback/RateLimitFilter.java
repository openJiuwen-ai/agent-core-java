/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.runner.callback;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filter to limit callback execution rate.
 * <p>
 * Prevents callbacks from executing too frequently within a time window.
 * Thread-safe implementation.
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

    @Override
    public synchronized FilterResult filter(String event, CallbackInfo callback,
                                             Object[] args, Map<String, Object> kwargs) {
        double currentTime = System.currentTimeMillis() / 1000.0;
        String key = event + ":" + callback.getCallbackDisplayName();

        Deque<Double> times = callTimes.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Remove expired timestamps
        while (!times.isEmpty() && currentTime - times.peekFirst() > timeWindow) {
            times.pollFirst();
        }

        // Check rate limit
        if (times.size() >= maxCalls) {
            return FilterResult.skipResult(
                    "Rate limit exceeded: " + maxCalls + " calls per " + timeWindow + "s");
        }

        // Record this call
        times.addLast(currentTime);

        return FilterResult.continueResult();
    }
}
