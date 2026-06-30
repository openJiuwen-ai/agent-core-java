/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Metadata and configuration for a registered callback.
 * <p>
 * In Java, the callback is a {@code Function<Map<String, Object>, Object>} that accepts
 * a map of keyword arguments (including positional args under key "_args") and returns a result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackInfo {

    /** The callback function. Accepts keyword args map, returns result. */
    private Function<Map<String, Object>, Object> callback;

    /** Execution priority (higher executes first). */
    @Builder.Default
    private int priority = 0;

    /** Whether callback should execute only once. */
    @Builder.Default
    private boolean once = false;

    /** Whether callback is currently enabled. */
    @Builder.Default
    private boolean enabled = true;

    /** Namespace for grouping callbacks. */
    @Builder.Default
    private String namespace = "default";

    /** Set of tags for filtering. */
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    /** Maximum retry attempts on failure. */
    @Builder.Default
    private int maxRetries = 0;

    /** Delay between retries in seconds. */
    @Builder.Default
    private double retryDelay = 0.0;

    /** Execution timeout in seconds. */
    private Double timeout;

    /** Timestamp when callback was registered (epoch seconds). */
    @Builder.Default
    private double createdAt = System.currentTimeMillis() / 1000.0;

    /** Name of the callback for logging purposes. */
    private String callbackName;

    /** Semantic type marker, e.g. "transform". Empty string means normal callback. */
    @Builder.Default
    private String callbackType = "";

    /**
     * Get the callback name for logging/metrics purposes.
     *
     * @return callback name or "unknown"
     */
    public String getCallbackDisplayName() {
        if (callbackName != null && !callbackName.isEmpty()) {
            return callbackName;
        }
        return callback != null ? callback.getClass().getSimpleName() : "unknown";
    }
}
