/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.artifacts;

import java.util.HashMap;
import java.util.Map;

/**
 * A scoped artifact store with session and task namespaces.
 *
 * <p>Mirrors Python's {@code ArtifactStore} in {@code openjiuwen.auto_harness.artifacts.store}.</p>
 */
public class ArtifactStore {

    private final Map<String, Object> session = new HashMap<>();
    private final Map<String, Map<String, Object>> task = new HashMap<>();

    /**
     * Get an artifact by name from session or task scope.
     *
     * @param name    the artifact name
     * @param taskId  the task ID (optional, empty string for session scope)
     * @param default the default value if not found
     * @return the artifact value or default
     */
    public Object get(String name, String taskId, Object defaultValue) {
        if (taskId != null && !taskId.isEmpty()) {
            Map<String, Object> taskBucket = task.get(taskId);
            if (taskBucket != null && taskBucket.containsKey(name)) {
                return taskBucket.get(name);
            }
        }
        return session.containsKey(name) ? session.get(name) : defaultValue;
    }

    /**
     * Get an artifact from session scope.
     *
     * @param name the artifact name
     * @return the artifact value or null
     */
    public Object get(String name) {
        return get(name, "", null);
    }

    /**
     * Require an artifact to exist, throwing if missing.
     *
     * @param name   the artifact name
     * @param taskId the task ID (optional)
     * @return the artifact value
     * @throws IllegalArgumentException if artifact not found
     */
    public Object require(String name, String taskId) {
        Object marker = new Object();
        Object value = get(name, taskId, marker);
        if (value == marker) {
            String scope = (taskId != null && !taskId.isEmpty()) ? "task=" + taskId : "session";
            throw new IllegalArgumentException("Missing artifact '" + name + "' in " + scope);
        }
        return value;
    }

    /**
     * Require an artifact from session scope.
     *
     * @param name the artifact name
     * @return the artifact value
     */
    public Object require(String name) {
        return require(name, "");
    }

    /**
     * Put an artifact into session or task scope.
     *
     * @param name   the artifact name
     * @param value  the artifact value
     * @param taskId the task ID (optional, empty string for session scope)
     */
    public void put(String name, Object value, String taskId) {
        if (taskId != null && !taskId.isEmpty()) {
            task.computeIfAbsent(taskId, k -> new HashMap<>()).put(name, value);
        } else {
            session.put(name, value);
        }
    }

    /**
     * Put an artifact into session scope.
     *
     * @param name  the artifact name
     * @param value the artifact value
     */
    public void put(String name, Object value) {
        put(name, value, "");
    }

    /**
     * Put multiple artifacts at once.
     *
     * @param artifacts the artifacts map
     * @param taskId    the task ID (optional)
     */
    public void putMany(Map<String, Object> artifacts, String taskId) {
        for (Map.Entry<String, Object> entry : artifacts.entrySet()) {
            put(entry.getKey(), entry.getValue(), taskId);
        }
    }

    /**
     * Check if an artifact exists.
     *
     * @param name   the artifact name
     * @param taskId the task ID (optional)
     * @return true if artifact exists
     */
    public boolean has(String name, String taskId) {
        Object marker = new Object();
        return get(name, taskId, marker) != marker;
    }

    /**
     * Check if an artifact exists in session scope.
     *
     * @param name the artifact name
     * @return true if artifact exists
     */
    public boolean has(String name) {
        return has(name, "");
    }

    /**
     * Reset task-scoped artifacts for a specific task.
     *
     * @param taskId the task ID
     */
    public void resetTask(String taskId) {
        task.remove(taskId);
    }

    /**
     * Clear all session and task artifacts.
     */
    public void clear() {
        session.clear();
        task.clear();
    }
}