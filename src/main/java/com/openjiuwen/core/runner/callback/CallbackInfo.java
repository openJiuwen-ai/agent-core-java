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

    @Builder.Default
    private String callbackType = "";

    @Override
    public int hashCode() {
        return System.identityHashCode(callback);
    }
}
