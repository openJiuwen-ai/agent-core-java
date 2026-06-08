/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.artifacts;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A scoped artifact store with session and task namespaces.
 * <p>
 * Mirrors Python's {@code ArtifactStore} in
 * {@code openjiuwen/auto_harness/artifacts/store.py}.
 */
public class ArtifactStore {

    private final Map<String, Object> session = new HashMap<>();
    private final Map<String, Map<String, Object>> task = new HashMap<>();

    public Object get(String name, String taskId, Object defaultValue) {
        if (taskId != null && !taskId.isEmpty()) {
            Map<String, Object> taskBucket = task.get(taskId);
            if (taskBucket != null && taskBucket.containsKey(name)) {
                return taskBucket.get(name);
            }
        }
        return session.containsKey(name) ? session.get(name) : defaultValue;
    }

    public Object get(String name, String taskId) {
        return get(name, taskId, null);
    }

    public Object get(String name) {
        return get(name, "", null);
    }

    public Object require(String name, String taskId) {
        Object marker = new Object();
        Object value = get(name, taskId, marker);
        if (value == marker) {
            String scope = taskId != null && !taskId.isEmpty() ? "task=" + taskId : "session";
            throw new NoSuchElementException("Missing artifact '" + name + "' in " + scope);
        }
        return value;
    }

    public Object require(String name) {
        return require(name, "");
    }

    public void put(String name, Object value, String taskId) {
        if (taskId != null && !taskId.isEmpty()) {
            task.computeIfAbsent(taskId, ignored -> new HashMap<>()).put(name, value);
            return;
        }
        session.put(name, value);
    }

    public void put(String name, Object value) {
        put(name, value, "");
    }

    public void putMany(Map<String, Object> artifacts, String taskId) {
        for (Map.Entry<String, Object> entry : artifacts.entrySet()) {
            put(entry.getKey(), entry.getValue(), taskId);
        }
    }

    public void putMany(Map<String, Object> artifacts) {
        putMany(artifacts, "");
    }

    public boolean has(String name, String taskId) {
        Object marker = new Object();
        return get(name, taskId, marker) != marker;
    }

    public boolean has(String name) {
        return has(name, "");
    }

    public void resetTask(String taskId) {
        task.remove(taskId);
    }
}
