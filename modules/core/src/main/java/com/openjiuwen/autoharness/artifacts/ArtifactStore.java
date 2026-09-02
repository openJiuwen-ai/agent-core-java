/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.artifacts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class ArtifactStore used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ArtifactStore {
    private final Map<String, Object> session = new LinkedHashMap<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Map<String, Object>> task = new LinkedHashMap<>();

    /**
     * get.
     * 
     * @param name name
     * @param taskId taskId
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
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
     * require.
     * 
     * @param name name
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
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
     * put.
     * 
     * @param name name
     * @param value value
     * @param taskId taskId
     * @since 0.1.7
     */
    public void put(String name, Object value, String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            task.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>()).put(name, value);
            return;
        }
        session.put(name, value);
    }

    /**
     * putMany.
     * 
     * @param artifacts artifacts
     * @param taskId taskId
     * @since 0.1.7
     */
    public void putMany(Map<String, Object> artifacts, String taskId) {
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        artifacts.forEach((name, value) -> put(name, value, taskId));
    }

    /**
     * has.
     * 
     * @param name name
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public boolean has(String name, String taskId) {
        Object marker = new Object();
        return get(name, taskId, marker) != marker;
    }

    /**
     * resetTask.
     * 
     * @param taskId taskId
     * @since 0.1.7
     */
    public void resetTask(String taskId) {
        task.remove(taskId);
    }
}
