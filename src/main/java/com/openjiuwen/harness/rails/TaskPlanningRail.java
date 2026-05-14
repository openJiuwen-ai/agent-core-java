/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal task-planning rail that tracks a lightweight per-session todo list.
 *
 * <p>Mirrors Python's task-planning rail in
 * {@code openjiuwen.harness.rails.task_planning_rail}.
 */
public class TaskPlanningRail extends DeepAgentRail {

    private final Map<String, List<Map<String, Object>>> sessionTodos = new LinkedHashMap<>();

    public TaskPlanningRail() {
        setPriority(90);
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        Object session = readField(ctx, "session");
        if (!(session instanceof com.openjiuwen.core.session.Session sessionApi)) {
            return;
        }
        String sessionId = sessionApi.getSessionId();
        sessionTodos.computeIfAbsent(sessionId, ignored -> new ArrayList<>());
        sessionApi.updateState(Map.of("harness.todos", sessionTodos.get(sessionId)));
    }

    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        Object session = readField(ctx, "session");
        Object inputs = readField(ctx, "inputs");
        if (!(session instanceof com.openjiuwen.core.session.Session sessionApi)
                || !(inputs instanceof InvokeInputs invokeInputs)) {
            return;
        }
        String sessionId = sessionApi.getSessionId();
        List<Map<String, Object>> todos = sessionTodos.computeIfAbsent(sessionId, ignored -> new ArrayList<>());
        if (todos.isEmpty()) {
            Map<String, Object> bootstrap = new LinkedHashMap<>();
            String query = readStringField(invokeInputs, "query");
            bootstrap.put("content", query != null ? query : "task");
            bootstrap.put("status", "pending");
            bootstrap.put("priority", "high");
            todos.add(bootstrap);
            sessionApi.updateState(Map.of("harness.todos", todos));
        }
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value != null ? String.valueOf(value) : null;
    }
}
