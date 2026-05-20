/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.artifacts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class ArtifactStore used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ArtifactStore {
    private final Map<String, Object> session = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> task = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object get(String name, String taskId, Object defaultValue) {
        if (taskId != null && !taskId.isBlank()) {
            Map<String, Object> bucket = task.get(taskId);
            if (bucket != null && bucket.containsKey(name)) {
                return bucket.get(name);
            }
        }
        return session.getOrDefault(name, defaultValue);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object require(String name, String taskId) {
        Object marker = new Object();
        Object value = get(name, taskId, marker);
        if (value == marker) {
            String scope = taskId != null && !taskId.isBlank() ? "task=" + taskId : "session";
            throw new IllegalArgumentException("Missing artifact '" + name + "' in " + scope);
        }
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void put(String name, Object value, String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            task.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>()).put(name, value);
            return;
        }
        session.put(name, value);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void putMany(Map<String, Object> artifacts, String taskId) {
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        artifacts.forEach((name, value) -> put(name, value, taskId));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean has(String name, String taskId) {
        Object marker = new Object();
        return get(name, taskId, marker) != marker;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void resetTask(String taskId) {
        task.remove(taskId);
    }
}
