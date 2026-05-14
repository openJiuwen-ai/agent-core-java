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

    public static CallbackInfoBuilder builder() {
        return new CallbackInfoBuilder();
    }

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

    public Function<Map<String, Object>, Object> getCallback() {
        return callback;
    }

    public void setCallback(Function<Map<String, Object>, Object> callback) {
        this.callback = callback;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isOnce() {
        return once;
    }

    public void setOnce(boolean once) {
        this.once = once;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public double getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(double retryDelay) {
        this.retryDelay = retryDelay;
    }

    public Double getTimeout() {
        return timeout;
    }

    public void setTimeout(Double timeout) {
        this.timeout = timeout;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }

    public String getCallbackName() {
        return callbackName;
    }

    public void setCallbackName(String callbackName) {
        this.callbackName = callbackName;
    }

    public static final class CallbackInfoBuilder {
        private Function<Map<String, Object>, Object> callback;
        private int priority = 0;
        private boolean once = false;
        private boolean enabled = true;
        private String namespace = "default";
        private Set<String> tags = new HashSet<>();
        private int maxRetries = 0;
        private double retryDelay = 0.0;
        private Double timeout;
        private double createdAt = System.currentTimeMillis() / 1000.0;
        private String callbackName;

        public CallbackInfoBuilder callback(Function<Map<String, Object>, Object> callback) { this.callback = callback; return this; }
        public CallbackInfoBuilder priority(int priority) { this.priority = priority; return this; }
        public CallbackInfoBuilder once(boolean once) { this.once = once; return this; }
        public CallbackInfoBuilder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public CallbackInfoBuilder namespace(String namespace) { this.namespace = namespace; return this; }
        public CallbackInfoBuilder tags(Set<String> tags) { this.tags = tags; return this; }
        public CallbackInfoBuilder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public CallbackInfoBuilder retryDelay(double retryDelay) { this.retryDelay = retryDelay; return this; }
        public CallbackInfoBuilder timeout(Double timeout) { this.timeout = timeout; return this; }
        public CallbackInfoBuilder createdAt(double createdAt) { this.createdAt = createdAt; return this; }
        public CallbackInfoBuilder callbackName(String callbackName) { this.callbackName = callbackName; return this; }

        public CallbackInfo build() {
            CallbackInfo info = new CallbackInfo();
            info.setCallback(callback);
            info.setPriority(priority);
            info.setOnce(once);
            info.setEnabled(enabled);
            info.setNamespace(namespace);
            info.setTags(tags);
            info.setMaxRetries(maxRetries);
            info.setRetryDelay(retryDelay);
            info.setTimeout(timeout);
            info.setCreatedAt(createdAt);
            info.setCallbackName(callbackName);
            return info;
        }
    }
}
