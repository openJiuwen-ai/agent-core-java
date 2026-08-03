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
 * Mirrors Python's {@code CallbackInfo} in
 * {@code openjiuwen/core/runner/callback/models.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackInfo {

    private Function<Map<String, Object>, Object> callback;

    private int priority;

    @Builder.Default
    private boolean once = false;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private String namespace = "default";

    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @Builder.Default
    private int maxRetries = 0;

    @Builder.Default
    private double retryDelay = 0.0;

    private Double timeout;

    @Builder.Default
    private double createdAt = System.currentTimeMillis() / 1000.0;

    private Function<Map<String, Object>, Object> wrapper;

    private String callbackName;

    @Builder.Default
    private String callbackType = "";

    /**
     * Returns the 0.1.12 callback display name for logging and rate-limit keys.
     *
     * @return configured callback name, callback toString, callback class name, or {@code unknown}
     */
    public String getCallbackDisplayName() {
        if (callbackName != null && !callbackName.isEmpty()) {
            return callbackName;
        }
        if (callback != null) {
            String str = callback.toString();
            String simpleName = callback.getClass().getSimpleName();
            // Use toString() if it provides a more descriptive name than the class simple name
            if (str != null && !str.isEmpty() && !str.equals(simpleName)) {
                return str;
            }
            return simpleName;
        }
        return "unknown";
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(callback);
    }
}
